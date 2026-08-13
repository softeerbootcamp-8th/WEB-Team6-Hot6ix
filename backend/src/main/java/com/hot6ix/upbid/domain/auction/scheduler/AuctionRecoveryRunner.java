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
 * DB 를 보고 마감 예약을 다시 채운다. <b>기동할 때 한 번</b>과 <b>그 뒤로 주기적으로</b> 돈다.
 *
 * <p>예약이 Redis 에만 있어서, Redis 가 비면 진행중인 물품이 마감 시각이 지나도 영영 열린 채로
 * 남는다. 서버가 같이 죽었다면 다시 뜨면서 채우지만, <b>서버는 살아 있는데 Redis 만 비는
 * 경우</b>는 아무도 채워주지 않는다. 주기 실행이 그 자리를 맡는다.
 *
 * <p>판단 기준은 예약이 아니라 <b>DB</b>다. 무엇이 살아 있었는지는 알 수도 없고 알 필요도 없다.
 *
 * <p><b>이미 지난 마감 시각은 따로 가르지 않는다.</b> 지난 시각으로 넣으면 다음 폴링에 곧바로
 * 집힌다. 유예를 두지 않는 것은 그렇게 하면 "서버가 꺼져 있던 동안의 입찰"이라는 있을 수 없는
 * 상태를 다뤄야 하기 때문이고, DB 의 {@code end_at}이 판단 기준이라는 팀 원칙과도 맞는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRecoveryRunner {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionCloseScheduler auctionCloseScheduler;

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
     * 막으면 되므로 짧게 잡는다. 실행 주기보다 길게 잡으면 주기를 줄여도 그만큼 안 돈다.
     */
    @Scheduled(fixedDelayString = "${upbid.auction.close.resync-interval-ms}")
    @SchedulerLock(name = "auction-close-resync", lockAtLeastFor = "10s", lockAtMostFor = "5m")
    public void resyncCloseSchedules() {
        resync();
    }

    /**
     * 진행 중인 물품을 읽어 <b>예약이 없는 것만</b> 채운다.
     *
     * <p>덮어쓰지 않는 것이 중요하다. 이미 있는 줄에는 연장이 반영돼 있거나 지금 어느 서버가
     * 집어서 처리 중이라는 표시가 들어 있는데, DB 의 원래 마감 시각으로 덮으면 그 둘을
     * 되돌려서 같은 물품에 두 서버가 달라붙는다.
     *
     * <p>예외를 삼키고 로그만 남긴다. 기동 시에 던지면 그 서버는 경매를 아예 받지 못하는데,
     * 그건 물품 몇 개가 안 닫히는 것보다 나쁘다.
     */
    private void resync() {

        try {
            List<InProgressAuctionItemProjection> targets =
                    auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS);

            targets.forEach(target ->
                    auctionCloseScheduler.scheduleIfAbsent(target.auctionItemId(), target.endAt()));

            log.debug("마감 예약 재동기화: 진행중 {}건", targets.size());
        } catch (Exception e) {
            log.error("마감 예약 재동기화 실패. 진행 중이던 물품이 닫히지 않은 채 남을 수 있다", e);
        }
    }
}
