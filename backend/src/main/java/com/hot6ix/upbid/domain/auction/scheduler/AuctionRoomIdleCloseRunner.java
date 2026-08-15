package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomCloseService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 물품이 전부 마감됐는데 판매자가 종료하지 않은 경매방을 주기적으로 찾아 닫는다(#284).
 *
 * <p>방을 종료하는 유일한 수단이 판매자의 종료 API 라, 판매자가 방송을 마치고 그냥 나가면
 * 방이 계속 {@code OPEN}으로 남는다. 목록과 지표에서 지금 하는 방송처럼 보이는 것이 문제라
 * 서버가 대신 정리한다.
 *
 * <p><b>예약을 걸지 않고 주기마다 훑는다.</b> 물품을 마감할 때 12시간 뒤를 예약해 두는 방식도
 * 되지만, 그러려면 마감할 때마다 "이게 이 방의 마지막 물품인가"를 판단하고 물품이 새로
 * 시작되면 예약을 취소해야 하며 Redis 가 비었을 때 채워 줄 재동기화도 따로 필요하다.
 * 12시간을 재는 일에 그만한 정밀도가 필요하지 않다. 훑는 쪽은 상태가 DB 에만 있어서
 * 재시작 복구도 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRoomIdleCloseRunner {

    private final AuctionRoomRepository auctionRoomRepository;
    private final AuctionRoomCloseService auctionRoomCloseService;
    private final AuctionProperties auctionProperties;

    /**
     * 종료 대상 방을 찾아 하나씩 닫는다.
     *
     * <p><b>분산 락으로 한 서버만 돈다.</b> 겹쳐도 방 행 락과 {@code closeIfIdle}의 재검사가
     * 막아 주지만, 막히기 전에 {@code RoomClosed}가 두 번 나갈 수 있다.
     *
     * <p>{@code lockAtLeastFor}는 서버 간 시각이 조금 어긋나 같은 tick 이 두 번 도는 것만
     * 막으면 되므로 실행 주기보다 짧게 잡는다.
     *
     * <p>기준 시각을 여기서 한 번만 계산해 목록 조회와 방마다의 재검사가 같은 값을 쓰게 한다.
     * 방을 닫는 동안 시간이 흘러 재검사 기준이 미세하게 늦춰지면, 목록에는 있는데 재검사에서
     * 빠지는 방이 생긴다.
     */
    @Scheduled(fixedDelayString = "${upbid.auction.room.idle-close-interval-ms}")
    @SchedulerLock(name = "auction-room-idle-close", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void closeIdleRooms() {

        AuctionProperties.Room room = auctionProperties.room();
        LocalDateTime idleBefore = LocalDateTime.now().minus(room.idleCloseAfter());

        List<Long> targets = findTargets(idleBefore, room.idleCloseBatchSize());

        if (targets.isEmpty()) {
            return;
        }

        long closed = targets.stream()
                .filter(auctionRoomId -> close(auctionRoomId, idleBefore))
                .count();

        log.info("방치된 경매방 자동 종료: 대상 {}건, 종료 {}건, 기준={}",
                targets.size(), closed, idleBefore);
    }

    /**
     * 예외를 삼키고 빈 목록을 준다. 스케줄러 스레드로 예외가 올라가면 이 작업이 아예 멈추는데,
     * 그건 방 몇 개가 안 닫히는 것보다 나쁘다. 다음 주기에 다시 조회한다.
     */
    private List<Long> findTargets(LocalDateTime idleBefore, int batchSize) {
        try {
            return auctionRoomRepository.findIdleRoomIds(idleBefore, Limit.of(batchSize));
        } catch (Exception e) {
            log.error("자동 종료 대상 조회 실패. 방치된 경매방이 계속 열려 있게 된다", e);
            return List.of();
        }
    }

    /**
     * 방 하나를 닫는다. <b>실패해도 나머지 방은 계속 닫는다.</b> 방마다 트랜잭션이 따로라
     * 하나가 롤백돼도 앞서 닫힌 방은 그대로 남는다.
     *
     * @return 실제로 닫혔으면 {@code true}. 그사이 물품이 다시 시작돼 대상에서 빠졌거나
     *         실패했으면 {@code false}
     */
    private boolean close(Long auctionRoomId, LocalDateTime idleBefore) {
        try {
            return auctionRoomCloseService.closeIfIdle(auctionRoomId, idleBefore);
        } catch (Exception e) {
            log.error("경매방 자동 종료 실패. 다음 주기에 다시 집힌다: roomId={}", auctionRoomId, e);
            return false;
        }
    }
}
