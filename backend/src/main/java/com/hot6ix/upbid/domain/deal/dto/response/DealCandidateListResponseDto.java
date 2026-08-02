package com.hot6ix.upbid.domain.deal.dto.response;

import com.hot6ix.upbid.domain.deal.entity.DealViewerRole;
import com.hot6ix.upbid.global.response.PageResponse;

/**
 * 후보 목록 응답.
 *
 * @param myRank 구매자의 순위. 요청한 페이지에 없을 수 있어 목록 바깥에 둔다 — 7위가 1페이지를
 *               보고 있어도 값이 나와야 화면이 자기 페이지로 이동할 수 있다.
 *               판매자는 {@code null}
 */
public record DealCandidateListResponseDto(
        DealViewerRole viewerRole,
        Integer myRank,
        PageResponse<DealCandidateResponseDto> candidates
) {
}
