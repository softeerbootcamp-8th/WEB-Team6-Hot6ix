package com.hot6ix.upbid.global.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 서버가 만든 {@code LocalDateTime}을 응답용 오프셋 값으로 바꾼다.
 */
public final class ServerTime {

    private ServerTime() {
    }

    public static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
