package com.hot6ix.upbid.domain.deal.recovery;

import com.hot6ix.upbid.domain.deal.repository.AwardRecoveryTargetProjection;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.deal.service.DealCandidateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "upbid.deal.award-recovery.enabled", matchIfMissing = true)
public class DealAwardRecoveryRunner {

    private final DealCandidateRepository dealCandidateRepository;
    private final DealCandidateService dealCandidateService;

    /**
     * 낙찰됐는데 후보가 없는 물품을 전부 찾아 후보를 다시 만든다.
     *
     * <p>물품마다 별개 트랜잭션({@code REQUIRES_NEW})이므로 <b>항목별로 예외를 잡는다.</b>
     * 전체를 하나의 {@code try}로 감싸면 한 건이 실패했을 때 나머지 물품이 전부 건너뛰어진다.
     *
     * <p>조회 자체가 실패해도 예외를 삼킨다. 복구에 실패했다고 애플리케이션 기동까지 막으면
     * 그 서버는 경매를 아예 받지 못하는데, 그건 후보 몇 건이 안 만들어지는 것보다 나쁘다
     * ({@code AuctionRecoveryRunner}와 같은 이유).
     */
    @Scheduled(fixedDelayString = "${upbid.deal.award-recovery.interval-ms}")
    public void restoreMissingAwards() {

        try {
            List<AwardRecoveryTargetProjection> targets =
                    dealCandidateRepository.findAwardRecoveryTargets();

            if (targets.isEmpty()) {
                return;
            }

            log.warn("낙찰 후보 복구 대상 {}건 발견", targets.size());

            targets.forEach(this::awardSafely);
        } catch (Exception e) {
            log.error("낙찰 후보 복구 조회 실패", e);
        }
    }

    private void awardSafely(AwardRecoveryTargetProjection target) {
        try {
            dealCandidateService.award(target.auctionRoomId(), target.auctionItemId());
        } catch (Exception e) {
            log.error("낙찰 후보 복구 실패: itemId={}", target.auctionItemId(), e);
        }
    }
}
