package com.hot6ix.upbid.domain.deal.repository;

import java.time.LocalDateTime;

/**
 * 거래 내역 한 줄의 원재료. 네이티브 UNION 쿼리는 생성자 표현식을 쓸 수 없어 인터페이스
 * 프로젝션으로 받는다.
 *
 * <p>쿼리는 <b>사실만 내보내고 판정은 서비스가 한다.</b> {@code sellerRow}와
 * {@code itemStatus}·{@code dealCompleted}에서 역할과 거래 상태를 계산한다. enum 이름을
 * SQL에 문자열로 박으면 상수명이 바뀌어도 컴파일러가 잡지 못한다.
 */
public interface DealSummaryProjection {

    /** 판매 쪽 원천에서 나온 행이면 1이다. */
    Integer getSellerRow();

    Long getAuctionItemId();

    Long getAuctionRoomId();

    /** 거래 상세 화면이 물품 상세를 부를 때 방을 지목하는 공개 식별자. */
    String getShareCode();

    /** 판매 건만 값이 있다. 내가 산 물건은 내 상품이 아니다. */
    Long getProductId();

    String getProductName();

    /** 상품 사진 주소. 판매자가 안 올렸으면 {@code null} */
    String getImageUrl();

    String getAuctionRoomName();

    /** {@code AuctionItemStatus} 이름. 유찰 판정에 쓴다. */
    String getItemStatus();

    /** 이 물품에 성사된 후보가 있으면 1이다. */
    Integer getDealCompleted();

    /**
     * 이 물품에 아직 처리되지 않은 후보가 있으면 1이다. 성사된 후보도 대기 후보도 없으면
     * 후보가 전원 실패한 것이라, 이 값이 유찰과 전원 실패를 가른다.
     */
    Integer getHasWaitingCandidate();

    /** 구매 건이면 내 후보 상태, 판매 건이면 null */
    String getMyCandidateStatus();

    /** 구매 건이면 내 차례인지 여부(1 또는 0), 판매 건이면 0 */
    Integer getMyTurn();

    Long getAmount();

    /** 판매 건이면 거래 상대 후보, 구매 건이면 판매자. 거래 상대가 없으면 {@code null} */
    String getPartnerNickname();

    /** 구매자가 판매자에게 연락할 때 쓰는 프로필 조회 키 */
    Long getSellerProfileId();

    LocalDateTime getClosedAt();
}
