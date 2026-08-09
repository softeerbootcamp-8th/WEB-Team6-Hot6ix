package com.hot6ix.upbid.domain.auction.entity;

/**
 * 경매방이 정한 Soft Close 규칙. 마감이 얼마나 남았을 때부터 연장 구간으로 보고(트리거),
 * 한 번에 얼마나 미룰지(연장 폭)를 담는다.
 *
 * <p>두 값을 묶어 두는 것은 {@link AuctionItem#extendIfClosingSoon}에 {@code Integer} 둘을
 * 나란히 넘기면 <b>순서를 바꿔 넣어도 컴파일이 되기 때문이다.</b> 그러면 트리거와 연장 폭이
 * 뒤바뀐 채로 마감이 돌게 되는데, 값이 둘 다 60 이상이라 테스트에서도 잘 안 드러난다.
 *
 * <p>경매방에 설정이 없으면 두 값이 {@code null}이고, 그때는 연장하지 않는다.
 */
public record SoftClosePolicy(Integer triggerSeconds, Integer extendSeconds) {

    /** 둘 중 하나라도 비어 있으면 연장 판단을 할 수 없다. */
    public boolean isConfigured() {
        return triggerSeconds != null && extendSeconds != null;
    }
}
