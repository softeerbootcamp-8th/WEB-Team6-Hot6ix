package com.hot6ix.upbid.domain.deal.entity;

/**
 * 거래 내역에 보이는 물품 단위의 거래 결과. 후보 한 명의 상태({@link DealStatus})가 아니라
 * 그 물품의 거래가 어디까지 왔는지를 나타내며, 판매자와 구매자가 같은 값을 본다.
 *
 * <p>{@code UNSOLD}와 {@code ALL_FAILED}는 둘 다 "거래할 상대가 없이 끝났다"지만 원인이 다르다.
 * 앞은 유효한 입찰이 없어 후보가 만들어지지 않은 것이고, 뒤는 후보가 있었는데 판매자가 전원
 * 실패로 처리한 것이다. 판매자가 취할 조치가 달라서 한 값으로 묶지 않는다 — 전원 실패를
 * {@code IN_PROGRESS}에 섞으면 화면이 "거래 중"으로 보여, 오지 않을 상대를 기다리게 된다.
 *
 */
public enum DealItemStatus {
    IN_PROGRESS,
    COMPLETED,
    UNSOLD,
    ALL_FAILED
}
