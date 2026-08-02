package com.hot6ix.upbid.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * offset 페이지네이션 응답. 목록은 {@link CursorPageResponse}가 기본이고, 이 형식은 전체
 * 개수와 임의 페이지 접근이 필요한 목록에만 쓴다 — 마감된 낙찰 후보처럼 크기가 정해져 있고
 * 순서가 고정된 목록이다.
 *
 * <p>{@code size}는 요청한 크기가 아니라 실제 반환된 개수다 — {@link CursorPageResponse}와 같다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(),
                page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
    }
}
