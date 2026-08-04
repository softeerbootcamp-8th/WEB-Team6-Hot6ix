package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.global.exception.ErrorType;
import java.util.List;

/**
 * 경매방 물품 벌크 추가 결과. 거절된 상품이 있어도 나머지는 추가되므로 <b>성공·실패가 한
 * 응답에 함께 담긴다.</b> 전부 거절돼 {@code added}가 비어도 201이며, 성공 여부는
 * {@code failed}가 비었는지로 판단한다 — "일부 실패는 2xx, 전부 실패는 4xx"로 가르면
 * 한 건만 성공한 경계에서 프론트 처리가 갈린다.
 *
 * @param added  추가된 물품. 단건 추가 응답과 같은 모양이라 프론트가 이미 아는 타입이다
 * @param failed 거절된 상품과 그 이유
 */
public record AuctionItemBulkAddResponseDto(
        List<AuctionItemDetailResponseDto> added,
        List<Failure> failed
) {

    /**
     * 거절 사유. {@code code}·{@code message}는 단건 추가가 같은 상황에서 내보내는 실패 응답과
     * 값이 같다 — 프론트가 문구를 한 벌만 들고 있으면 되도록 새 사유 코드를 만들지 않는다.
     */
    public record Failure(Long productId, Integer code, String message) {

        public static Failure of(Long productId, ErrorType errorType) {
            return new Failure(productId, errorType.getErrorCode(), errorType.getMessage());
        }
    }
}
