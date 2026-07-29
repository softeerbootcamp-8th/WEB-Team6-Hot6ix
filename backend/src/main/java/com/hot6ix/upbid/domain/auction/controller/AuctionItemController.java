package com.hot6ix.upbid.domain.auction.controller;

import com.hot6ix.upbid.domain.auction.api.AuctionItemApi;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.service.AuctionItemService;
import com.hot6ix.upbid.global.response.CommonResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuctionItemController implements AuctionItemApi {

    private final AuctionItemService auctionItemService;

    @GetMapping("/auction-rooms/{auctionRoomId}/auction-items")
    @Override
    public ResponseEntity<CommonResponse<List<AuctionItemSummaryResponseDto>>> getSummaries(
            Long auctionRoomId) {

        List<AuctionItemSummaryResponseDto> response = auctionItemService.getSummaries(auctionRoomId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매 물품 목록 조회에 성공했습니다."));
    }

    @GetMapping("/auction-items/{auctionItemId}")
    @Override
    public ResponseEntity<CommonResponse<AuctionItemDetailResponseDto>> getDetail(
            Long auctionItemId) {

        AuctionItemDetailResponseDto response = auctionItemService.getDetail(auctionItemId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매 물품 상세 조회에 성공했습니다."));
    }
}
