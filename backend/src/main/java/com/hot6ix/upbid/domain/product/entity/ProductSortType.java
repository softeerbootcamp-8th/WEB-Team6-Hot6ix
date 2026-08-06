package com.hot6ix.upbid.domain.product.entity;

/**
 * 상품 목록 정렬 기준.
 *
 * <p>두 경우 모두 정렬 키는 불변인 productId다. 등록 시각이 같은 상품이 있어도 순서가
 * 흔들리지 않고, offset 페이지네이션에서 같은 상품이 두 쪽에 겹쳐 나오지 않는다.
 */
public enum ProductSortType {

    /** 최근 등록순(productId 내림차순). 정렬을 생략했을 때의 기본값이다. */
    LATEST,

    /** 오래된 등록순(productId 오름차순). */
    OLDEST
}
