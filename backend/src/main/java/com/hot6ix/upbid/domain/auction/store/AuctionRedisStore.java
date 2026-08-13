package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.domain.user.entity.User;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 입찰 판정에 쓰는 Redis 값을 읽고 쓴다 (이슈 #246 의 비교군 C).
 *
 * <p>물품 Hash와 참여자 Set으로 판정하고, 승인 결과 Hash와 Stream을 함께 남긴다. 판정은
 * {@code lua/bid.lua}가 하고 이 클래스는 스크립트 호출과 반환 타입 변환만 담당한다.
 *
 * <pre>
 *   auction:item:{id}               HASH  status endAt currentPrice leaderUserId
 *                                         startingPrice bidIncrement sellerUserId
 *                                         Soft Close 설정과 누적 연장 시간
 *   auction:room:{id}:participants  SET   약관 동의를 마친 userId
 *   auction:item:{id}:accepted      HASH  requestId별 첫 승인 결과
 *   auction:bid:stream              STREAM MySQL에 반영할 승인 이벤트
 * </pre>
 *
 * <p><b>{@code endAt} 은 epoch millis 로 넣는다.</b> Lua 에서 문자열 시각을 비교할 수 없고,
 * 앱이 쓰는 {@code LocalDateTime} 에는 오프셋이 없어 그대로 넣으면 Redis 안에서 어느
 * 시간대인지 알 수 없다. 시스템 기본 시간대로 바꿔 넣는다 — 컨테이너에 {@code TZ=Asia/Seoul}
 * 이 박혀 있고 DB 에 든 값도 같은 기준이다.
 */
@Component
public class AuctionRedisStore {

    /** 임시 비교군 C 스크립트가 접수했다는 뜻. Task 8의 API 전환 때 제거한다. */
    public static final long ACCEPTED = 0L;

    /** 임시 비교군 C 스크립트에서 물품 Hash가 없다는 뜻. */
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

    private final StringRedisTemplate redis;
    private final RedisScript<Long> legacyBidScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> bidScript;
    private final RedisScript<Void> updateEndAtScript;
    private final RedisScript<Long> seedScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> closeScript;

    public AuctionRedisStore(StringRedisTemplate redis) {
        this.redis = redis;
        // 스크립트는 한 번 읽어 들고 있는다. Spring Data Redis 가 EVALSHA 로 부르고
        // NOSCRIPT 가 오면 알아서 EVAL 로 한 번 더 보낸다.
        this.legacyBidScript = new DefaultRedisScript<>(readScript("lua/bid-legacy.lua"), Long.class);
        this.bidScript = new DefaultRedisScript<>(readScript("lua/bid.lua"), List.class);
        this.updateEndAtScript = new DefaultRedisScript<>(UPDATE_END_AT, Void.class);
        this.seedScript = new DefaultRedisScript<>(readScript("lua/seed-auction.lua"), Long.class);
        this.closeScript = new DefaultRedisScript<>(readScript("lua/close-auction.lua"), List.class);
    }

    /**
     * 현재 동기 API를 위한 임시 비교군 C 판정 경로. 접수면 현재가와 최고 입찰자를 갱신한다.
     *
     * @param nowMillis 비교군의 판정 기준 시각
     * @return {@link #ACCEPTED}, {@link #KEY_MISSING}, 또는 거절 사유 코드
     */
    public long evaluateBid(long itemId, long bidderUserId, long amount, long nowMillis) {

        // 키를 하나만 넘긴다. 방 참여자 SET 은 스크립트가 HASH 의 roomId 로 만든다 —
        // 여기서 만들려면 roomId 를 알아야 하고, 그걸 알려면 DB 를 읽어야 한다.
        Long result = redis.execute(legacyBidScript,
                List.of(AuctionRedisKeys.item(itemId)),
                String.valueOf(bidderUserId), String.valueOf(amount), String.valueOf(nowMillis));

        // execute 가 null 을 주는 것은 스크립트가 값을 안 돌려줬다는 뜻이다. 스크립트는 모든
        // 경로에서 return 하므로 정상 경로에는 없다. 키 없음으로 취급하면 DB 에서 읽어 채우고
        // 다시 부르게 되어, 조용히 접수로 넘어가지 않는다.
        return result == null ? KEY_MISSING : result;
    }

    /** 입찰 판정부터 승인 이벤트 기록까지 새 Lua 계약으로 수행한다. */
    public RedisBidDecision evaluateBid(long itemId, String requestId, long bidderUserId,
                                        long amount, long arrivedAtMillis) {

        @SuppressWarnings("unchecked")
        List<String> result = redis.execute(
                bidScript,
                List.of(
                        AuctionRedisKeys.item(itemId),
                        AuctionRedisKeys.accepted(itemId),
                        AuctionRedisKeys.stream()),
                requestId,
                String.valueOf(bidderUserId),
                String.valueOf(amount),
                String.valueOf(arrivedAtMillis));

        return toDecision(result);
    }

    private static RedisBidDecision toDecision(List<String> result) {
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("bid.lua가 결과를 반환하지 않았다");
        }
        if ("REJECTED".equals(result.get(0))) {
            return new RedisBidDecision.Rejected(RedisBidDecision.Reason.valueOf(result.get(1)));
        }
        if (!"ACCEPTED".equals(result.get(0)) || result.size() != 6) {
            throw new IllegalStateException("bid.lua가 모르는 결과를 반환했다: " + result);
        }
        return new RedisBidDecision.Accepted(
                result.get(1),
                Long.parseLong(result.get(2)),
                Long.parseLong(result.get(3)),
                Integer.parseInt(result.get(4)),
                "1".equals(result.get(5)));
    }

    /**
     * 기존 비교군 C의 호출 계약을 유지하는 어댑터. 엔티티를 불변 seed 스냅샷으로 바꾼 뒤
     * {@link #seed(AuctionRedisSeed)}에 위임한다. 시작 후 initializer와 아직 남아 있는 첫 입찰
     * 복구 경로가 함께 사용하며, 어느 쪽이 먼저 와도 이미 공개된 Hash는 덮어쓰지 않는다.
     */
    public void seed(AuctionItem item, long roomId, long sellerUserId, Collection<Long> participantUserIds) {
        User leader = item.getLeaderUser();
        seed(new AuctionRedisSeed(
                item.getAuctionItemId(),
                roomId,
                sellerUserId,
                item.getStatus(),
                item.getStartingPrice(),
                item.getCurrentPrice(),
                leader == null ? null : leader.getUserId(),
                item.getBidIncrement(),
                toMillis(item.getEndAt()),
                item.getAuctionRoom().getSoftCloseTriggerSeconds(),
                item.getAuctionRoom().getSoftCloseExtendSeconds(),
                item.getTotalExtensionSeconds(),
                AuctionItem.MAX_TOTAL_EXTENSION_SECONDS,
                List.copyOf(participantUserIds)));
    }

    /**
     * 참여자 Set과 물품 Hash를 한 Lua 실행으로 준비한다.
     *
     * <p>Lua는 참여자 Set을 먼저 쓰고 물품 Hash를 마지막에 공개한다. 실행 전체가 원자적이므로
     * 다른 입찰 Lua는 Hash가 없거나, Set까지 완성된 Hash만 볼 수 있다. Hash가 이미 존재하면
     * Redis의 최신 입찰 상태를 과거 DB 스냅샷으로 되돌리지 않고 {@code false}를 반환한다.
     */
    public boolean seed(AuctionRedisSeed seed) {

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(seed.roomId()));
        args.add(String.valueOf(seed.sellerUserId()));
        args.add(seed.status().name());
        args.add(String.valueOf(seed.startingPrice()));
        args.add(String.valueOf(seed.currentPrice()));
        args.add(String.valueOf(seed.bidIncrement()));
        args.add(String.valueOf(seed.endAtMillis()));
        args.add(nullableNumber(seed.softCloseTriggerSeconds()));
        args.add(nullableNumber(seed.softCloseExtendSeconds()));
        args.add(String.valueOf(seed.totalExtensionSeconds()));
        args.add(String.valueOf(seed.maxTotalExtensionSeconds()));
        args.add(nullableNumber(seed.leaderUserId()));
        seed.participantUserIds().stream().map(String::valueOf).forEach(args::add);

        Long result = redis.execute(
                seedScript,
                List.of(AuctionRedisKeys.item(seed.itemId()), AuctionRedisKeys.participants(seed.roomId())),
                args.toArray());

        return Long.valueOf(1L).equals(result);
    }

    /** 약관 동의를 마친 회원을 방 참여자 SET 에 더한다. */
    public void addParticipant(long roomId, long userId) {
        redis.opsForSet().add(AuctionRedisKeys.participants(roomId), String.valueOf(userId));
    }

    /**
     * 마감 시각을 다시 쓴다. Soft Close 연장과 판매자 조기 마감이 {@code endAt} 을 바꾸는데,
     * 그 판정은 아직 DB 쪽에 있어서 바뀐 값을 여기로 옮겨야 한다.
     *
     * <p>키가 없으면 아무것도 안 한다. 없는 물품에 필드 하나만 있는 HASH 를 만들면 스크립트가
     * 그걸 "채워진 물품" 으로 읽어 나머지 필드를 못 찾는다.
     */
    public void updateEndAt(long itemId, LocalDateTime endAt) {
        redis.execute(updateEndAtScript, List.of(AuctionRedisKeys.item(itemId)), String.valueOf(toMillis(endAt)));
    }

    public RedisCloseDecision requestNaturalClose(long itemId, long nowMillis) {
        return executeClose(itemId, "NATURAL", String.valueOf(nowMillis));
    }

    public RedisCloseDecision requestSellerAdvance(long itemId, long sellerUserId) {
        return executeClose(itemId, "SELLER_ADVANCE", String.valueOf(sellerUserId));
    }

    private RedisCloseDecision executeClose(long itemId, String mode, String argument) {
        @SuppressWarnings("unchecked")
        List<String> result = redis.execute(closeScript,
                List.of(AuctionRedisKeys.item(itemId), AuctionRedisKeys.stream()), mode, argument);

        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("close-auction.lua가 결과를 반환하지 않았다");
        }
        return switch (result.getFirst()) {
            case "CLOSING" -> new RedisCloseDecision.Closing(Long.parseLong(result.get(1)));
            case "ADVANCED" -> new RedisCloseDecision.Advanced(
                    Long.parseLong(result.get(1)), Integer.parseInt(result.get(2)),
                    Long.parseLong(result.get(3)));
            case "REJECTED" -> new RedisCloseDecision.Rejected(
                    RedisCloseDecision.Reason.valueOf(result.get(1)),
                    result.size() < 3 || result.get(2).isBlank() ? null : Long.parseLong(result.get(2)));
            default -> throw new IllegalStateException("close-auction.lua가 모르는 결과를 반환했다: " + result);
        };
    }

    private static long toMillis(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String nullableNumber(Number value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String readScript(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes());
        } catch (Exception e) {
            throw new IllegalStateException(path + "를 읽지 못했다", e);
        }
    }
}
