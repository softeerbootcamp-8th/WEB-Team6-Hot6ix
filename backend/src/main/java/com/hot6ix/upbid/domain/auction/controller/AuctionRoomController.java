package com.hot6ix.upbid.domain.auction.controller;

import com.hot6ix.upbid.domain.auction.api.AuctionRoomApi;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomService;
import com.hot6ix.upbid.global.interceptor.GuestAllowed;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> create(
            Long userId, AuctionRoomCreateRequestDto request) {

        AuctionRoomPublicResponseDto response = auctionRoomService.create(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.ok(response, "경매방이 생성되었습니다."));
    }

    @GetMapping("/{roomId}")
    @GuestAllowed
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> getRoom(@PathVariable Long roomId) {

        AuctionRoomPublicResponseDto response = auctionRoomService.getRoom(roomId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매방 정보 조회에 성공했습니다."));
    }

    @PatchMapping("/{roomId}")
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> update(
            Long userId, Long roomId, AuctionRoomUpdateRequestDto request) {

        AuctionRoomPublicResponseDto response = auctionRoomService.update(userId, roomId, request);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매방이 수정되었습니다."));
    }
}
