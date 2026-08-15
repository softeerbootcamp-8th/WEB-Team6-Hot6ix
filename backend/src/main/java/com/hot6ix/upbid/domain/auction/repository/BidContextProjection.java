package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.SoftCloseSetting;

/**
 * 입찰 한 건을 처리하는 데 필요한 주변 값만 모은 한 줄. 물품 행 락을 잡기 전에 읽는다.
 *
 * <p><b>여기 담긴 값은 입찰하는 동안 바뀌지 않는다.</b> 상품명도 경매방의 Soft Close 설정도
 * 입찰이 건드리는 값이 아니라 락 안에서 읽을 이유가 없는데, 엔티티에서 꺼내면 경매방과 상품이
 * LAZY라 그 자리에서 SELECT가 나간다. 그 쿼리가 락 안에 있으면 같은 물품에 몰린 다른 입찰이
 * 그만큼 뒤로 밀린다.
 *
 * <p>판매자 ID 하나만 읽던 자리를 넓힌 것이라 <b>입찰당 SELECT 수는 늘지 않는다.</b>
 * 경매방은 판매자를 찾느라 이미 조인하고 있어서 Soft Close 설정은 그대로 딸려 오고, 상품 조인
 * 하나만 늘었다.
 *
 * @param sellerUserId           물품을 올린 판매자의 회원 ID. 판매자 본인 입찰을 거른다
 * @param productName            실시간 이벤트에 실어 보낼 상품명
 * @param softCloseTriggerSeconds 임박으로 볼 남은 초. 설정하지 않은 방이면 {@code null}
 * @param softCloseExtendSeconds  임박 구간 입찰이 마감을 밀어 주는 초. 마찬가지로 {@code null}일 수 있다
 */
public record BidContextProjection(
        Long sellerUserId,
        String productName,
        Integer softCloseTriggerSeconds,
        Integer softCloseExtendSeconds
) {

    /** 연장 판정에 넘길 형태로 묶어 준다. 두 값이 언제나 짝으로 쓰여서 따로 꺼낼 일이 없다. */
    public SoftCloseSetting softCloseSetting() {
        return new SoftCloseSetting(softCloseTriggerSeconds, softCloseExtendSeconds);
    }
}
