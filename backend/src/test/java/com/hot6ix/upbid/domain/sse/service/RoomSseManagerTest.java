package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    private final RoomSseManager roomSseManager = new RoomSseManager();

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
                        roomSseManager.subscribe(EVENT_NAME, ROOM_ID, "payload");
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
            roomSseManager.subscribe(EVENT_NAME, ROOM_ID, "payload");
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
                roomSseManager.subscribe(EVENT_NAME, ROOM_ID, "payload");
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

        roomSseManager.subscribe(EVENT_NAME, ROOM_ID, "payload");
        roomSseManager.subscribe(EVENT_NAME, ROOM_ID, "payload");
        SseEmitter dead = roomSseManager.subscribe(EVENT_NAME, ROOM_ID, "payload");

        // 연결은 끊겼지만 아직 Set 에서 제거되지 않은 상태를 만든다.
        // 이 emitter 에 쓰면 ResponseBodyEmitter 가 IllegalStateException 을 던진다.
        dead.complete();

        assertThatCode(() -> roomSseManager.sendBroadCast(EVENT_NAME, ROOM_ID, "payload"))
                .doesNotThrowAnyException();

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("구독하지 않은 방의 참여자 수는 0이다")
    void countsZeroForUnknownRoom() {

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isZero();
    }

    @Test
    @DisplayName("구독한 emitter 를 그대로 돌려준다")
    void returnsRegisteredEmitters() {

        List<SseEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            emitters.add(roomSseManager.subscribe(EVENT_NAME, ROOM_ID, "payload"));
        }

        assertThat(emitters).doesNotContainNull();
        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(3);
    }
}
