package com.hot6ix.upbid.domain.deal.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 거래 상대 선정 규칙의 시험대. 예전에는 이 규칙이 네이티브 SQL 서브쿼리로 적혀 있어
 * Testcontainers 를 띄워야 확인할 수 있었다. 규칙을 자바로 옮긴 뒤로는 DB 없이 검증한다.
 */
class DealProgressTest {

    private static final LocalDateTime RESOLVED_AT = LocalDateTime.of(2026, 7, 29, 22, 0);

    /**
     * 후보 하나. {@code dealCandidateId} 는 DB가 만드는 값이라 빌더에 없지만, 정렬 마지막 키로
     * 쓰이므로 넣어 준다 — 조회로 올라온 후보에는 언제나 값이 있다.
     */
    private DealCandidate candidate(long id, int rank, long amount, String nickname) {
        DealCandidate candidate = DealCandidate.builder()
                .bidder(User.builder()
                        .email(nickname + "@hot6ix.com")
                        .password("password")
                        .nickname(nickname)
                        .phoneNumber("010-1234-5678")
                        .build())
                .candidateRank(rank)
                .bidAmount(amount)
                .build();
        ReflectionTestUtils.setField(candidate, "dealCandidateId", id);
        return candidate;
    }

    @Test
    @DisplayName("아무도 실패하지 않았으면 1순위가 거래 상대다")
    void partnerIsTopRank() {

        DealProgress progress = DealProgress.of(List.of(
                candidate(51L, 1, 20_000L, "1순위"),
                candidate(52L, 2, 12_000L, "2순위")));

        assertThat(progress.partnerNickname()).isEqualTo("1순위");
        assertThat(progress.partnerAmount()).isEqualTo(20_000L);
        assertThat(progress.partnerCandidateId()).isEqualTo(51L);
        assertThat(progress.candidateCount()).isEqualTo(2);
        assertThat(progress.failedCount()).isZero();
    }

    /** 실패한 후보는 이미 지나간 상대다. 상대·금액·후보 ID가 모두 함께 넘어가야 한다. */
    @Test
    @DisplayName("1순위가 실패하면 상대·금액·후보 ID가 모두 2순위로 넘어간다")
    void partnerSucceedsToNextRank() {

        DealCandidate first = candidate(51L, 1, 20_000L, "1순위");
        first.fail(RESOLVED_AT);

        DealProgress progress = DealProgress.of(List.of(first, candidate(52L, 2, 12_000L, "2순위")));

        assertThat(progress.partnerNickname()).isEqualTo("2순위");
        assertThat(progress.partnerAmount()).isEqualTo(12_000L);
        assertThat(progress.partnerCandidateId()).isEqualTo(52L);
        assertThat(progress.failedCount()).isEqualTo(1);
        assertThat(progress.completed()).isFalse();
    }

    /** 거래가 끝난 뒤에도 하위 순위는 WAITING 으로 남는다. 성사된 후보가 상대여야 한다. */
    @Test
    @DisplayName("성사된 후보가 있으면 대기 후보가 남아도 그 후보가 상대다")
    void completedCandidateWinsOverWaiting() {

        DealCandidate second = candidate(52L, 2, 12_000L, "2순위");
        second.complete(RESOLVED_AT);
        DealCandidate first = candidate(51L, 1, 20_000L, "1순위");
        first.fail(RESOLVED_AT);

        DealProgress progress = DealProgress.of(List.of(first, second, candidate(53L, 3, 9_000L, "3순위")));

        assertThat(progress.partnerNickname()).isEqualTo("2순위");
        assertThat(progress.completed()).isTrue();
    }

