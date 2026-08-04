package com.hot6ix.upbid.domain.deal.entity;

import java.util.Comparator;
import java.util.List;

/**
 * 한 물품의 낙찰 후보들을 읽어 거래가 어디까지 왔는지 계산한 결과.
 *
 * <p><b>왜 자바인가.</b> 이 규칙은 원래 SQL 서브쿼리로 두 번, {@link DealCandidate} 주석으로
 * 한 번, {@code DealCandidateService} 의 조회 메서드로 한 번 적혀 있었다. 같은 규칙이 언어를
 * 넘나들며 흩어지면 한쪽만 고쳐도 아무도 모른다. 규칙 자체는 후보 목록만 있으면 계산되므로
 * 여기 한 곳에 둔다.
 *
 * <p>{@code DealCandidateService} 의 거래 성사·실패 처리는 이 클래스를 쓰지 않는다. 그쪽은
 * 물품 행에 락을 걸고 후보를 하나씩 읽어야 하는 <b>쓰기 경로</b>라 접근 방식이 다르다.
 * 이 클래스는 읽기 전용 조회가 쓴다.
 *
 * @param partner        지금 거래할 상대. 없으면 {@code null}
 * @param completed      성사된 후보가 있는지
 * @param candidateCount 후보 수. 탈퇴 회원도 센다 — 몇 명이 후보였는지는 지나간 사실이다
 * @param failedCount    실패로 처리된 후보 수. 몇 번 승계됐는지가 드러난다
 */
public record DealProgress(
        DealCandidate partner,
        boolean completed,
        int candidateCount,
        int failedCount
) {
    /**
     * 거래 상대를 고르는 순서. 성사된 후보가 먼저고, 그다음은 순위가 낮은 쪽이다.
     * 마지막 키는 순위가 겹쳐도 상대가 하나로 정해지게 한다 —
     * {@code (auction_item_id, candidate_rank)} 에 DB 유니크 제약이 없어 중복이 가능하다.
     */
    private static final Comparator<DealCandidate> PARTNER_ORDER =
            Comparator.<DealCandidate, Integer>comparing(
                            candidate -> candidate.getStatus() == DealCandidateStatus.COMPLETED ? 0 : 1)
                    .thenComparing(DealCandidate::getCandidateRank)
                    .thenComparing(DealCandidate::getDealCandidateId);

    /**
     * @param candidates 한 물품의 후보 전체. 탈퇴 회원도 걸러내지 않은 그대로를 넘긴다 —
     *                   상대 선정에서는 빼지만 후보 수에는 넣어야 해서, 필터를 여기서 나눈다
     */
    public static DealProgress of(List<DealCandidate> candidates) {

        DealCandidate partner = candidates.stream()
                // 실패한 후보는 이미 지나간 상대다. 탈퇴 회원은 후보로 취급하지 않으므로
                // 순위가 다음 후보에게 넘어가 있다.
                .filter(candidate -> candidate.getStatus() != DealCandidateStatus.FAILED)
                .filter(candidate -> !candidate.getBidder().isDeleted())
                .min(PARTNER_ORDER)
                .orElse(null);

        boolean completed = candidates.stream()
                .anyMatch(candidate -> candidate.getStatus() == DealCandidateStatus.COMPLETED);

        int failedCount = (int) candidates.stream()
                .filter(candidate -> candidate.getStatus() == DealCandidateStatus.FAILED)
                .count();

        return new DealProgress(partner, completed, candidates.size(), failedCount);
    }

    /** 후보가 없는 물품. 입찰이 하나도 없어 유찰된 경우다. */
    public static DealProgress empty() {
        return new DealProgress(null, false, 0, 0);
    }

    public boolean hasPartner() {
        return partner != null;
    }

    /** 상대가 없으면 {@code null}. 낙찰가가 아니라 지금 상대가 부른 금액이다. */
    public Long partnerAmount() {
        return partner == null ? null : partner.getBidAmount();
    }

    public Long partnerCandidateId() {
        return partner == null ? null : partner.getDealCandidateId();
    }

    public String partnerNickname() {
        return partner == null ? null : partner.getBidder().getNickname();
    }
}
