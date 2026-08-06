package com.hot6ix.upbid.domain.deal.entity;

/**
 * 거래 내역에 보이는 물품 단위의 거래 결과.
 *
 * <p>판매 건은 물품 단위 4값({@code IN_PROGRESS}, {@code COMPLETED}, {@code UNSOLD}, {@code ALL_FAILED})을 쓰고,
 * 구매 건은 내 후보의 {@link DealStatus} 와 같은 4값({@code WAITING}, {@code IN_PROGRESS}, {@code COMPLETED}, {@code FAILED})을 쓴다.
 * 
 * <p>판매 건에서 {@code UNSOLD}와 {@code ALL_FAILED}는 둘 다 "거래할 상대가 없이 끝났다"지만 원인이 다르다.
 * 앞은 유효한 입찰이 없어 후보가 만들어지지 않은 것이고, 뒤는 후보가 있었는데 판매자가 전원
 * 실패로 처리한 것이다. 판매자가 취할 조치가 달라서 한 값으로 묶지 않는다 — 전원 실패를
 * {@code IN_PROGRESS}에 섞으면 화면이 "거래 중"으로 보여, 오지 않을 상대를 기다리게 된다.
 *
 * <p>{@code AuctionItemDealStatusResponseDto} 도 같은 enum 을 쓰므로 springdoc 스키마에는 6값이 다 뜬다.
 * 그 응답은 판매자 전용이라 신규 2값({@code WAITING}, {@code FAILED})이 실제로 나올 수 없다.
 */
public enum DealItemStatus {
    IN_PROGRESS,   // 판매: 물품 거래 진행 중 / 구매: 지금 내 차례
    COMPLETED,     // 판매/구매 공통: 내가 거래를 성사시킴
    UNSOLD,        // 판매 건만 — 입찰이 없어 유찰
    ALL_FAILED,    // 판매 건만 — 후보가 있었는데 전원 실패
    WAITING,       // 구매 건만 — 앞 순위가 진행 중이라 내 차례가 아직 아님
    FAILED         // 구매 건만 — 내 후보가 실패했거나 앞 순위가 성사해 기회가 없음
}