    @Test
    @DisplayName("후보가 전원 실패하면 상대가 없다")
    void noPartnerWhenAllFailed() {

        DealCandidate only = candidate(51L, 1, 20_000L, "유일후보");
        only.fail(RESOLVED_AT);

        DealProgress progress = DealProgress.of(List.of(only));

        assertThat(progress.hasPartner()).isFalse();
        assertThat(progress.partnerAmount()).isNull();
        assertThat(progress.partnerCandidateId()).isNull();
        assertThat(progress.candidateCount()).isEqualTo(1);
        assertThat(progress.failedCount()).isEqualTo(1);
    }

    /** 탈퇴한 회원은 후보로 취급하지 않으므로 순위가 다음 후보에게 넘어가 있다. */
    @Test
    @DisplayName("탈퇴한 후보는 상대로 뽑히지 않고 다음 순위로 넘어간다")
    void skipsWithdrawnCandidate() {

        DealCandidate withdrawn = candidate(51L, 1, 20_000L, "탈퇴자");
        withdrawn.getBidder().softDelete(RESOLVED_AT);

        DealProgress progress = DealProgress.of(List.of(withdrawn, candidate(52L, 2, 12_000L, "2순위")));

        assertThat(progress.partnerNickname()).isEqualTo("2순위");
        assertThat(progress.partnerAmount()).isEqualTo(12_000L);
    }

    /**
     * 후보 수에는 탈퇴자를 넣는다. 몇 명이 후보였는지는 지나간 사실이고, 실패 횟수와 짝을 이뤄
     * 몇 번 승계됐는지를 보여준다. 상대 선정과 모집단이 달라야 하는 지점이다.
     */
    @Test
    @DisplayName("상대에서 빠진 탈퇴자도 후보 수에는 들어간다")
    void countsWithdrawnCandidate() {

        DealCandidate withdrawn = candidate(51L, 1, 20_000L, "탈퇴자");
        withdrawn.getBidder().softDelete(RESOLVED_AT);

        DealProgress progress = DealProgress.of(List.of(withdrawn, candidate(52L, 2, 12_000L, "2순위")));

        assertThat(progress.candidateCount()).isEqualTo(2);
        assertThat(progress.failedCount()).isZero();
    }

    /**
     * 유일한 대기 후보가 탈퇴하면 후보는 남아 있는데 거래할 상대가 없다. 대기 여부를 따로 세면
     * 여기서 "거래 중"이 나와, 판매자가 오지 않을 상대를 기다린다.
     */
    @Test
    @DisplayName("유일한 대기 후보가 탈퇴하면 후보는 남고 상대만 없다")
    void noPartnerWhenOnlyCandidateWithdrew() {

        DealCandidate withdrawn = candidate(51L, 1, 20_000L, "탈퇴자");
        withdrawn.getBidder().softDelete(RESOLVED_AT);

        DealProgress progress = DealProgress.of(List.of(withdrawn));

        assertThat(progress.hasPartner()).isFalse();
        assertThat(progress.candidateCount()).isEqualTo(1);
        assertThat(progress.failedCount()).isZero();
    }

    /**
     * {@code (auction_item_id, candidate_rank)} 에 DB 유니크 제약이 없어 순위 중복이 가능하다.
     * 마지막 정렬 키가 없으면 요청마다 다른 상대가 나올 수 있다.
     */
    @Test
    @DisplayName("순위가 겹치면 후보 ID가 작은 쪽이 상대로 정해진다")
    void breaksRankTieByCandidateId() {

        DealProgress progress = DealProgress.of(List.of(
                candidate(52L, 1, 20_000L, "나중행"),
                candidate(51L, 1, 20_000L, "먼저행")));

        assertThat(progress.partnerCandidateId()).isEqualTo(51L);
        assertThat(progress.partnerNickname()).isEqualTo("먼저행");
    }

    @Test
    @DisplayName("후보가 없는 물품은 상대도 없고 수도 0이다")
    void empty() {

        DealProgress progress = DealProgress.empty();

        assertThat(progress.hasPartner()).isFalse();
        assertThat(progress.completed()).isFalse();
        assertThat(progress.candidateCount()).isZero();
        assertThat(progress.failedCount()).isZero();
    }
}
