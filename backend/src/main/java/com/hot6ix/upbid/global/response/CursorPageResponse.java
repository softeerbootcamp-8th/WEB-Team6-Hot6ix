package com.hot6ix.upbid.global.response;

import java.util.List;

public record CursorPageResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext,
        int size
) {
    public static <T> CursorPageResponse<T> of(List<T> content, String nextCursor) {
        return new CursorPageResponse<>(content, nextCursor, nextCursor != null, content.size());
    }
}
