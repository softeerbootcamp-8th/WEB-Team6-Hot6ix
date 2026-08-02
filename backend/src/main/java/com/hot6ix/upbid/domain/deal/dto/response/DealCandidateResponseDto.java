package com.hot6ix.upbid.domain.deal.dto.response;

import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealStatus;

/**
 * 후보 한 명. 닉네임과 입찰가는 경매 결과라 모두에게 공개하고, 연락처만 가린다.
 *
 * @param phoneNumber 판매자가 볼 때 거래 상대인 후보만 값이 있다. 나머지는 {@code null}이라
 *                    {@code @JsonInclude(NON_NULL)}에 걸려 응답에서 빠진다
 * @param isMe        구매자가 목록에서 자기 행을 짚기 위한 값
 */
public record DealCandidateResponseDto(
        Long dealCandidateId,
        Integer candidateRank,
        String nickname,
        Long bidAmount,
        DealStatus dealStatus,
        String phoneNumber,
        boolean isMe
) {
    public static DealCandidateResponseDto of(
            DealCandidate candidate, DealStatus dealStatus, boolean contactVisible, boolean isMe) {

        return new DealCandidateResponseDto(
                candidate.getDealCandidateId(),
                candidate.getCandidateRank(),
                candidate.getBidder().getNickname(),
                candidate.getBidAmount(),
                dealStatus,
                contactVisible ? candidate.getBidder().getPhoneNumber() : null,
                isMe);
    }
}
