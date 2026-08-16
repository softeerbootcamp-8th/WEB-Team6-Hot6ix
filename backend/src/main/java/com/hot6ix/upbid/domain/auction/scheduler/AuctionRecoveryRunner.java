package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.InProgressAuctionItemProjection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DB 를 보고 <b>마감 예약과 마감 임박 알림 예약을 함께</b> 다시 채운다. <b>기동할 때 한 번</b>과
 * <b>그 뒤로 주기적으로</b> 돈다.
 *
 * <p>예약이 Redis 에만 있어서, Redis 가 비면 진행중인 물품이 마감 시각이 지나도 영영 열린 채로
 * 남는다. 서버가 같이 죽었다면 다시 뜨면서 채우지만, <b>서버는 살아 있는데 Redis 만 비는
 * 경우</b>는 아무도 채워주지 않는다. 주기 실행이 그 자리를 맡는다.
 *
 * <p>둘을 <b>한 러너에서 채우는 것은 조회가 같기 때문</b>이다(#290). 마감 예약도 알림 예약도
 * 대상이 "진행중인 물품"이라, 한 번 읽어 둘 다 채우면 진행중 물품 전체 조회가 두 번 돌지 않는다.
 *
 * <p>판단 기준은 예약이 아니라 <b>DB</b>다. 무엇이 살아 있었는지는 알 수도 없고 알 필요도 없다.
 * 그래서 <b>예약이 있어도 마감 시각은 DB 값으로 맞춘다.</b> 빠진 예약을 채우는 것뿐 아니라
 * 커밋 뒤 재예약이 실패해 낡은 시각이 남은 줄을 고치는 것도 여기 몫이다(#327).
 *
 * <p><b>이미 지난 시각은 따로 가르지 않는다.</b> 지난 시각으로 넣으면 다음 폴링에 곧바로
 * 집힌다. 유예를 두지 않는 것은 그렇게 하면 "서버가 꺼져 있던 동안의 입찰"이라는 있을 수 없는
 * 상태를 다뤄야 하기 때문이고, DB 의 {@code end_at}이 판단 기준이라는 팀 원칙과도 맞는다.
 * 알림도 마찬가지라서, <b>재배포하는 동안 알림 시각이 지나간 물품은 늦게라도 알린다.</b> 알림
 * 시각이 지났는데 아직 안 닫혔다는 것은 마감까지 트리거 초 안쪽으로 남았다는 뜻이라 "지금
 * 입찰하면 마감이 밀린다"는 알림 내용이 여전히 맞다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRecoveryRunner {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionCloseScheduler auctionCloseScheduler;
    private final ItemClosingSoonScheduler itemClosingSoonScheduler;

    /**
     * 기동 직후 한 번 채운다.
     *
     * <p>{@code ApplicationReadyEvent}를 쓰는 것은 이 시점이면 데이터소스와 Redis 가 모두 떠
     * 있기 때문이다. 빈 초기화 단계에서 돌리면 아직 준비되지 않은 것에 기대게 된다.
     *
     * <p><b>여기에는 분산 락을 걸지 않는다.</b> 서버마다 뜰 때 한 번씩만 도는 데다 없을 때만
     * 넣기 때문에 겹쳐도 결과가 같아서, 락을 걸어 얻을 것이 없다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreCloseSchedules() {
        resync();
    }

    /**
     * 주기적으로 빠진 예약을 채운다.
     *
     * <p><b>분산 락으로 한 서버만 돈다.</b> 결과는 겹쳐도 같지만, 진행중인 물품을 전부 읽는
     * 쿼리가 서버 수만큼 도는 것을 막는다. 같은 락을 {@code DealAwardRecoveryRunner}도 쓴다.
     *
     * <p>{@code lockAtLeastFor}는 서버 간 시각이 조금 어긋나 같은 tick 이 두 번 도는 것만
     * 막으면 되므로 짧게 잡는다. <b>실행 주기보다 짧아야 한다</b> — 주기와 같거나 길면 다음
     * tick 이 제 락에 막혀 건너뛰고, 주기를 줄여도 그만큼 안 돈다. 주기를 10s 로 당기면서
     * 같이 내렸다(#327).
     */
    @Scheduled(fixedDelayString = "${upbid.auction.close.resync-interval-ms}")
    @SchedulerLock(name = "auction-close-resync", lockAtLeastFor = "2s", lockAtMostFor = "5m")
    public void resyncCloseSchedules() {
        resync();
    }

    /**
     * 진행 중인 물품을 읽어 예약을 DB 에 맞춘다. 마감 예약과 알림 예약을 같은 목록으로 함께
     * 채운다.
     *
     * <p>예외를 삼키고 로그만 남긴다. 기동 시에 던지면 그 서버는 경매를 아예 받지 못하는데,
     * 그건 물품 몇 개가 안 닫히는 것보다 나쁘다.
     */
    private void resync() {

        try {
            List<InProgressAuctionItemProjection> targets =
                    auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS);

            targets.forEach(this::restoreSchedules);

            log.debug("마감·알림 예약 재동기화: 진행중 {}건", targets.size());
        } catch (Exception e) {
            log.error("예약 재동기화 실패. 진행 중이던 물품이 닫히지 않거나 알림이 나가지 않을 수 있다", e);
        }
    }

    /**
     * 물품 하나의 마감 예약과 알림 예약을 채운다. <b>마감은 덮어쓰고 알림은 없을 때만 넣는다.</b>
     *
     * <p><b>마감을 덮어쓰는 것은 예약 시각이 앞으로 당겨지는 경로가 있기 때문</b>이다(#327).
     * 판매자가 마감을 앞당기면 {@code end_at} 이 지금 + 트리거 초로 바뀌는데, 커밋 뒤에 도는
     * 재예약이 실패하면 큐에는 원래 시각이 그대로 남는다. 없을 때만 넣는 방식으로는 그 줄을
     * 못 고쳐서 물품이 <b>원래 마감까지 열려 있다.</b> DB 가 판단 기준이라는 원칙대로 큐를
     * DB 에 맞춘다.
     *
     * <p>덮어쓰면 지금 집혀서 처리 중인 예약의 유예까지 되돌려, 같은 물품을 다른 서버가 한 번
     * 더 집을 수 있다. 그래도 되는 것은 {@code closeIfDue}가 물품 행 락을 잡고 상태를 다시
     * 보기 때문이다 — 이미 닫힌 물품은 거기서 걸러지고 헛 조회 한 번으로 끝난다. 제시간에
     * 닫히는 쪽이 그 낭비보다 중요하다.
     *
     * <p><b>알림은 아직 안 알린 물품만 넣는다.</b> 이미 알린 물품도 알림 시각이 과거라 그냥
     * 넣으면 걸리는데, 그러면 폴러가 집어서 DB 를 읽고 {@code notified_at} 에 막혀 버리는
     * 한 바퀴가 재동기화 주기마다 반복된다. 알림이 두 번 나가지는 않지만 밀린 예약 수 지표에
     * 잡음이 끼고 로그만 쌓인다.
     *
     * <p>알림에 마감과 같은 문제가 없는 것은 <b>알림 시각이 앞으로 당겨져야 하는 경우가 없기
     * 때문</b>이다. 앞당기기는 {@code notified_at} 을 찍어 알림을 아예 끄고, 연장은 시각을
     * 뒤로만 민다. 뒤로 밀린 예약은 일찍 깨어나도 {@code notifyIfDue}가 다시 재워 준다.
     */
    private void restoreSchedules(InProgressAuctionItemProjection target) {

        auctionCloseScheduler.schedule(target.auctionItemId(), target.endAt());

        if (target.needsClosingSoonSchedule()) {
            itemClosingSoonScheduler.scheduleIfAbsent(target.auctionItemId(), target.notifyAt());
        }
    }
}
