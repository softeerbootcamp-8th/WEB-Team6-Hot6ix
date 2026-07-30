package com.hot6ix.upbid.domain.deal.controller;

import com.hot6ix.upbid.domain.deal.api.DealCandidateApi;
import com.hot6ix.upbid.domain.deal.service.DealCandidateService;
import com.hot6ix.upbid.global.response.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auction-items/{auctionItemId}/deal-candidates")
@RequiredArgsConstructor
public class DealCandidateController implements DealCandidateApi {

    private final DealCandidateService dealCandidateService;

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
