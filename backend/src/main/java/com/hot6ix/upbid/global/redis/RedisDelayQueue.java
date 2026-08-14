package com.hot6ix.upbid.global.redis;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * "언제 무엇을 처리할지"를 Redis Sorted Set 하나에 담는 지연 큐. member 가 대상 ID,
 * score 가 실행 시각(epoch millis)이다.
 *
 * <p><b>키 하나당 인스턴스 하나</b>다. 마감과 마감 임박 알림이 각자 키를 갖되 같은 코드를
 * 쓰도록 키를 생성자로 받는다.
 *
 * <p>여기는 "언제 깨울지"만 안다. <b>처리할지 말지는 부르는 쪽이 DB 를 보고 정한다.</b>
 * 그래서 이 큐가 날아가도 데이터가 깨지지 않고 DB 를 읽어 다시 채우면 된다.
 */
@RequiredArgsConstructor
public class RedisDelayQueue {

    /**
     * 지난 예약을 집어오면서 <b>지우지 않고 뒤로 미룬다.</b>
     *
     * <p>지우면 집어간 서버가 처리 도중 죽었을 때 큐에도 없고 DB 는 처리 전이라 아무도 그
     * 일을 하지 않는다. 미뤄 두면 그 시각에 다시 떠올라 누군가 집어간다.
     *
     * <p>스크립트로 묶는 것은 <b>두 서버가 같은 예약을 집지 못하게</b> 하려는 것이다. Redis 는
     * 스크립트가 도는 동안 다른 명령을 받지 않아서, 먼저 실행한 서버가 미뤄 두면 뒤이은 조회에
     * 그 예약이 안 잡힌다.
     *
     * <p>{@code GT}는 미루기가 시각을 앞으로 당기지 않게 막는다. 지금 구조에서는 걸릴 일이
     * 없고, {@code visibility}를 0 이하로 잘못 넣었을 때만 쓸모가 있다.
     */
    private static final String CLAIM_LUA = """
            local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'WITHSCORES', 'LIMIT', 0, ARGV[2])
            for i = 1, #due, 2 do
                redis.call('ZADD', KEYS[1], 'GT', ARGV[3], due[i])
            end
            return due
            """;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CLAIM_SCRIPT =
            new DefaultRedisScript<>(CLAIM_LUA, List.class);

    /**
     * 점수가 기대한 값 그대로일 때만 지운다. 읽기와 삭제를 스크립트로 묶는 것은 그 사이에
     * 다른 서버가 시각을 바꾸면 <b>이미 낡은 판단으로 지우게 되기</b> 때문이다.
     */
    private static final String CANCEL_IF_DEFERRED_LUA = """
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if score and tonumber(score) == tonumber(ARGV[2]) then
                return redis.call('ZREM', KEYS[1], ARGV[1])
            end
            return 0
            """;

