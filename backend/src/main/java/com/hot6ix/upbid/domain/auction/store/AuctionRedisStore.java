package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.domain.bid.stream.BidStreamMetrics;
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
 * <p>물품 Hash와 참여자 Set으로 판정하고, 전역 요청 결과 Hash와 Stream을 함께 남긴다. 판정은
 * {@code lua/bid.lua}가 하고 이 클래스는 스크립트 호출과 반환 타입 변환만 담당한다.
 *
 * <pre>
 *   auction:item:{id}               HASH  status endAt currentPrice leaderUserId itemName
 *                                         startingPrice bidIncrement sellerUserId
 *                                         Soft Close 설정과 누적 연장 시간
 *   auction:room:{id}:participants  SET   약관 동의를 마친 userId
 *   auction:room:{id}:participant-nicknames HASH userId -> 화면 표시 nickname
 *   auction:bid:request:{requestId} HASH  전역 requestId별 fingerprint와 첫 승인 결과
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

    private final StringRedisTemplate redis;
    private final BidStreamMetrics metrics;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> bidScript;
    private final RedisScript<Long> seedScript;
    private final RedisScript<Long> addParticipantScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> closeScript;

    public AuctionRedisStore(StringRedisTemplate redis, BidStreamMetrics metrics) {
        this.redis = redis;
        this.metrics = metrics;
        // 스크립트는 한 번 읽어 들고 있는다. Spring Data Redis 가 EVALSHA 로 부르고
        // NOSCRIPT 가 오면 알아서 EVAL 로 한 번 더 보낸다.
        this.bidScript = new DefaultRedisScript<>(readScript("lua/bid.lua"), List.class);
        this.seedScript = new DefaultRedisScript<>(readScript("lua/seed-auction.lua"), Long.class);
        this.addParticipantScript = new DefaultRedisScript<>(
                readScript("lua/add-auction-participant.lua"), Long.class);
        this.closeScript = new DefaultRedisScript<>(readScript("lua/close-auction.lua"), List.class);
    }

    /** 입찰 판정부터 승인 이벤트 기록까지 새 Lua 계약으로 수행한다. */
    public RedisBidDecision evaluateBid(long itemId, String requestId, long bidderUserId,
                                        long amount, long arrivedAtMillis) {

        List<String> result;
        try {
            @SuppressWarnings("unchecked")
            List<String> executed = redis.execute(
                    bidScript,
                    List.of(
                            AuctionRedisKeys.item(itemId),
                            AuctionRedisKeys.bidRequest(requestId),
                            AuctionRedisKeys.stream()),
                    requestId,
                    String.valueOf(bidderUserId),
                    String.valueOf(amount),
                    String.valueOf(arrivedAtMillis));
            result = executed;
        } catch (RuntimeException e) {
            metrics.recordLuaFailure("execution");
            throw e;
        }

        RedisBidDecision decision;
        try {
            decision = toDecision(result);
        } catch (RuntimeException e) {
            metrics.recordLuaFailure("parsing");
            throw e;
        }
        switch (decision) {
            case RedisBidDecision.Accepted ignored -> metrics.recordLuaDecision("accepted");
            case RedisBidDecision.Rejected rejected -> metrics.recordLuaDecision(
                    "rejected_" + rejected.reason().name().toLowerCase(java.util.Locale.ROOT));
        }
        return decision;
    }

    private static RedisBidDecision toDecision(List<String> result) {
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("bid.lua가 결과를 반환하지 않았다");
        }
        if ("REJECTED".equals(result.get(0))) {
            return new RedisBidDecision.Rejected(RedisBidDecision.Reason.valueOf(result.get(1)));
        }
        if (!"ACCEPTED".equals(result.get(0)) || result.size() != 11) {
            throw new IllegalStateException("bid.lua가 모르는 결과를 반환했다: " + result);
        }
        return new RedisBidDecision.Accepted(
                result.get(1),
                Long.parseLong(result.get(2)),
                result.get(3),
                Long.parseLong(result.get(4)),
                result.get(5),
                Long.parseLong(result.get(6)),
                Long.parseLong(result.get(7)),
                Long.parseLong(result.get(8)),
                Integer.parseInt(result.get(9)),
                "1".equals(result.get(10)));
    }

    /**
     * 엔티티를 불변 seed 스냅샷으로 바꾼 뒤 {@link #seed(AuctionRedisSeed)}에 위임한다.
     * 이미 공개된 Hash는 과거 DB 스냅샷으로 덮어쓰지 않는다.
     */
    public void seed(AuctionItem item, long roomId, long sellerUserId,
                     Collection<AuctionRedisParticipant> participants) {
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
                item.getProduct().getName(),
                List.copyOf(participants)));
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
        args.add(seed.itemName());
        for (AuctionRedisParticipant participant : seed.participants()) {
            args.add(String.valueOf(participant.userId()));
            args.add(participant.nickname());
        }

        Long result;
        try {
            result = redis.execute(
                    seedScript,
                    List.of(
                            AuctionRedisKeys.item(seed.itemId()),
                            AuctionRedisKeys.participants(seed.roomId()),
                            AuctionRedisKeys.participantNicknames(seed.roomId())),
                    args.toArray());
        } catch (RuntimeException e) {
            metrics.recordSeedFailure();
            throw e;
        }

        return Long.valueOf(1L).equals(result);
    }

    /** 약관 동의를 마친 회원의 판정용 ID와 표시용 nickname을 한 Lua 실행으로 더한다. */
    public void addParticipant(long roomId, long userId, String nickname) {
        redis.execute(
                addParticipantScript,
                List.of(
                        AuctionRedisKeys.participants(roomId),
                        AuctionRedisKeys.participantNicknames(roomId)),
                String.valueOf(userId),
                nickname);
    }

    public RedisCloseDecision requestNaturalClose(long itemId, long nowMillis) {
        return executeClose(itemId, "NATURAL", String.valueOf(nowMillis));
    }

    /**
     * 판매자 마감 앞당기기를 Lua 한 번으로 판정한다.
     *
     * @param remainingSeconds 마감까지 남길 초. <b>{@code null}이면 경매방의 연장 트리거 초</b>다.
     *                         남길 시간을 Lua 에 넘겨 새 마감 시각을 Redis 시계로 계산하게 하는
     *                         것은, 서버마다 다를 수 있는 시계를 판정에서 빼기 위해서다
     */
    public RedisCloseDecision requestSellerAdvance(
            long itemId, long sellerUserId, Integer remainingSeconds) {

        return executeClose(itemId, "SELLER_ADVANCE", String.valueOf(sellerUserId),
                remainingSeconds == null ? "" : String.valueOf(remainingSeconds));
    }

    private RedisCloseDecision executeClose(long itemId, String mode, String... arguments) {
        Object[] args = new Object[arguments.length + 1];
        args[0] = mode;
        System.arraycopy(arguments, 0, args, 1, arguments.length);

        @SuppressWarnings("unchecked")
        List<String> result = redis.execute(closeScript,
                List.of(AuctionRedisKeys.item(itemId), AuctionRedisKeys.stream()), args);

        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("close-auction.lua가 결과를 반환하지 않았다");
        }
        return switch (result.getFirst()) {
            case "CLOSING" -> {
                if (result.size() != 9) {
                    throw new IllegalStateException("close-auction.lua CLOSING 결과가 잘못됐다: " + result);
                }
                yield new RedisCloseDecision.Closing(
                        Long.parseLong(result.get(1)),
                        Long.parseLong(result.get(2)),
                        result.get(3),
                        Long.parseLong(result.get(4)),
                        result.get(5).isBlank() ? null : Long.parseLong(result.get(5)),
                        result.get(6).isBlank() ? null : result.get(6),
                        Long.parseLong(result.get(7)),
                        Long.parseLong(result.get(8)));
            }
            case "ADVANCED" -> new RedisCloseDecision.Advanced(
                    Long.parseLong(result.get(1)),
                    Long.parseLong(result.get(2)),
                    result.get(3),
                    Long.parseLong(result.get(4)),
                    Integer.parseInt(result.get(5)),
                    Long.parseLong(result.get(6)));
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
