package com.hot6ix.upbid.domain.deal.entity;

/**
 * 거래 내역에 보이는 물품 단위의 거래 결과. 후보 한 명의 상태({@link DealStatus})가 아니라
 * 그 물품의 거래가 어디까지 왔는지를 나타내며, 판매자와 구매자가 같은 값을 본다.
 *
 * <p>{@code UNSOLD}는 유효한 입찰이 없어 후보 자체가 만들어지지 않은 경우다.
 * <b>후보가 있었는데 전원 실패한 물품은 아직 자리가 없다</b> — 화면에 그 상태가 없어
 * 프론트 연동 때 함께 정한다. 지금은 {@code IN_PROGRESS}로 남는다.
 */
public enum DealItemStatus {
    IN_PROGRESS,
    COMPLETED,
    UNSOLD
}
