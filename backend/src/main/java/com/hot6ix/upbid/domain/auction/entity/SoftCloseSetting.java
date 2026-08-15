package com.hot6ix.upbid.domain.auction.entity;

/**
 * 경매방이 정한 Soft Close 규칙. 임박으로 볼 남은 초와 그때 밀어 줄 초는 언제나 짝으로 쓰여서
 * 한 덩어리로 묶는다. 둘을 {@code Integer} 두 개로 따로 넘기면 순서를 바꿔 넣어도 컴파일이
 * 통과한다.
 *
 * <p><b>물품 행 락을 잡기 전에 읽어 두라고 있는 타입이다.</b> {@link AuctionItem}이 자기
 * {@code auctionRoom}에서 이 값을 직접 꺼내면 경매방이 LAZY라 거기서 SELECT가 나가는데, 그
 * 자리가 하필 입찰이 물품 행 락을 쥐고 있는 구간이다. 락은 줄을 세우는 장치라 한 건이 오래
 * 잡으면 같은 물품에 몰린 입찰 전부가 그만큼 밀린다.
 *
 * @param triggerSeconds 마감까지 이만큼 남으면 임박으로 본다. 설정하지 않았으면 {@code null}
 * @param extendSeconds  임박 구간 입찰이 마감을 밀어 주는 초. 설정하지 않았으면 {@code null}
 */
public record SoftCloseSetting(Integer triggerSeconds, Integer extendSeconds) {

    /**
     * 이 방이 Soft Close를 켜 두었는지.
     *
     * <p>둘 중 하나만 있으면 안 켠 것으로 본다. 연장은 마감을 뒤로 미는 일이라 기준이나 폭 중
     * 하나라도 없으면 임의로 정해서 밀면 안 된다.
     */
    public boolean isConfigured() {
        return triggerSeconds != null && extendSeconds != null;
    }
}
