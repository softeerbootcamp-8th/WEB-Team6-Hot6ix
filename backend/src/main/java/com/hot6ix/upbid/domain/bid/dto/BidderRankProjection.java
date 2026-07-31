package com.hot6ix.upbid.domain.bid.dto;

/**
 * 낙찰 순위 산정용 조회 결과. 입찰자 한 명당 한 행이며 최고 입찰가를 담는다.
 *
 * <p>native 쿼리라 생성자 표현식({@code select new ...})을 쓸 수 없어 인터페이스
 * projection으로 받는다. 응답으로 나가는 DTO가 아니라 영속성 계층의 읽기 형태이므로
 * record가 아닌 인터페이스이고 이름도 {@code ...Projection}으로 둔다.
 */
public interface BidderRankProjection {

    Long getBidderUserId();

    Long getAmount();
}
