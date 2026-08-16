package com.hot6ix.upbid.domain.sse.event;

import com.hot6ix.upbid.domain.sse.service.SseServerIdentifier;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 방별 참여자 수를 전역으로 집계해 발행한다.
 * 서버별 몫 갱신, 필드 TTL 갱신, 전체 합산, 채널 발행이 <b>스크립트 한 번</b>에 끝난다
 * ({@code redis/participant-count-publish.lua}).
 *
 * <p>{@code SseEventPublisher}와 달리 순차 ID를 발급하지 않고 재연결 replay 버퍼에도
 * 안 쌓는다. 참여자 수는 지난 값을 다시 볼 이유가 없는 "지금 몇 명" 신호라, 버퍼 자리를
 * 차지하면 정작 replay가 필요한 입찰·마감 이벤트가 먼저 밀려나기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipantCountPublisher {

    public static final String PARTICIPANT_COUNT_CHANNEL = "upbid:sse:participant-count:v1";

    /** heartbeat(기본 30초)를 한 번 놓쳐도 버티도록 2배 여유를 둔다. */
    private static final Duration FIELD_TTL = Duration.ofMinutes(1);

    /** 방이 비정상 종료되어 정리가 안 된 경우에도 키가 남지 않도록 발행마다 갱신한다. */
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> participantCountPublishScript;
    private final SseServerIdentifier serverIdentifier;

    /**
     * 이 서버의 로컬 참여자 수를 갱신하고, 전역 합계를 발행한다.
     *
     * <p>발행이 실패하면 그 갱신은 사라진다. 다음 heartbeat나 다른 연결/해제가 다시
     * 갱신을 트리거하므로 값은 결국 맞아지지만, 조용히 사라지면 아무도 모르니 로그로 남긴다.
     */
    public void publish(Long roomId, int localCount) {
        try {
            stringRedisTemplate.execute(
                    participantCountPublishScript,
                    List.of(SseRedisKeys.counts(roomId)),
                    serverIdentifier.id(),
                    String.valueOf(localCount),
                    String.valueOf(FIELD_TTL.toSeconds()),
                    PARTICIPANT_COUNT_CHANNEL,
                    String.valueOf(KEY_TTL.toSeconds()),
                    String.valueOf(roomId));

        } catch (RuntimeException e) {
            log.error("참여자 수 발행 실패: roomId={}", roomId, e);
        }
    }
}
