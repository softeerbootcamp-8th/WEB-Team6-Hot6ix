package com.hot6ix.upbid.domain.bid.controller;

import com.hot6ix.upbid.domain.bid.api.BidApi;
import com.hot6ix.upbid.domain.bid.dto.request.BidCreateRequestDto;
import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.domain.bid.service.BidService;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auction-items/{auctionItemId}/bids")
@RequiredArgsConstructor
public class BidController implements BidApi {

    private final BidService bidService;

    @PostMapping
    @Override
    public ResponseEntity<CommonResponse<BidCreateResponseDto>> place(
            Long auctionItemId, Long userId, String requestId, BidCreateRequestDto request) {

        BidCreateResponseDto response = bidService.place(
                auctionItemId, userId, request.amount(), requestId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.ok(response, "입찰이 접수되었습니다."));
    }
}
