package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.user.entity.User;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 입찰 판정에 쓰는 Redis 값을 읽고 쓴다 (이슈 #246 의 비교군 C).
 *
 * <p>물품 HASH 하나와 방 참여자 SET 하나를 둔다. 판정은 {@code lua/bid.lua} 가 하고 이
 * 클래스는 그 스크립트를 부르는 일과 값을 채우는 일만 한다. 필드 이름과 반환값의 뜻은
 * 스크립트 주석에 있다.
 *
 * <pre>
 *   auction:item:{id}               HASH  endAt currentPrice leaderUserId
 *                                         startingPrice bidIncrement sellerUserId
 *   auction:room:{id}:participants  SET   약관 동의를 마친 userId
 * </pre>
 *
 * <p><b>{@code endAt} 은 epoch millis 로 넣는다.</b> Lua 에서 문자열 시각을 비교할 수 없고,
 * 앱이 쓰는 {@code LocalDateTime} 에는 오프셋이 없어 그대로 넣으면 Redis 안에서 어느
 * 시간대인지 알 수 없다. 시스템 기본 시간대로 바꿔 넣는다 — 컨테이너에 {@code TZ=Asia/Seoul}
 * 이 박혀 있고 DB 에 든 값도 같은 기준이다.
 */
@Component
public class AuctionRedisStore {

    /** {@code lua/bid.lua} 가 접수했다는 뜻. 나머지 반환값은 스크립트 주석 참고. */
    public static final long ACCEPTED = 0L;

    /** 물품 HASH 가 없다는 뜻. 부르는 쪽이 DB 에서 읽어 채우고 다시 부른다. */
    public static final long KEY_MISSING = -1L;

    /**
     * 물품 HASH 가 이미 있을 때만 {@code endAt} 을 바꾼다.
     *
     * <p>{@code HSET} 만 쓰면 없는 물품에 {@code endAt} 하나뿐인 HASH 가 생기고, 스크립트가
     * 그걸 "채워진 물품" 으로 읽어 나머지 필드를 못 찾는다. {@code EXISTS} 로 보고 나서
     * {@code HSET} 하는 것을 두 번에 나눠 보내면 그 사이에 키가 사라질 수 있어 한 번에 보낸다.
     * Redis 에는 "있을 때만 필드를 쓴다" 는 명령이 없다.
     */
    private static final String UPDATE_END_AT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('HSET', KEYS[1], 'endAt', ARGV[1])
            end
            """;

    /**
     * 물품 HASH 가 <b>없을 때만</b> 채운다.
     *
     * <p>그냥 {@code HSET} 으로 덮으면 측정 시작 순간처럼 여러 요청이 한꺼번에 키 없음을
     * 받았을 때 값이 되돌아간다. 한 요청이 DB 를 읽는 사이 다른 요청이 채우고 입찰까지
     * 접수하면, 늦게 끝난 쪽이 그 위에 옛 {@code currentPrice} 를 덮어쓴다. 그러면 이미
     * 접수된 금액이 다시 최소 금액이 되어 같은 금액이 두 번 접수되고,
     * {@code (auction_item_id, amount)} unique 위반이 난다.
     */
    private static final String SEED_IF_ABSENT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('HSET', KEYS[1], unpack(ARGV))
            end
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> bidScript;
    private final RedisScript<Void> updateEndAtScript;
    private final RedisScript<Void> seedScript;

    public AuctionRedisStore(StringRedisTemplate redis) {
        this.redis = redis;
        // 스크립트는 한 번 읽어 들고 있는다. Spring Data Redis 가 EVALSHA 로 부르고
        // NOSCRIPT 가 오면 알아서 EVAL 로 한 번 더 보낸다.
        this.bidScript = new DefaultRedisScript<>(readScript(), Long.class);
        this.updateEndAtScript = new DefaultRedisScript<>(UPDATE_END_AT, Void.class);
        this.seedScript = new DefaultRedisScript<>(SEED_IF_ABSENT, Void.class);
    }

    /**
     * 입찰을 판정하고, 접수면 {@code currentPrice} 와 {@code leaderUserId} 를 갱신한다.
     *
     * @param nowMillis 판정 기준 시각. 앱의 {@code Clock} 으로 만든 값을 넘긴다 —
     *                  스크립트 안에서 {@code TIME} 을 부르면 앱 시계와 갈린다
     * @return {@link #ACCEPTED}, {@link #KEY_MISSING}, 또는 거절 사유 코드
     */
    public long evaluateBid(long itemId, long bidderUserId, long amount, long nowMillis) {

        // 키를 하나만 넘긴다. 방 참여자 SET 은 스크립트가 HASH 의 roomId 로 만든다 —
        // 여기서 만들려면 roomId 를 알아야 하고, 그걸 알려면 DB 를 읽어야 한다.
        Long result = redis.execute(bidScript,
                List.of(itemKey(itemId)),
                String.valueOf(bidderUserId), String.valueOf(amount), String.valueOf(nowMillis));

        // execute 가 null 을 주는 것은 스크립트가 값을 안 돌려줬다는 뜻이다. 스크립트는 모든
        // 경로에서 return 하므로 정상 경로에는 없다. 키 없음으로 취급하면 DB 에서 읽어 채우고
        // 다시 부르게 되어, 조용히 접수로 넘어가지 않는다.
        return result == null ? KEY_MISSING : result;
    }

    /**
     * DB 에서 읽은 물품과 참여자로 두 키를 채운다. 물품이 시작될 때가 아니라 <b>첫 입찰이
     * 왔을 때</b> 불린다. 시작 시점에 채우면 채우는 자리가 하나 늘고, 그 자리가 실패했을 때
     * 어차피 이 복구가 필요하다.
     *
     * <p>참여자는 {@code SADD} 로 더한다. 지우고 다시 넣으면, 이 메서드가 DB 를 읽은 뒤
     * 커밋된 동의가 사라진다.
     */
    public void seed(AuctionItem item, long roomId, long sellerUserId, Collection<Long> participantUserIds) {

        Map<String, String> fields = new HashMap<>();
        fields.put("endAt", String.valueOf(toMillis(item.getEndAt())));
        fields.put("currentPrice", String.valueOf(item.getCurrentPrice()));
        fields.put("startingPrice", String.valueOf(item.getStartingPrice()));
        fields.put("bidIncrement", String.valueOf(item.getBidIncrement()));
        fields.put("sellerUserId", String.valueOf(sellerUserId));
        fields.put("roomId", String.valueOf(roomId));

        // 입찰이 아직 없으면 필드를 안 넣는다. 스크립트가 HGET 의 false 로 "첫 입찰" 을
        // 판정해서 최소 금액을 시작가로 잡는다.
        User leader = item.getLeaderUser();
        if (leader != null) {
            fields.put("leaderUserId", String.valueOf(leader.getUserId()));
        }

        List<String> args = new ArrayList<>();
        fields.forEach((field, value) -> {
            args.add(field);
            args.add(value);
        });
        redis.execute(seedScript, List.of(itemKey(item.getAuctionItemId())), args.toArray());

        // 참여자는 SADD 라 늦게 끝나도 덮어쓰지 않는다. HASH 와 달리 되돌아갈 값이 없다.
        if (!participantUserIds.isEmpty()) {
            redis.opsForSet().add(participantsKey(roomId),
                    participantUserIds.stream().map(String::valueOf).toArray(String[]::new));
        }
    }

    /** 약관 동의를 마친 회원을 방 참여자 SET 에 더한다. */
    public void addParticipant(long roomId, long userId) {
        redis.opsForSet().add(participantsKey(roomId), String.valueOf(userId));
    }

    /**
     * 마감 시각을 다시 쓴다. Soft Close 연장과 판매자 조기 마감이 {@code endAt} 을 바꾸는데,
     * 그 판정은 아직 DB 쪽에 있어서 바뀐 값을 여기로 옮겨야 한다.
     *
     * <p>키가 없으면 아무것도 안 한다. 없는 물품에 필드 하나만 있는 HASH 를 만들면 스크립트가
     * 그걸 "채워진 물품" 으로 읽어 나머지 필드를 못 찾는다.
     */
    public void updateEndAt(long itemId, LocalDateTime endAt) {
        redis.execute(updateEndAtScript, List.of(itemKey(itemId)), String.valueOf(toMillis(endAt)));
    }

    private static String itemKey(long itemId) {
        return "auction:item:" + itemId;
    }

    private static String participantsKey(long roomId) {
        return "auction:room:" + roomId + ":participants";
    }

    private static long toMillis(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String readScript() {
        try (var in = new ClassPathResource("lua/bid.lua").getInputStream()) {
            return new String(in.readAllBytes());
        } catch (Exception e) {
            throw new IllegalStateException("lua/bid.lua 를 읽지 못했다", e);
        }
    }
}
