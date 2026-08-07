package com.hot6ix.upbid.domain.sse.dto;

import com.hot6ix.upbid.global.common.ServerTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

// 실제 리더보드 조회 로직이 나오기 전까지 subscribe() 초기 데이터로 사용하는 임시 DTO
public record LeaderboardDto(
        Long itemId,
        String itemName,
        Long currentPrice,
        OffsetDateTime endedTime,
        List<RankingDto> rankings
) {

    public record RankingDto(
            int rank,
            String bidderNickname,
            Long bidPrice
    ) {
    }

    public static LeaderboardDto dummy() {
        return new LeaderboardDto(
                0L,
                "한정판 조던 스니커즈",
                85_000L,
                ServerTime.toOffset(LocalDateTime.now().plusMinutes(12).plusSeconds(10)),
                List.of(
                        new RankingDto(1, "스니커홀릭", 85_000L),
                        new RankingDto(2, "조던매니아", 82_000L),
                        new RankingDto(3, "슈즈러버", 80_000L)
                )
        );
    }
}
