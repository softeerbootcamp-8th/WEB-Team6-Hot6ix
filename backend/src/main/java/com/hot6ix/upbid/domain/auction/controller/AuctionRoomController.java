package com.hot6ix.upbid.domain.auction.controller;

import com.hot6ix.upbid.domain.auction.api.AuctionRoomApi;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomShareResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomService;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomShareService;
import com.hot6ix.upbid.global.interceptor.GuestAllowed;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.response.CursorPageResponse;
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
    private final AuctionRoomShareService auctionRoomShareService;

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
    public ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> getRoom(
            Long userId, @PathVariable Long roomId) {

        AuctionRoomPublicResponseDto response = auctionRoomService.getRoom(roomId, userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매방 정보 조회에 성공했습니다."));
    }

    @GetMapping("/me")
    @Override
    public ResponseEntity<CommonResponse<CursorPageResponse<AuctionRoomListItemResponseDto>>> getMyRooms(
            Long userId, String keyword, AuctionRoomStatus status, Long cursor, Integer size) {

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(userId, keyword, status, cursor, size);

        return ResponseEntity.ok(CommonResponse.ok(response, "내 경매방 목록 조회에 성공했습니다."));
    }

    @GetMapping("/{roomId}/share")
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomShareResponseDto>> getShareInfo(
            Long userId, @PathVariable Long roomId) {

        AuctionRoomShareResponseDto response = auctionRoomShareService.getShareInfo(userId, roomId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매방 공유 링크 조회에 성공했습니다."));
    }

    @GetMapping("/share/{shareCode}")
    @GuestAllowed
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> getRoomByShareCode(
            Long userId, @PathVariable String shareCode) {

        AuctionRoomPublicResponseDto response = auctionRoomService.getRoomByShareCode(shareCode, userId);

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
