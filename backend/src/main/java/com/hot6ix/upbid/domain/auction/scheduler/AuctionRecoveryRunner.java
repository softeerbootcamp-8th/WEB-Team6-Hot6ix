package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.InProgressAuctionItemProjection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 기동하면 진행 중인 물품의 마감 예약을 다시 건다.
 *
 * <p>{@link InMemoryAuctionCloseScheduler}가 예약을 프로세스 메모리에 담고 있어서, 재배포나
 * 비정상 종료로 프로세스가 죽으면 예약이 통째로 사라진다. 그러면 DB에는 물품이 진행중으로
 * 남아 있는데 아무도 닫아주지 않아, 마감 시각이 지나도 <b>남은 시간 0초에 진행 중</b>인
 * 상태로 머문다(이슈 #214).
 *
 * <p>판단 기준은 예약이 아니라 <b>DB</b>다. 살아 있는 예약이 무엇이었는지는 이미 사라져서 알
 * 수 없고, 알 필요도 없다. 진행중인 물품을 전부 다시 걸면 되고 중복은 스케줄러가
 * 갈아 끼우면서 정리한다.
 *
 * <p><b>이미 지난 마감 시각은 따로 가르지 않는다.</b> {@code TaskScheduler}가 과거 시각을
 * 받으면 곧바로 실행하므로 서버가 꺼져 있던 동안 마감됐어야 할 물품은 기동 직후에 닫힌다.
 * 유예를 두지 않는 것은 그렇게 하면 "서버가 꺼져 있던 동안의 입찰"이라는 있을 수 없는 상태를
 * 다뤄야 하기 때문이고, DB의 {@code end_at}이 판단 기준이라는 팀 원칙과도 맞는다.
 *
 * <p><b>서버가 여러 대면 각 인스턴스가 이 복구를 따로 돌려 같은 물품에 예약이 여러 개
 * 걸린다.</b> 마감 자체는 물품 행 락과 상태 검사로 걸러져 데이터가 깨지지는 않지만, 누가
 * 집을지 정하는 장치는 예약 저장소를 옮길 때 함께 붙여야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRecoveryRunner {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionCloseScheduler auctionCloseScheduler;

    /**
     * 진행 중인 물품을 모두 읽어 마감 예약을 다시 건다.
     *
     * <p>{@code ApplicationReadyEvent}를 쓰는 것은 이 시점이면 데이터소스와 스케줄러가 모두
     * 떠 있기 때문이다. 빈 초기화 단계에서 돌리면 아직 준비되지 않은 것에 기대게 된다.
     *
     * <p>예외를 삼키고 로그만 남긴다. 복구에 실패했다고 애플리케이션 기동까지 막으면 그
     * 서버는 경매를 아예 받지 못하는데, 그건 물품 몇 개가 안 닫히는 것보다 나쁘다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreCloseSchedules() {

        try {
            List<InProgressAuctionItemProjection> targets =
                    auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS);

            targets.forEach(target ->
                    auctionCloseScheduler.schedule(target.auctionItemId(), target.endAt()));

            log.info("마감 예약 복구 완료: {}건", targets.size());
        } catch (Exception e) {
            log.error("마감 예약 복구 실패. 진행 중이던 물품이 닫히지 않은 채 남는다", e);
        }
    }
}
