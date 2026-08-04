package com.hot6ix.upbid.domain.deal.dto.response;

import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;

/**
 * 판매자가 보는 물품 한 건의 거래 진행 상황.
 *
 * <p>연락처는 내려가지 않는다. 방 전체를 훑는 목록이라 물품 수만큼 개인정보를 들고 다니게
 * 되고, 판매자가 실제로 연락할 때는 그 물품의 낙찰 후보 조회로 들어간다.
 *
 * @param amount           지금 거래 상대가 부른 금액. 상대가 없으면 {@code null}.
 *                         <b>낙찰가가 아니다</b> — 1순위가 실패해 차순위로 넘어가면 실제 거래
 *                         금액도 그 후보의 입찰가로 바뀐다
 * @param dealCandidateId  지금 거래 상대인 후보의 ID. 화면이 거래 성사·실패를 이 값으로
 *                         호출한다. 상대가 없으면 {@code null}
 * @param partnerNickname  지금 거래 상대의 닉네임. 상대가 없으면 {@code null}
 * @param candidateCount   이 물품의 낙찰 후보 수
 * @param failedCandidateCount 실패로 처리된 후보 수. 몇 번 승계됐는지가 드러난다
 */
public record AuctionItemDealStatusResponseDto(
        Long auctionItemId,
        String productName,
        DealItemStatus dealStatus,
        Long amount,
        Long dealCandidateId,
        String partnerNickname,
        Integer candidateCount,
        Integer failedCandidateCount
) {
}
