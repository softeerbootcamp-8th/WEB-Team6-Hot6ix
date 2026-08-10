package com.hot6ix.upbid.domain.deal.recovery;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.deal.repository.AwardRecoveryTargetProjection;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.deal.service.DealCandidateService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DealAwardRecoveryRunnerTest {

    @Mock
    private DealCandidateRepository dealCandidateRepository;

    @Mock
    private DealCandidateService dealCandidateService;

    @InjectMocks
    private DealAwardRecoveryRunner dealAwardRecoveryRunner;

    @Test
    @DisplayName("복구 대상마다 방·물품 ID로 후보 생성을 다시 부른다")
    void awardsEachTargetById() {

        givenTargets(
                new AwardRecoveryTargetProjection(10L, 1L),
                new AwardRecoveryTargetProjection(11L, 1L));

        dealAwardRecoveryRunner.restoreMissingAwards();

        verify(dealCandidateService).award(1L, 10L);
        verify(dealCandidateService).award(1L, 11L);
    }

    @Test
    @DisplayName("대상이 없으면 후보 생성을 부르지 않는다")
    void doesNothingWhenNoTargets() {

        givenTargets();

        dealAwardRecoveryRunner.restoreMissingAwards();

        verify(dealCandidateService, never()).award(anyLong(), anyLong());
    }

    /** 물품마다 별개 트랜잭션이라 한 건이 실패해도 나머지는 그대로 진행돼야 한다. */
    @Test
    @DisplayName("한 물품의 복구가 실패해도 나머지 물품은 계속 처리한다")
    void continuesAfterOneItemFails() {

        givenTargets(
                new AwardRecoveryTargetProjection(10L, 1L),
                new AwardRecoveryTargetProjection(11L, 1L));
        doThrowOnAward(1L, 10L);

        assertThatCode(() -> dealAwardRecoveryRunner.restoreMissingAwards())
                .doesNotThrowAnyException();

        verify(dealCandidateService).award(1L, 10L);
        verify(dealCandidateService).award(1L, 11L);
    }

    @Test
    @DisplayName("대상 조회가 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsQueryFailure() {

        when(dealCandidateRepository.findAwardRecoveryTargets())
                .thenThrow(new IllegalStateException("DB 연결 끊김"));

        assertThatCode(() -> dealAwardRecoveryRunner.restoreMissingAwards())
                .as("복구 조회 실패로 애플리케이션 기동을 막으면 안 된다")
                .doesNotThrowAnyException();
    }

    private void givenTargets(AwardRecoveryTargetProjection... targets) {
        when(dealCandidateRepository.findAwardRecoveryTargets()).thenReturn(List.of(targets));
    }

    private void doThrowOnAward(Long roomId, Long itemId) {
        doThrow(new IllegalStateException("물품 없음")).when(dealCandidateService).award(roomId, itemId);
    }
}
