package com.hot6ix.upbid.domain.deal.repository;

/**
 * 경매방 거래 현황 한 줄의 원재료. {@link DealSummaryProjection}과 같은 이유로 인터페이스
 * 프로젝션이다 — 거래 상대를 고르는 {@code LATERAL} 조인을 JPQL 생성자 표현식으로 옮길 수 없다.
 *
 * <p>쿼리는 사실만 내보내고 상태 판정은 서비스가 한다. enum 이름을 SQL에 문자열로 박으면
 * 상수명이 바뀌어도 컴파일러가 잡지 못한다.
 *
 * <p><b>"대기 후보가 있나"를 따로 두지 않는다.</b> 거래 중인지는 곧 거래할 상대가 있는지이고,
 * 그 답은 {@code dealCandidateId}가 이미 갖고 있다. 대기 여부를 별도 컬럼으로 두면 탈퇴 회원을
 * 세는 쪽과 빼는 쪽이 갈려서, 상대가 없는데 "거래 중"으로 보이는 물품이 생긴다.
 */
public interface RoomDealStatusProjection {

    Long getAuctionItemId();

    String getProductName();

    /** {@code AuctionItemStatus} 이름. 유찰 판정에 쓴다. */
    String getItemStatus();

    /** 성사된 후보 수. 물품당 최대 하나이므로 사실상 플래그다. */
    Integer getDealCompleted();

    /** 지금 거래 상대인 후보. 상대가 없으면 {@code null} */
    Long getDealCandidateId();

    /** 지금 거래 상대가 부른 금액. 상대가 없으면 {@code null} */
    Long getAmount();

    /** 지금 거래 상대의 닉네임. 상대가 없으면 {@code null} */
    String getPartnerNickname();

    Integer getCandidateCount();

    Integer getFailedCandidateCount();
}
