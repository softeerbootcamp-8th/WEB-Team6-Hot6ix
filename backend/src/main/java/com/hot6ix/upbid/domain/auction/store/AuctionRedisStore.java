package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.domain.bid.stream.BidStreamMetrics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *                                         Soft Close 설정, 누적 연장 시간, revision
 *   auction:item:{id}:leaderboard   ZSET  bidderUserId -> 입찰자별 최고 금액
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
    private final RedisScript<Long> seedReadyScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> closeScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> readEndAtScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> readLiveStateScript;

    /**
     * {@link #findEndAtMillis(List)}가 한 번에 읽는 물품 수. Redis 는 스크립트가 도는 동안
     * 다른 명령을 받지 못하므로 {@code AuctionClosePoller}의 집기 상한과 같은 이유로 자른다.
     */
    private static final int END_AT_READ_CHUNK_SIZE = 200;

    public AuctionRedisStore(StringRedisTemplate redis, BidStreamMetrics metrics) {
        this.redis = redis;
        this.metrics = metrics;
        // 스크립트는 한 번 읽어 들고 있는다. Spring Data Redis 가 EVALSHA 로 부르고
        // NOSCRIPT 가 오면 알아서 EVAL 로 한 번 더 보낸다.
        this.bidScript = new DefaultRedisScript<>(readScript("lua/bid.lua"), List.class);
        this.seedScript = new DefaultRedisScript<>(readScript("lua/seed-auction.lua"), Long.class);
        this.addParticipantScript = new DefaultRedisScript<>(
                readScript("lua/add-auction-participant.lua"), Long.class);
        this.seedReadyScript = new DefaultRedisScript<>(readScript("lua/seed-ready.lua"), Long.class);
        this.closeScript = new DefaultRedisScript<>(readScript("lua/close-auction.lua"), List.class);
        this.readEndAtScript = new DefaultRedisScript<>(
                readScript("lua/read-auction-end-at.lua"), List.class);
        this.readLiveStateScript = new DefaultRedisScript<>(
                readScript("lua/read-auction-live-state.lua"), List.class);
    }

    /** 물품 Hash와 상위 3명 리더보드를 Lua 한 번으로 읽는다. */
    public Optional<AuctionRedisLiveState> findLiveState(long itemId) {
        @SuppressWarnings("unchecked")
        List<String> result = redis.execute(
                readLiveStateScript,
                List.of(AuctionRedisKeys.item(itemId), AuctionRedisKeys.leaderboard(itemId)),
                String.valueOf(itemId));

        if (result == null) {
            throw new IllegalStateException("read-auction-live-state.lua가 결과를 반환하지 않았다");
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }
        if (result.size() < 9) {
            throw new IllegalStateException("라이브 상태 결과 필드가 부족하다: " + result);
        }

        int leaderboardSize = Integer.parseInt(result.get(8));
        if (leaderboardSize < 0 || result.size() != 9 + leaderboardSize * 3) {
            throw new IllegalStateException("라이브 상태 리더보드 결과가 잘못됐다: " + result);
        }
        List<AuctionRedisLeaderboardEntry> leaderboard = new ArrayList<>(leaderboardSize);
        for (int index = 0; index < leaderboardSize; index++) {
            int offset = 9 + index * 3;
            leaderboard.add(new AuctionRedisLeaderboardEntry(
                    Long.parseLong(result.get(offset)),
                    result.get(offset + 1),
                    Long.parseLong(result.get(offset + 2))));
        }

        return Optional.of(new AuctionRedisLiveState(
                Long.parseLong(result.get(0)),
                Long.parseLong(result.get(1)),
                AuctionRedisLiveStatus.valueOf(result.get(2)),
                Long.parseLong(result.get(3)),
                result.get(4).isBlank() ? null : Long.parseLong(result.get(4)),
                Long.parseLong(result.get(5)),
                Integer.parseInt(result.get(6)),
                Long.parseLong(result.get(7)),
                leaderboard));
    }

    /**
     * 진행 중 물품의 Redis 마감 시각을 한 번에 읽는다. Hash 가 없거나 {@code endAt} 이 비어
     * 있으면 그 물품은 결과에 담기지 않는다.
     *
     * <p>마감 예약 재동기화가 <b>큐를 DB 가 아니라 Redis 에 맞추려고</b> 쓴다. 판매자가 마감을
     * 앞당기면 Redis 의 {@code endAt} 만 즉시 바뀌고 DB 는 Stream 이 반영한 뒤에야 따라오는데,
     * 그 사이 재동기화가 DB 값으로 큐를 덮으면 앞당김이 되돌아간다(#374).
     *
     * @param itemIds 읽을 물품 ID. 비어 있으면 Redis 를 부르지 않는다
     * @return 물품 ID 별 마감 시각 epoch millis
     */
    public Map<Long, Long> findEndAtMillis(List<Long> itemIds) {

        Map<Long, Long> endAtByItemId = new HashMap<>();

        for (int start = 0; start < itemIds.size(); start += END_AT_READ_CHUNK_SIZE) {
            List<Long> chunk =
                    itemIds.subList(start, Math.min(start + END_AT_READ_CHUNK_SIZE, itemIds.size()));
            readEndAtChunk(chunk, endAtByItemId);
        }

        return endAtByItemId;
    }

    private void readEndAtChunk(List<Long> itemIds, Map<Long, Long> target) {

        @SuppressWarnings("unchecked")
        List<String> result = redis.execute(
                readEndAtScript,
                itemIds.stream().map(AuctionRedisKeys::item).toList());

        if (result == null) {
            throw new IllegalStateException("read-auction-end-at.lua가 결과를 반환하지 않았다");
        }

        for (int index = 0; index < itemIds.size() && index < result.size(); index++) {
            String value = result.get(index);
            if (value != null && !value.isBlank()) {
                target.put(itemIds.get(index), Long.parseLong(value));
            }
        }
    }

    /** 입찰 판정부터 승인 이벤트 기록까지 새 Lua 계약으로 수행한다. */
    public RedisBidDecision evaluateBid(long itemId, String requestId, long bidderUserId,
                                        long amount) {

        List<String> result;
        try {
            @SuppressWarnings("unchecked")
            List<String> executed = redis.execute(
                    bidScript,
                    List.of(
                            AuctionRedisKeys.item(itemId),
                            AuctionRedisKeys.bidRequest(requestId),
                            AuctionRedisKeys.stream(),
                            AuctionRedisKeys.leaderboard(itemId)),
                    requestId,
                    String.valueOf(bidderUserId),
                    String.valueOf(amount));
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
        if (!"ACCEPTED".equals(result.get(0)) || result.size() != 12) {
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
                Long.parseLong(result.get(10)),
                "1".equals(result.get(11)));
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
        args.add(String.valueOf(seed.participants().size()));
        for (AuctionRedisParticipant participant : seed.participants()) {
            args.add(String.valueOf(participant.userId()));
            args.add(participant.nickname());
        }
        args.add(String.valueOf(seed.leaderboard().size()));
        for (AuctionRedisLeaderboardEntry entry : seed.leaderboard()) {
            args.add(String.valueOf(entry.bidderUserId()));
            args.add(String.valueOf(entry.amount()));
        }

        Long result;
        try {
            result = redis.execute(
                    seedScript,
                    List.of(
                            AuctionRedisKeys.item(seed.itemId()),
                            AuctionRedisKeys.participants(seed.roomId()),
                            AuctionRedisKeys.participantNicknames(seed.roomId()),
                            AuctionRedisKeys.leaderboard(seed.itemId())),
                    args.toArray());
        } catch (RuntimeException e) {
            metrics.recordSeedFailure();
            throw e;
        }

        return Long.valueOf(1L).equals(result);
    }

    /** 물품 Hash와 방 참여자 Set·nickname Hash가 모두 준비됐는지 값 전체를 읽지 않고 확인한다. */
    public boolean isSeedReady(long itemId, long roomId) {

        Long result;
        try {
            result = redis.execute(
                    seedReadyScript,
                    List.of(
                            AuctionRedisKeys.item(itemId),
                            AuctionRedisKeys.participants(roomId),
                            AuctionRedisKeys.participantNicknames(roomId),
                            AuctionRedisKeys.leaderboard(itemId)));
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

    public RedisCloseDecision requestNaturalClose(long itemId) {
        return executeClose(itemId, "NATURAL");
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
                if (result.size() != 10) {
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
                        Long.parseLong(result.get(8)),
                        Long.parseLong(result.get(9)));
            }
            case "ADVANCED" -> new RedisCloseDecision.Advanced(
                    Long.parseLong(result.get(1)),
                    Long.parseLong(result.get(2)),
                    result.get(3),
                    Long.parseLong(result.get(4)),
                    Integer.parseInt(result.get(5)),
                    Long.parseLong(result.get(6)),
                    Long.parseLong(result.get(7)));
            case "REJECTED" -> new RedisCloseDecision.Rejected(
                    RedisCloseDecision.Reason.valueOf(result.get(1)),
                    result.size() < 3 || result.get(2).isBlank() ? null : Long.parseLong(result.get(2)));
            default -> throw new IllegalStateException("close-auction.lua가 모르는 결과를 반환했다: " + result);
        };
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
