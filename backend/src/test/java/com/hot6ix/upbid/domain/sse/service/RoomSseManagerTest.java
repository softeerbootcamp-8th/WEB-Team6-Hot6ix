package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.event.ParticipantCountPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RoomSseManagerTest {

    private static final Long ROOM_ID = 1L;
    private static final String EVENT_NAME = "TEST_EVENT";
    private static final long EMITTER_TIMEOUT_MS = 60 * 60 * 1000L;
    private static final int QUEUE_CAPACITY = 128;

    private static final SseProperties PROPS =
            new SseProperties(30_000L, EMITTER_TIMEOUT_MS, 50, "local", QUEUE_CAPACITY);

    private final RoomSseManager roomSseManager = newRoomSseManager();

    /**
     * 지표는 이 테스트의 관심사가 아니라 아무 데도 안 내보내는 레지스트리를 준다.
     *
     * <p>발행은 mock 이다. 이 클래스가 보는 것은 <b>이 인스턴스에 붙은 연결</b>을 어떻게
     * 다루는지이고, Redis 왕복은 그 관심사가 아니다.
     */
    private static RoomSseManager newRoomSseManager() {
        return newRoomSseManager(mock(SseEventBuffer.class), mock(ParticipantCountPublisher.class));
    }

    private static RoomSseManager newRoomSseManager(SseEventBuffer buffer, ParticipantCountPublisher publisher) {
        return newRoomSseManager(buffer, publisher, new SimpleMeterRegistry());
    }

    /**
     * dispatcher 를 돌리는 executor 로 {@code Runnable::run} 을 준다. 운영에서는 고정 풀이
     * 비동기로 전송하지만, 이 테스트가 보는 것은 <b>등록·해제·팬아웃 대상 선정</b>이지
     * 전송 시점이 아니다. 같은 스레드에서 즉시 실행시켜 {@code deliverLocal} 이 돌아온 뒤
     * 바로 단언할 수 있게 한다.
     *
     * <p>emitter 는 {@link FakeMvcEmitter}로 갈아끼운다. 이유는 그 클래스 주석 참고.
     */
    private static RoomSseManager newRoomSseManager(
            SseEventBuffer buffer, ParticipantCountPublisher publisher, SimpleMeterRegistry registry) {
        return new RoomSseManager(PROPS, new SseMetrics(registry), buffer, publisher, Runnable::run) {
            @Override
            SseEmitter createEmitter() {
                return new FakeMvcEmitter();
            }
        };
    }

    /**
     * Spring MVC 의 async handler 역할을 대신하는 emitter.
     *
     * <p>{@code ResponseBodyEmitter}는 MVC 가 {@code initialize(handler)}를 불러줘야
     * {@code onCompletion}/{@code onError} 콜백을 연결한다. 유닛 테스트에서 만든 맨 emitter 는
     * handler 가 없어서 {@code complete()}를 불러도 <b>콜백이 발화하지 않는다.</b>
     * {@code RoomSseManager}의 정리가 전부 그 콜백을 타므로, 이 대역 없이는
     * "죽은 연결이 걷어지는가"를 확인할 수 없다.
     *
     * <p>{@link #killClient()}는 <b>클라이언트 소켓은 죽었는데 서버는 아직 모르는</b> 상태를
     * 만든다. 실제로 연결이 끊기는 방식이 이렇다 — 다음 전송을 시도해야 비로소 드러난다.
     * {@code complete()}를 대신 부르면 그 즉시 정리돼서, heartbeat 가 걷어내는지를 못 본다.
     */
    /** 클라이언트 소켓만 죽인다. 서버는 다음 전송을 시도해야 안다. */
    private static void killClient(SseEmitter emitter) {
        ((FakeMvcEmitter) emitter).killClient();
    }

    private static class FakeMvcEmitter extends SseEmitter {

        private Runnable completionCallback;
        private Consumer<Throwable> errorCallback;
        private boolean completed;
        private boolean clientGone;

        void killClient() {
            this.clientGone = true;
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completionCallback = callback;
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            this.errorCallback = callback;
        }

        @Override
        public void complete() {
            finish(null);
        }

        @Override
        public void completeWithError(Throwable ex) {
            finish(ex);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            requireWritable();
        }

        @Override
        public void send(Object object) throws IOException {
            requireWritable();
        }

        /**
         * 콜백은 lock 밖에서 부른다. 콜백이 {@code disconnect → unregister} 로 이어지며
         * 이 emitter 를 다시 건드리기 때문이다.
         */
        private void finish(Throwable ex) {
            Runnable completion;
            Consumer<Throwable> error;

            synchronized (this) {
                if (completed) {
                    // 실제 ResponseBodyEmitter 와 같은 반응. closeRoom 이 이걸 잡아서 삼킨다.
                    throw new IllegalStateException("ResponseBodyEmitter has already completed");
                }
                completed = true;
                completion = completionCallback;
                error = errorCallback;
            }

            if (ex != null) {
                if (error != null) {
                    error.accept(ex);
                }
                return;
            }
            if (completion != null) {
                completion.run();
            }
        }

        private synchronized void requireWritable() {
            if (completed || clientGone) {
                throw new IllegalStateException("ResponseBodyEmitter has already completed");
            }
        }
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
                    roomSseManager.deliverLocal(ROOM_ID, EVENT_NAME, i + 1, "payload");
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
        killClient(dead);

        assertThatCode(() -> roomSseManager.deliverLocal(ROOM_ID, EVENT_NAME, 1L, "payload"))
                .doesNotThrowAnyException();

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("heartbeat 가 응답하지 않는 구독을 걷어낸다")
    void sweepsDeadSubscriberOnHeartbeat() {

        roomSseManager.subscribe(ROOM_ID, null);
        SseEmitter dead = roomSseManager.subscribe(ROOM_ID, null);

        killClient(dead);

        assertThatCode(roomSseManager::sendHeartbeat).doesNotThrowAnyException();

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("구독하면 참여자 수를 즉시 증가시켜 발행한다")
    void publishesParticipantCountOnSubscribe() {

        ParticipantCountPublisher publisher = mock(ParticipantCountPublisher.class);
        RoomSseManager manager = newRoomSseManager(mock(SseEventBuffer.class), publisher);

        manager.subscribe(ROOM_ID, null);

        verify(publisher).increment(ROOM_ID, 1);
    }

    @Test
    @DisplayName("연결이 끊기면 heartbeat 를 기다리지 않고 즉시 참여자 수를 감소시켜 발행한다")
    void publishesParticipantCountImmediatelyOnDisconnect() {

        ParticipantCountPublisher publisher = mock(ParticipantCountPublisher.class);
        RoomSseManager manager = newRoomSseManager(mock(SseEventBuffer.class), publisher);
        SseEmitter dead = manager.subscribe(ROOM_ID, null);
        killClient(dead);

        clearInvocations(publisher);

        // 끊긴 연결은 write 를 시도할 때만 드러난다 — 실제로 보내봐야 disconnect() 가 불린다.
        manager.deliverLocal(ROOM_ID, EVENT_NAME, 1L, "payload");

        verify(publisher, times(1)).increment(ROOM_ID, -1);
    }

    @Test
    @DisplayName("참여자 수는 id 없이 보내도 실패하지 않고, 다른 이벤트와 같은 지표로 계측된다")
    void deliversParticipantCountWithoutId() {

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RoomSseManager manager = newRoomSseManager(
                mock(SseEventBuffer.class), mock(ParticipantCountPublisher.class), registry);

        manager.subscribe(ROOM_ID, null);

        manager.deliverParticipantCountLocal(ROOM_ID, 5);

        assertThat(registry.get("upbid.sse.send.attempts")
                .tag("event", RoomSseManager.PARTICIPANT_COUNT_EVENT)
                .counter().count()).isEqualTo(1);
        assertThat(manager.getParticipantCount(ROOM_ID))
                .as("참여자 수 이벤트를 보낸다고 실제 연결 수(로컬 카운트)가 바뀌지는 않는다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("heartbeat 로 구독을 걷어내면 disconnect() 가 즉시 증감을 발행한다")
    void broadcastsCountAfterHeartbeatSweep() {

        ParticipantCountPublisher publisher = mock(ParticipantCountPublisher.class);
        RoomSseManager manager = newRoomSseManager(mock(SseEventBuffer.class), publisher);

        manager.subscribe(ROOM_ID, null);
        SseEmitter dead = manager.subscribe(ROOM_ID, null);
        killClient(dead);

        clearInvocations(publisher);

        manager.sendHeartbeat();

        // ping 실패로 걷어낸 연결은 disconnect() 가 즉시 increment(-1) 로 반영한다(#311).
        verify(publisher).increment(ROOM_ID, -1);
    }

    @Test
    @DisplayName("걷어낼 구독이 없어도 heartbeat 마다 절대값을 다시 발행해 TTL 을 갱신한다")
    void broadcastsAbsoluteCountOnEveryHeartbeatEvenWhenNothingSwept() {

        ParticipantCountPublisher publisher = mock(ParticipantCountPublisher.class);
        RoomSseManager manager = newRoomSseManager(mock(SseEventBuffer.class), publisher);

        manager.subscribe(ROOM_ID, null);

        clearInvocations(publisher);

        manager.sendHeartbeat();

        // 연결 변화가 없어도 heartbeat 마다 방 단위로 절대값을 재발행한다 — 그러지 않으면
        // 조용한 방의 Redis 필드 TTL 이 만료돼 다른 서버 집계에서 이 서버 몫이 빠진다(#311).
        verify(publisher, times(1)).publish(eq(ROOM_ID), anyInt());
    }

    @Test
    @DisplayName("방을 닫으면 그 방의 연결이 모두 끊긴다")
    void closesEverySubscriberOnRoomClose() {

        SseEmitter first = roomSseManager.subscribe(ROOM_ID, null);
        SseEmitter second = roomSseManager.subscribe(ROOM_ID, null);

        roomSseManager.closeRoom(ROOM_ID);

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isZero();

        // Map 에서 지우기만 하면 연결은 살아 있어 emitter-timeout(1시간)까지 남고, 그사이
        // 프록시 idle timeout 에 끊기면 EventSource 가 다시 붙는다. complete() 까지 불러야
        // 실제로 닫힌다 — 닫힌 emitter 에 쓰면 IllegalStateException 이 난다.
        assertThatThrownBy(() -> first.send("payload")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> second.send("payload")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("방을 닫으면 이벤트 버퍼와 순차 ID 카운터가 모두 정리된다")
    void clearsBufferAndSequenceOnRoomClose() {

        SseEventBuffer buffer = mock(SseEventBuffer.class);
        RoomSseManager manager = newRoomSseManager(buffer, mock(ParticipantCountPublisher.class));

        manager.subscribe(ROOM_ID, null);

        manager.closeRoom(ROOM_ID);

        // 끝난 방은 재연결 replay 대상이 아니다. 버퍼와 카운터가 둘 다 Redis 에 있어
        // 이 호출 하나로 함께 지워진다(SseEventBufferTest 에서 확인).
        verify(buffer).clear(ROOM_ID);
    }

    @Test
    @DisplayName("방을 닫으면 참여자 수 집계 키도 함께 지운다")
    void clearsParticipantCountOnRoomClose() {

        ParticipantCountPublisher publisher = mock(ParticipantCountPublisher.class);
        RoomSseManager manager = newRoomSseManager(mock(SseEventBuffer.class), publisher);

        manager.subscribe(ROOM_ID, null);
        clearInvocations(publisher);

        manager.closeRoom(ROOM_ID);

        verify(publisher).clear(ROOM_ID);
    }

    @Test
    @DisplayName("닫은 방에 전달을 시도해도 남은 연결이 없어 아무 일도 일어나지 않는다")
    void deliversNothingToClosedRoom() {

        SseEmitter emitter = roomSseManager.subscribe(ROOM_ID, null);
        roomSseManager.closeRoom(ROOM_ID);

        assertThatCode(() -> roomSseManager.deliverLocal(ROOM_ID, EVENT_NAME, 1L, "payload"))
                .doesNotThrowAnyException();

        assertThat(roomSseManager.getParticipantCount(ROOM_ID)).isZero();
        assertThatThrownBy(() -> emitter.send("payload")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("연결이 없는 방을 닫아도 예외가 발생하지 않는다")
    void closesRoomWithoutSubscribers() {

        assertThatCode(() -> roomSseManager.closeRoom(ROOM_ID)).doesNotThrowAnyException();
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
        RoomSseManager manager = newRoomSseManager(
                mock(SseEventBuffer.class), mock(ParticipantCountPublisher.class), registry);

        // 게이지는 @PostConstruct 에서 붙는다. 직접 생성한 객체에서는 안 불리므로 여기서 부른다.
        manager.bindMetrics();

        SseEmitter first = manager.subscribe(ROOM_ID, null);
        SseEmitter second = manager.subscribe(ROOM_ID, null);

        assertThat(rooms(registry)).isEqualTo(1);

        // 연결이 끊긴 상태를 만들고 heartbeat 로 걷어내게 한다.
        killClient(first);
        killClient(second);
        manager.sendHeartbeat();

        assertThat(manager.getParticipantCount(ROOM_ID)).isZero();
        assertThat(rooms(registry))
                .as("방을 안 지우면 한 번 오른 뒤 영영 안 내려온다")
                .isZero();
    }

    @Test
    @DisplayName("연결 수립과 방 종료 연결 수를 중복 없이 기록한다")
    void recordsOpenedAndRoomClosedConnections() {

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RoomSseManager manager = newRoomSseManager(
                mock(SseEventBuffer.class), mock(ParticipantCountPublisher.class), registry);

        manager.subscribe(ROOM_ID, null);
        manager.subscribe(ROOM_ID, null);
        manager.closeRoom(ROOM_ID);

        assertThat(registry.get("upbid.sse.connections.opened").counter().count()).isEqualTo(2);
        assertThat(registry.get("upbid.sse.connections.closed")
                .tag("reason", SseMetrics.CLOSE_ROOM_CLOSED)
                .counter().count()).isEqualTo(2);
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
    @DisplayName("재연결(Last-Event-ID 있음)로 구독하면 그 이후 이벤트를 버퍼에서 조회한다")
    void queriesBufferOnReconnectSubscribe() {

        SseEventBuffer buffer = mock(SseEventBuffer.class);
        when(buffer.getEventsAfter(any(), anyLong())).thenReturn(List.of());
        RoomSseManager manager = newRoomSseManager(buffer, mock(ParticipantCountPublisher.class));

        manager.subscribe(ROOM_ID, 10L);

        verify(buffer, times(1)).getEventsAfter(ROOM_ID, 10L);
    }

    @Test
    @DisplayName("최초 연결(Last-Event-ID 없음)은 버퍼 조회 자체를 하지 않는다")
    void skipsBufferQueryOnFreshSubscribe() {

        SseEventBuffer buffer = mock(SseEventBuffer.class);
        RoomSseManager manager = newRoomSseManager(buffer, mock(ParticipantCountPublisher.class));

        manager.subscribe(ROOM_ID, null);

        verify(buffer, never()).getEventsAfter(any(), anyLong());
    }

    @Test
    @DisplayName("재연결 시 아직 아무 이벤트도 못 받은 emitter는 버퍼 조회 자체를 건너뛴다")
    void skipsNeverDeliveredEmitterOnReconnectReplay() {

        SseEventBuffer buffer = mock(SseEventBuffer.class);
        RoomSseManager manager = newRoomSseManager(buffer, mock(ParticipantCountPublisher.class));

        manager.subscribe(ROOM_ID, null);

        manager.replayAfterReconnect();

        // lastDeliveredEventId가 -1(아직 아무것도 못 받음)이면, 접속 전 역사를 잘못
        // 재전송하는 걸 막기 위해 이 emitter에 대한 버퍼 조회 자체를 하지 않는다.
        verify(buffer, never()).getEventsAfter(any(), anyLong());
    }

    @Test
    @DisplayName("emitter마다 자기 lastDeliveredEventId 기준으로 개별적으로 replay를 조회한다")
    void queriesReplayPerEmitterUsingItsOwnLastDeliveredEventId() {

        SseEventBuffer buffer = mock(SseEventBuffer.class);
        when(buffer.getEventsAfter(any(), anyLong())).thenReturn(List.of());
        RoomSseManager manager = newRoomSseManager(buffer, mock(ParticipantCountPublisher.class));

        manager.subscribe(ROOM_ID, null);                          // emitter B: 아직 -1
        manager.deliverLocal(ROOM_ID, EVENT_NAME, 5L, "payload");  // B만 5까지 받음(A는 아직 구독 전)
        manager.subscribe(ROOM_ID, null);                          // emitter A: 여전히 -1

        manager.replayAfterReconnect();

        verify(buffer, times(1)).getEventsAfter(ROOM_ID, 5L);
        verify(buffer, never()).getEventsAfter(eq(ROOM_ID), eq(-1L));
    }

    @Test
    @DisplayName("이 인스턴스가 갖고 있는 모든 방에 대해 각각 재연결 replay를 수행한다")
    void replaysAcrossAllRoomsOnThisInstance() {

        SseEventBuffer buffer = mock(SseEventBuffer.class);
        when(buffer.getEventsAfter(any(), anyLong())).thenReturn(List.of());
        RoomSseManager manager = newRoomSseManager(buffer, mock(ParticipantCountPublisher.class));

        Long roomA = 1L;
        Long roomB = 2L;
        manager.subscribe(roomA, null);
        manager.deliverLocal(roomA, EVENT_NAME, 3L, "payload");
        manager.subscribe(roomB, null);
        manager.deliverLocal(roomB, EVENT_NAME, 9L, "payload");

        manager.replayAfterReconnect();

        verify(buffer).getEventsAfter(roomA, 3L);
        verify(buffer).getEventsAfter(roomB, 9L);
    }
}
