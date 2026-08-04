package com.hot6ix.upbid.domain.auction.dto.request;

import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;

/**
 * 경매방 물품 벌크 추가(POST /api/v1/auction-rooms/{auctionRoomId}/auction-items/bulk) 요청 바디.
 * 항목 하나하나는 단건 추가와 같은 형식이라 {@link AuctionItemAddRequestDto}를 그대로 재사용한다.
 *
 * <p>배열 길이 상한은 경매방당 물품 상한과 같은 값이다. 한 요청으로 상한을 넘길 수 없게 해
 * 서비스의 개수 검사에 도달하기 전에 걸러낸다.
 */
@Builder
public record AuctionItemBulkAddRequestDto(

        @NotEmpty(message = "추가할 물품은 하나 이상이어야 합니다.")
        @Size(max = AuctionItemRepository.MAX_SUMMARY_SIZE,
                message = "한 번에 최대 " + AuctionItemRepository.MAX_SUMMARY_SIZE + "개까지 추가할 수 있습니다.")
        @Valid
        List<AuctionItemAddRequestDto> items
) {
}
