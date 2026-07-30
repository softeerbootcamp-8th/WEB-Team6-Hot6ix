package com.hot6ix.upbid.domain.auction.controller;

import com.hot6ix.upbid.domain.auction.api.AuctionRoomApi;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResponseDto;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomService;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auction-rooms")
@RequiredArgsConstructor
public class AuctionRoomController implements AuctionRoomApi {

    private final AuctionRoomService auctionRoomService;

    @PostMapping
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomResponseDto>> create(
            Long userId, AuctionRoomCreateRequestDto request) {

        AuctionRoomResponseDto response = auctionRoomService.create(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.ok(response, "경매방이 생성되었습니다."));
    }
}
