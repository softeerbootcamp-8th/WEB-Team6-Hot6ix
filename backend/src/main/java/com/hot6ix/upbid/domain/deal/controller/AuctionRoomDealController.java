package com.hot6ix.upbid.domain.deal.controller;

import com.hot6ix.upbid.domain.deal.api.AuctionRoomDealApi;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionRoomDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.service.DealService;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경매방을 기준으로 거래를 보는 조회. {@link DealController}와 나눈 이유는 경로가 다르기
 * 때문이다 — 그쪽은 {@code /api/v1/deals}에 묶여 있고, 이 조회는 방에 딸린 자원이다.
 */
@RestController
@RequestMapping("/api/v1/auction-rooms/{roomId}/deals")
@RequiredArgsConstructor
public class AuctionRoomDealController implements AuctionRoomDealApi {

    private final DealService dealService;

    @GetMapping
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomDealStatusResponseDto>> getRoomDeals(
            @PathVariable Long roomId, Long userId) {

        AuctionRoomDealStatusResponseDto response = dealService.getRoomDeals(roomId, userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매방 거래 현황 조회에 성공했습니다."));
    }
}
