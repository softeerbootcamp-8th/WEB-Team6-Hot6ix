package com.hot6ix.upbid.global.response;

import java.util.List;

public record CursorPageResponse<T>(
        List<T> content,
        Long nextCursor,
        boolean hasNext,
        int size
) {
    public static <T> CursorPageResponse<T> of(List<T> content, Long nextCursor) {
        return new CursorPageResponse<>(content, nextCursor, nextCursor != null, content.size());
    }
}
