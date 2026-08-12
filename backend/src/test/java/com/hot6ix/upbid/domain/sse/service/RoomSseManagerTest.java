package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RoomSseManagerTest {

    private static final Long ROOM_ID = 1L;
    private static final String EVENT_NAME = "TEST_EVENT";
    private static final String ROOM_CLOSED_EVENT = "ROOM_CLOSED";
    private static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";
    private static final long EMITTER_TIMEOUT_MS = 60 * 60 * 1000L;

    private final RoomSseManager roomSseManager = newRoomSseManager();

    /**
     * 지표는 이 테스트의 관심사가 아니라 아무 데도 안 내보내는 레지스트리를 준다.
     *
     * <p>{@code Runnable::run}으로 브로드캐스트를 호출 스레드에서 바로 실행시킨다 — 실제
     * {@code sseExecutor}를 쓰면 전송이 비동기로 미뤄져서, sendBroadCast·closeRoom 호출
     * 직후 상태를 확인하는 아래 테스트들이 타이밍에 따라 흔들린다.
     */
    private static RoomSseManager newRoomSseManager() {
        SseProperties props = new SseProperties(30_000L, EMITTER_TIMEOUT_MS, 50);
        return new RoomSseManager(props, new SseMetrics(new SimpleMeterRegistry()), new SseEventBuffer(props),
                Runnable::run);
    }

    @Test
    @DisplayName("여러 스레드가 동시에 구독해도 참여자 수가 구독 수와 일치한다")
    void countsEverySubscriberUnderConcurrentSubscribe() throws InterruptedException {

        int subscriberCount = 64;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(subscriberCount);

        try {
            for (int i = 0; i < subscriberCount; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        roomSseManager.subscribe(ROOM_ID, null);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(subscriberCount);
    }

    @Test
    @DisplayName("브로드캐스트 중 다른 스레드가 구독을 추가해도 예외가 발생하지 않는다")
    void broadcastSurvivesConcurrentSubscribe() throws InterruptedException {

        int initialSubscribers = 32;
        for (int i = 0; i < initialSubscribers; i++) {
            roomSseManager.subscribe(ROOM_ID, null);
        }

        AtomicReference<Throwable> broadcastFailure = new AtomicReference<>();

        Thread broadcaster = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    roomSseManager.sendBroadCast(EVENT_NAME, ROOM_ID, "payload");
                }
            } catch (Throwable t) {
                broadcastFailure.set(t);
            }
        });

        Thread subscriber = new Thread(() -> {
            for (int i = 0; i < 200; i++) {
                roomSseManager.subscribe(ROOM_ID, null);
            }
        });

        broadcaster.start();
        subscriber.start();
        broadcaster.join();
        subscriber.join();

        assertThat(broadcastFailure.get()).isNull();
        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(initialSubscribers + 200);
    }

    @Test
    @DisplayName("이미 완료된 emitter가 섞여 있어도 나머지 구독은 이벤트를 받는다")
    void isolatesFailureFromOtherSubscribers() {

        roomSseManager.subscribe(ROOM_ID, null);
        roomSseManager.subscribe(ROOM_ID, null);
        SseEmitter dead = roomSseManager.subscribe(ROOM_ID, null);

        // 연결은 끊겼지만 아직 Set 에서 제거되지 않은 상태를 만든다.
        // 이 emitter 에 쓰면 ResponseBodyEmitter 가 IllegalStateException 을 던진다.
        dead.complete();

        assertThatCode(() -> roomSseManager.sendBroadCast(EVENT_NAME, ROOM_ID, "payload"))
                .doesNotThrowAnyException();

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("heartbeat 가 응답하지 않는 구독을 걷어낸다")
    void sweepsDeadSubscriberOnHeartbeat() {

        roomSseManager.subscribe(ROOM_ID, null);
        SseEmitter dead = roomSseManager.subscribe(ROOM_ID, null);

        dead.complete();

        assertThatCode(roomSseManager::sendHeartbeat).doesNotThrowAnyException();

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("heartbeat 로 구독을 걷어내면 남은 구독에 참여자 수를 다시 알린다")
    void broadcastsCountAfterHeartbeatSweep() {

        RoomSseManager manager = spy(newRoomSseManager());

        manager.subscribe(ROOM_ID, null);
        SseEmitter dead = manager.subscribe(ROOM_ID, null);
        dead.complete();

        clearInvocations(manager);

        manager.sendHeartbeat();

        verify(manager).sendBroadCast(eq(PARTICIPANT_COUNT_EVENT), eq(ROOM_ID), any());
    }

    @Test
    @DisplayName("걷어낼 구독이 없으면 참여자 수를 다시 알리지 않는다")
    void doesNotBroadcastCountWhenNothingSwept() {

        RoomSseManager manager = spy(newRoomSseManager());

        manager.subscribe(ROOM_ID, null);

        clearInvocations(manager);

        manager.sendHeartbeat();

        verify(manager, never()).sendBroadCast(eq(PARTICIPANT_COUNT_EVENT), eq(ROOM_ID), any());
    }

    @Test
    @DisplayName("방을 닫으면 그 방의 연결이 모두 끊긴다")
    void closesEverySubscriberOnRoomClose() {

        SseEmitter first = roomSseManager.subscribe(ROOM_ID, null);
        SseEmitter second = roomSseManager.subscribe(ROOM_ID, null);

        roomSseManager.closeRoom(ROOM_CLOSED_EVENT, ROOM_ID, "payload");

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isZero();

        // Map 에서 지우기만 하면 연결은 살아 있어 emitter-timeout(1시간)까지 남고, 그사이
        // 프록시 idle timeout 에 끊기면 EventSource 가 다시 붙는다. complete() 까지 불러야
        // 실제로 닫힌다 — 닫힌 emitter 에 쓰면 IllegalStateException 이 난다.
        assertThatThrownBy(() -> first.send("payload")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> second.send("payload")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("방을 닫으면 이벤트 버퍼도 비워진다")
    void clearsBufferOnRoomClose() {

        SseProperties props = new SseProperties(30_000L, EMITTER_TIMEOUT_MS, 50);
        SseEventBuffer buffer = new SseEventBuffer(props);
        RoomSseManager manager =
                new RoomSseManager(props, new SseMetrics(new SimpleMeterRegistry()), buffer, Runnable::run);

        manager.subscribe(ROOM_ID, null);
        manager.sendBroadCast(EVENT_NAME, ROOM_ID, "payload");
        assertThat(buffer.getEventsAfter(ROOM_ID, 0L)).isNotEmpty();

        manager.closeRoom(ROOM_CLOSED_EVENT, ROOM_ID, "payload");

        assertThat(buffer.getEventsAfter(ROOM_ID, 0L))
                .as("끝난 방은 재연결 replay 대상이 아니므로 메모리를 붙잡고 있을 이유가 없다")
                .isEmpty();
    }

    @Test
    @DisplayName("닫은 방에는 브로드캐스트가 나가지 않는다")
    void doesNotBroadcastToClosedRoom() {

        SseProperties props = new SseProperties(30_000L, EMITTER_TIMEOUT_MS, 50);
        SseEventBuffer buffer = new SseEventBuffer(props);
        RoomSseManager manager =
                new RoomSseManager(props, new SseMetrics(new SimpleMeterRegistry()), buffer, Runnable::run);

        manager.subscribe(ROOM_ID, null);
        manager.closeRoom(ROOM_CLOSED_EVENT, ROOM_ID, "payload");

        manager.sendBroadCast(EVENT_NAME, ROOM_ID, "payload");

        assertThat(buffer.getEventsAfter(ROOM_ID, 0L)).isEmpty();
    }

    @Test
    @DisplayName("연결이 없는 방을 닫아도 예외가 발생하지 않는다")
    void closesRoomWithoutSubscribers() {

        assertThatCode(() -> roomSseManager.closeRoom(ROOM_CLOSED_EVENT, ROOM_ID, "payload"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("구독하지 않은 방의 참여자 수는 0이다")
    void countsZeroForUnknownRoom() {

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isZero();
    }

    @Test
    @DisplayName("마지막 구독이 빠지면 방 수 지표도 0으로 내려간다")
    void reportsZeroRoomsAfterLastSubscriberLeaves() {

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RoomSseManager manager = new RoomSseManager(
                new SseProperties(30_000L, EMITTER_TIMEOUT_MS, 50), new SseMetrics(registry),
                new SseEventBuffer(new SseProperties(30_000L, EMITTER_TIMEOUT_MS, 50)), Runnable::run);

        // 게이지는 @PostConstruct 에서 붙는다. 직접 생성한 객체에서는 안 불리므로 여기서 부른다.
        manager.bindMetrics();

        SseEmitter first = manager.subscribe(ROOM_ID, null);
        SseEmitter second = manager.subscribe(ROOM_ID, null);

        assertThat(rooms(registry)).isEqualTo(1);

        // 연결이 끊긴 상태를 만들고 heartbeat 로 걷어내게 한다.
        first.complete();
        second.complete();
        manager.sendHeartbeat();

        assertThat(manager.getParticipantCount(ROOM_ID)).isZero();
        assertThat(rooms(registry))
                .as("방을 안 지우면 한 번 오른 뒤 영영 안 내려온다")
                .isZero();
    }

    private static double rooms(SimpleMeterRegistry registry) {
        return registry.get("upbid.sse.rooms").gauge().value();
    }

    @Test
    @DisplayName("구독한 emitter 를 그대로 돌려준다")
    void returnsRegisteredEmitters() {

        List<SseEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            emitters.add(roomSseManager.subscribe(ROOM_ID, null));
        }

        assertThat(emitters).doesNotContainNull();
        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(3);
    }

    @Test
    @DisplayName("방을 닫을 때 실행기가 거부해도 emitter는 끊긴다")
    void closeRoomStillCompletesEmitterWhenExecutorRejects() {

        SseProperties props = new SseProperties(30_000L, EMITTER_TIMEOUT_MS, 50);
        RoomSseManager manager = new RoomSseManager(props, new SseMetrics(new SimpleMeterRegistry()),
                new SseEventBuffer(props), rejecting -> {
                    throw new RejectedExecutionException("test");
                });

        SseEmitter emitter = manager.subscribe(ROOM_ID, null);

        manager.closeRoom(ROOM_CLOSED_EVENT, ROOM_ID, "payload");

        // sseExecutor가 거부해서 ROOM_CLOSED 전송 자체는 못 나갔어도, emitter는 열어둔 채로
        // 두지 않고 끊는다 — 안 그러면 앱이 셧다운되는 중에도 연결이 계속 남아 있게 된다.
        assertThatThrownBy(() -> emitter.send("payload")).isInstanceOf(IllegalStateException.class);
    }
}
