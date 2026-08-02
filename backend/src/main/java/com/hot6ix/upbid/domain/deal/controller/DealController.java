package com.hot6ix.upbid.domain.deal.controller;

import com.hot6ix.upbid.domain.deal.api.DealApi;
import com.hot6ix.upbid.domain.deal.dto.response.DealSummaryResponseDto;
import com.hot6ix.upbid.domain.deal.service.DealService;
import com.hot6ix.upbid.global.response.CommonResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
public class DealController implements DealApi {

    private final DealService dealService;

    @GetMapping
    @Override
    public ResponseEntity<CommonResponse<List<DealSummaryResponseDto>>> getDeals(Long userId) {

        List<DealSummaryResponseDto> response = dealService.getDeals(userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "거래 내역 조회에 성공했습니다."));
    }
}
