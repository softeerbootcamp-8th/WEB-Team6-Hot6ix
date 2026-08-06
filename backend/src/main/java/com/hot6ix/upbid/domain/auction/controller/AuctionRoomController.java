package com.hot6ix.upbid.domain.auction.controller;

import com.hot6ix.upbid.domain.auction.api.AuctionRoomApi;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomCountsResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomShareResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomRole;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.service.AuctionParticipantService;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomCloseService;
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
    private final AuctionRoomCloseService auctionRoomCloseService;
    private final AuctionParticipantService auctionParticipantService;

    @PostMapping
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> create(
            Long userId, AuctionRoomCreateRequestDto request) {

        AuctionRoomPublicResponseDto response = auctionRoomService.create(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.ok(response, "경매방이 생성되었습니다."));
    }

    @GetMapping("/me")
    @Override
    public ResponseEntity<CommonResponse<CursorPageResponse<AuctionRoomListItemResponseDto>>> getMyRooms(
            Long userId, String keyword, AuctionRoomStatus status, AuctionRoomRole role,
            Long cursor, Integer size) {

        CursorPageResponse<AuctionRoomListItemResponseDto> response =
                auctionRoomService.getMyRooms(userId, keyword, status, role, cursor, size);

        return ResponseEntity.ok(CommonResponse.ok(response, "내 경매방 목록 조회에 성공했습니다."));
    }

    @GetMapping("/me/counts")
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomCountsResponseDto>> getMyRoomCounts(
            Long userId, String keyword, AuctionRoomRole role) {

        AuctionRoomCountsResponseDto response = auctionRoomService.getMyRoomCounts(userId, keyword, role);

        return ResponseEntity.ok(CommonResponse.ok(response, "내 경매방 상태별 개수 조회에 성공했습니다."));
    }

    @GetMapping("/share/{shareCode}/results")
    @GuestAllowed
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomResultResponseDto>> getResults(
            @PathVariable String shareCode, Long userId) {

        AuctionRoomResultResponseDto response = auctionRoomService.getResults(shareCode, userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매방 낙찰 결과 조회에 성공했습니다."));
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

    @PostMapping("/{roomId}/close")
    @Override
    public ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> close(
            Long userId, @PathVariable Long roomId) {

        AuctionRoomPublicResponseDto response = auctionRoomCloseService.close(userId, roomId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매방이 종료되었습니다."));
    }

    @PostMapping("/share/{shareCode}/agreement")
    @Override
    public ResponseEntity<CommonResponse<Void>> agree(Long userId, @PathVariable String shareCode) {
        auctionParticipantService.agree(shareCode, userId);

        return ResponseEntity.ok(CommonResponse.ok("약관 동의가 완료되었습니다."));
    }
}
