package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RoomSseManagerTest {

    private static final Long ROOM_ID = 1L;
    private static final String EVENT_NAME = "TEST_EVENT";
    private static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";
    private static final long EMITTER_TIMEOUT_MS = 60 * 60 * 1000L;

    private final RoomSseManager roomSseManager = newRoomSseManager();

    /** 지표는 이 테스트의 관심사가 아니라 아무 데도 안 내보내는 레지스트리를 준다. */
    private static RoomSseManager newRoomSseManager() {
        return new RoomSseManager(
                new SseProperties(30_000L, EMITTER_TIMEOUT_MS),
                new SseMetrics(new SimpleMeterRegistry()));
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
                        roomSseManager.subscribe(ROOM_ID);
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
            roomSseManager.subscribe(ROOM_ID);
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
                roomSseManager.subscribe(ROOM_ID);
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

        roomSseManager.subscribe(ROOM_ID);
        roomSseManager.subscribe(ROOM_ID);
        SseEmitter dead = roomSseManager.subscribe(ROOM_ID);

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

        roomSseManager.subscribe(ROOM_ID);
        SseEmitter dead = roomSseManager.subscribe(ROOM_ID);

        dead.complete();

        assertThatCode(roomSseManager::sendHeartbeat).doesNotThrowAnyException();

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("heartbeat 로 구독을 걷어내면 남은 구독에 참여자 수를 다시 알린다")
    void broadcastsCountAfterHeartbeatSweep() {

        RoomSseManager manager = spy(newRoomSseManager());

        manager.subscribe(ROOM_ID);
        SseEmitter dead = manager.subscribe(ROOM_ID);
        dead.complete();

        clearInvocations(manager);

        manager.sendHeartbeat();

        verify(manager).sendBroadCast(eq(PARTICIPANT_COUNT_EVENT), eq(ROOM_ID), any());
    }

    @Test
    @DisplayName("걷어낼 구독이 없으면 참여자 수를 다시 알리지 않는다")
    void doesNotBroadcastCountWhenNothingSwept() {

        RoomSseManager manager = spy(newRoomSseManager());

        manager.subscribe(ROOM_ID);

        clearInvocations(manager);

        manager.sendHeartbeat();

        verify(manager, never()).sendBroadCast(eq(PARTICIPANT_COUNT_EVENT), eq(ROOM_ID), any());
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
                new SseProperties(30_000L, EMITTER_TIMEOUT_MS), new SseMetrics(registry));

        // 게이지는 @PostConstruct 에서 붙는다. 직접 생성한 객체에서는 안 불리므로 여기서 부른다.
        manager.bindMetrics();

        SseEmitter first = manager.subscribe(ROOM_ID);
        SseEmitter second = manager.subscribe(ROOM_ID);

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
            emitters.add(roomSseManager.subscribe(ROOM_ID));
        }

        assertThat(emitters).doesNotContainNull();
        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(3);
    }
}
