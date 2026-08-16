package com.hot6ix.upbid.domain.auction.controller;

import com.hot6ix.upbid.domain.auction.api.AuctionItemApi;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemBulkAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemCloseEarlyRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemBulkAddResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemCloseEarlyResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import com.hot6ix.upbid.domain.auction.service.AuctionItemService;
import com.hot6ix.upbid.global.interceptor.GuestAllowed;
import com.hot6ix.upbid.global.response.CommonResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuctionItemController implements AuctionItemApi {

    private final AuctionItemService auctionItemService;
    private final AuctionItemCloseService auctionItemCloseService;

    @GetMapping("/auction-rooms/share/{shareCode}/auction-items")
    @GuestAllowed
    @Override
    public ResponseEntity<CommonResponse<List<AuctionItemSummaryResponseDto>>> getSummaries(
            String shareCode, Long userId) {

        List<AuctionItemSummaryResponseDto> response = auctionItemService.getSummaries(shareCode, userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매 물품 목록 조회에 성공했습니다."));
    }

    @GetMapping("/auction-rooms/share/{shareCode}/auction-items/{auctionItemId}")
    @GuestAllowed
    @Override
    public ResponseEntity<CommonResponse<AuctionItemDetailResponseDto>> getDetail(
            String shareCode, Long auctionItemId, Long userId) {

        AuctionItemDetailResponseDto response =
                auctionItemService.getDetail(shareCode, auctionItemId, userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "경매 물품 상세 조회에 성공했습니다."));
    }

    @PostMapping("/auction-rooms/{auctionRoomId}/auction-items")
    @Override
    public ResponseEntity<CommonResponse<AuctionItemDetailResponseDto>> add(
            Long userId, Long auctionRoomId, AuctionItemAddRequestDto request) {

        AuctionItemDetailResponseDto response = auctionItemService.add(userId, auctionRoomId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok(response, "경매방에 물품이 추가되었습니다."));
    }

    @PostMapping("/auction-rooms/{auctionRoomId}/auction-items/bulk")
    @Override
    public ResponseEntity<CommonResponse<AuctionItemBulkAddResponseDto>> addAll(
            Long userId, Long auctionRoomId, AuctionItemBulkAddRequestDto request) {

        AuctionItemBulkAddResponseDto response = auctionItemService.addAll(userId, auctionRoomId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok(response, "경매방 물품 추가를 처리했습니다."));
    }

    @DeleteMapping("/auction-rooms/{auctionRoomId}/auction-items/{auctionItemId}")
    @Override
    public ResponseEntity<CommonResponse<Void>> remove(
            Long userId, Long auctionRoomId, Long auctionItemId) {

        auctionItemService.remove(userId, auctionRoomId, auctionItemId);

        return ResponseEntity.ok(CommonResponse.ok("경매방에서 물품이 제외되었습니다."));
    }

    @PostMapping("/auction-items/{auctionItemId}/start")
    @Override
    public ResponseEntity<CommonResponse<AuctionItemDetailResponseDto>> start(
            Long auctionItemId, Long userId, AuctionItemStartRequestDto request) {

        AuctionItemDetailResponseDto response = auctionItemService.start(auctionItemId, userId, request);

        return ResponseEntity.ok(CommonResponse.ok(response, "물품 경매가 시작되었습니다."));
    }

    @PostMapping("/auction-items/{auctionItemId}/close-early")
    @Override
    public ResponseEntity<CommonResponse<AuctionItemCloseEarlyResponseDto>> closeEarly(
            Long auctionItemId, Long userId, AuctionItemCloseEarlyRequestDto request) {

        AuctionItemCloseEarlyResponseDto response =
                auctionItemCloseService.closeEarly(userId, auctionItemId, request);

        return ResponseEntity.ok(CommonResponse.ok(response, "물품 마감이 앞당겨졌습니다."));
    }
}
