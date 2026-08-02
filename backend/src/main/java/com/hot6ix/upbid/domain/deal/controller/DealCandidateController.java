package com.hot6ix.upbid.domain.deal.controller;

import com.hot6ix.upbid.domain.deal.api.DealCandidateApi;
import com.hot6ix.upbid.domain.deal.dto.response.DealCandidateListResponseDto;
import com.hot6ix.upbid.domain.deal.service.DealCandidateService;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auction-items/{auctionItemId}/deal-candidates")
@RequiredArgsConstructor
public class DealCandidateController implements DealCandidateApi {

    private final DealCandidateService dealCandidateService;

    @GetMapping
    @Override
    public ResponseEntity<CommonResponse<DealCandidateListResponseDto>> getCandidates(
            Long auctionItemId, int page, Long userId) {

        DealCandidateListResponseDto response =
                dealCandidateService.getCandidates(auctionItemId, page, userId);

        return ResponseEntity.ok(CommonResponse.ok(response, "낙찰 후보 목록 조회에 성공했습니다."));
    }

    @PostMapping("/{candidateId}/fail")
    @Override
    public ResponseEntity<CommonResponse<Void>> fail(
            Long auctionItemId, Long candidateId, Long userId) {

        dealCandidateService.fail(auctionItemId, candidateId, userId);

        return ResponseEntity.ok(CommonResponse.ok("거래 실패를 처리했습니다."));
    }

    @PostMapping("/{candidateId}/complete")
    @Override
    public ResponseEntity<CommonResponse<Void>> complete(
            Long auctionItemId, Long candidateId, Long userId) {

        dealCandidateService.complete(auctionItemId, candidateId, userId);

        return ResponseEntity.ok(CommonResponse.ok("거래 성사를 확정했습니다."));
    }
}
