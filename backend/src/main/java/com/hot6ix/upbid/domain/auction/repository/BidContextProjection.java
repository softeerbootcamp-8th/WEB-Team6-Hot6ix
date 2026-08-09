package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.SoftClosePolicy;

/**
 * 입찰이 <b>물품 행 락을 잡기 전에</b> 미리 읽어 두는 값들. 락과 아무 상관이 없는데 지연 로딩
 * 때문에 하필 락 안에서 읽히던 것들을 여기로 모았다.
 *
 * <p>락은 줄을 세우는 장치라 한 요청이 오래 잡고 있으면 그 시간이 뒤에 선 요청 수만큼
 * 곱해진다. 그래서 락 안에서 하는 일은 <b>락이 필요한 것만</b> 남겨야 한다.
 *
 * <p>여기 담긴 값은 전부 입찰 도중에 바뀌지 않는다. 판매자와 상품명은 물품이 만들어질 때
 * 정해지고, Soft Close 설정은 경매방 설정이라 경매가 시작된 뒤에는 바꿀 수 없다. 그래서
 * 락 앞에서 읽어도 낡은 값을 쓸 위험이 없다.
 *
 * <p>반대로 현재가와 마감 시각, 상태는 여기 없다. 그건 입찰마다 바뀌므로 반드시 락을 잡고
 * 다시 읽어야 한다.
 */
public record BidContextProjection(
        Long sellerUserId,
        Long roomId,
        String itemName,
        Integer softCloseTriggerSeconds,
        Integer softCloseExtendSeconds
) {

    /**
     * JPQL 생성자 표현식은 중첩 {@code new}를 안정적으로 못 써서 두 값을 평평하게 받고
     * 여기서 묶는다. 쓰는 쪽은 {@link SoftClosePolicy} 하나만 넘기면 된다.
     */
    public SoftClosePolicy softClosePolicy() {
        return new SoftClosePolicy(softCloseTriggerSeconds, softCloseExtendSeconds);
    }
}