    private static final RedisScript<Long> CANCEL_IF_DEFERRED_SCRIPT =
            new DefaultRedisScript<>(CANCEL_IF_DEFERRED_LUA, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String key;

    /**
     * 예약을 넣는다. <b>이미 있으면 시각만 바뀐다.</b> 그래서 "취소하고 다시 걸기"를 따로 하지
     * 않아도 되고, 어느 서버에서 부르든 결과가 같다.
     */
    public void schedule(Long id, LocalDateTime runAt) {
        redisTemplate.opsForZSet().add(key, String.valueOf(id), toScore(runAt));
    }

    /**
     * <b>예약이 없을 때만</b> 넣는다. DB 를 보고 큐를 다시 채우는 재동기화가 쓴다.
     *
     * <p>덮어쓰면 안 되는 것은 이미 있는 줄이 더 최신이기 때문이다. 연장이 반영돼 있거나
     * 누가 집어서 미뤄 둔 시각이 들어 있는데, DB 의 원래 시각으로 덮으면 그 둘을 되돌린다.
     *
     * @return 실제로 넣었으면 {@code true}, 이미 있어서 두었으면 {@code false}
     */
    public boolean scheduleIfAbsent(Long id, LocalDateTime runAt) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForZSet().addIfAbsent(key, String.valueOf(id), toScore(runAt)));
    }

    /** 예약을 지운다. 없으면 아무 일도 하지 않는다. */
    public void cancel(Long id) {
        redisTemplate.opsForZSet().remove(key, String.valueOf(id));
    }

    /**
     * <b>집으면서 미뤄 둔 시각 그대로일 때만</b> 예약을 지운다. 처리하는 사이에 누가 시각을
     * 바꿔 놨으면 그대로 둔다.
     *
     * <p>{@link #cancel}로 무조건 지우면 <b>처리하는 동안 새로 걸린 예약까지 지운다.</b> 마감은
     * 처리하고 나면 물품이 닫혀서 새 예약이 들어올 수 없지만, 마감 임박 알림은 발행한 뒤에도
     * 물품이 그대로 살아 있어서 곧바로 연장이 들어올 수 있다. 하필 알림이 나간 직후가 입찰이
     * 가장 몰리는 구간이라 실제로 겹친다(#290).
     *
     * <p>점수를 문자열이 아니라 <b>수로 견준다.</b> Redis 가 큰 값을 지수 표기로 돌려줄 수 있어
     * 문자열로 비교하면 같은 값도 다르게 보인다.
     *
     * @param claimedAt  {@link #claimDue}에 넘겼던 시각
     * @param visibility {@link #claimDue}에 넘겼던 유예. 미뤄 둔 시각을 <b>집을 때와 똑같은
     *                   식으로</b> 다시 구하려고 받는다
     * @return 지웠으면 {@code true}, 시각이 바뀌어 두었으면 {@code false}
     */
    public boolean cancelIfDeferred(Long id, LocalDateTime claimedAt, Duration visibility) {

        Long removed = redisTemplate.execute(
                CANCEL_IF_DEFERRED_SCRIPT,
                List.of(key),
                String.valueOf(id),
                String.valueOf(toMillis(claimedAt) + visibility.toMillis()));

        return removed != null && removed > 0;
    }

    /**
     * 실행 시각이 지난 예약을 집어오고, 집은 것은 {@code visibility}만큼 뒤로 미룬다.
     * <b>처리를 끝낸 쪽이 {@link #cancel}로 지워야 한다.</b> 안 지우면 미룬 시각에 다시 떠올라
     * 한 번 더 처리된다.
     *
     * @param visibility 집은 예약을 얼마나 미뤄 둘지. 처리에 걸리는 시간보다 넉넉해야 한다
     * @return 집은 예약. 없으면 빈 목록
     */
    public List<DueEntry> claimDue(LocalDateTime now, int limit, Duration visibility) {

        long nowMillis = toMillis(now);

        @SuppressWarnings("unchecked")
        List<String> claimed = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(key),
                String.valueOf(nowMillis),
                String.valueOf(limit),
                String.valueOf(nowMillis + visibility.toMillis()));

        return toEntries(claimed);
    }

    /**
     * 실행 시각이 지났는데 아직 처리되지 않은 예약 수.
     *
     * <p>이 값이 0 에서 안 내려오면 처리가 못 따라가고 있다는 뜻이다. 실행 지연에는 폴링
     * 주기가 바닥으로 깔려서 밀리는 것을 알기 어렵기 때문에 이쪽을 지표로 본다.
     */
    public long backlogSize(LocalDateTime now) {

        Long count = redisTemplate.opsForZSet()
                .count(key, Double.NEGATIVE_INFINITY, toScore(now));

        return count == null ? 0 : count;
    }

    /**
     * Lua 가 돌려준 {@code [member, score, member, score, ...]}를 둘씩 묶는다.
     * score 를 실수로 파싱하는 것은 Redis 가 점수를 실수 문자열로 돌려주기 때문이다.
     */
    private List<DueEntry> toEntries(List<String> claimed) {

        if (claimed == null || claimed.isEmpty()) {
            return List.of();
        }

        List<DueEntry> entries = new ArrayList<>(claimed.size() / 2);

        for (int i = 0; i + 1 < claimed.size(); i += 2) {
            long scheduledAtMillis = (long) Double.parseDouble(claimed.get(i + 1));
            entries.add(new DueEntry(Long.valueOf(claimed.get(i)), toLocalDateTime(scheduledAtMillis)));
        }

        return entries;
    }

    private double toScore(LocalDateTime time) {
        return toMillis(time);
    }

    private long toMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
