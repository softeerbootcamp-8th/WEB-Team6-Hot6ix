package com.hot6ix.upbid.domain.deal.repository;

/**
 * 경매방 거래 현황 한 줄의 원재료. {@link DealSummaryProjection}과 같은 이유로 인터페이스
 * 프로젝션이다 — 거래 상대를 고르는 서브쿼리가 상관 서브쿼리라 JPQL 생성자 표현식으로 옮길 수
 * 없다.
 *
 * <p>쿼리는 사실만 내보내고 상태 판정은 서비스가 한다. enum 이름을 SQL에 문자열로 박으면
 * 상수명이 바뀌어도 컴파일러가 잡지 못한다.
 */
public interface RoomDealStatusProjection {

    Long getAuctionItemId();

    String getProductName();

    /** {@code AuctionItemStatus} 이름. 유찰 판정에 쓴다. */
    String getItemStatus();

    /** 성사된 후보가 있으면 1이다. */
    Integer getDealCompleted();

    /** 아직 처리되지 않은 후보가 있으면 1이다. */
    Integer getHasWaitingCandidate();

    /** 지금 거래 상대인 후보. 상대가 없으면 {@code null} */
    Long getDealCandidateId();

    /** 지금 거래 상대가 부른 금액. 상대가 없으면 {@code null} */
    Long getAmount();

    /** 지금 거래 상대의 닉네임. 상대가 없으면 {@code null} */
    String getPartnerNickname();

    Integer getCandidateCount();

    Integer getFailedCandidateCount();
}
