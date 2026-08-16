package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
import com.hot6ix.upbid.domain.sse.event.ParticipantCountPublisher;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SseProperties.class)
public class RoomSseManager {

    /**
     * {@code EventType}에 없는 유일한 이벤트 이름이라 {@code SseMetrics}가 태그값으로 가져간다.
     * {@code ParticipantCountPublisher}(다른 패키지)도 발행 지표 태그로 같은 값을 쓴다.
     */
    public static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";

    private static final int PARTICIPANT_JOINED = 1;
    private static final int PARTICIPANT_LEFT = -1;

    private final Map<Long, Set<SseEmitter>> roomEmitters = new ConcurrentHashMap<>();

    private final SseProperties sseProperties;
    /** 계측은 이 객체가 안다. 이 클래스는 Micrometer 를 모른다. */
    private final SseMetrics sseMetrics;
    private final SseEventBuffer sseEventBuffer;
    /** 참여자 수 전역 합계 갱신+발행. 여기서 로컬 값만 쏘면 다른 인스턴스가 모른다. */
    private final ParticipantCountPublisher participantCountPublisher;

    /**
     * 지금 열려 있는 연결 수를 지표로 내보낸다. 게이지는 스크랩할 때 위 Map 을 읽어 가는
     * 방식이라, 연결을 등록·해제하는 코드에는 계측이 안 들어간다.
     *
     * <p>{@code @PostConstruct} 를 여기 쓰는 건 괜찮다. 테스트가 이 클래스를 직접 생성하면
     * 이 콜백이 안 불려서 게이지가 안 붙지만, 그뿐이고 동작이 깨지지는 않는다. <b>값을 필드에
     * 담는 용도로는 쓰지 않는다</b> — 그러면 직접 생성한 객체에서 null 이 되어 터진다.
     */
    @PostConstruct
    void bindMetrics() {
        sseMetrics.bindConnections(roomEmitters);
    }

    /**
     * SSE 구독을 등록한다. 구독 시점의 현재 상태는 물품 조회 API가 내려주므로 초기 이벤트는 없다.
     *
     * <p>재연결({@code lastEventId != null})이면 버퍼에서 그 ID 이후 이벤트를 replay한다. 아래
     * 참여자 수 브로드캐스트는 id 없이 나가므로(#311) {@code Last-Event-ID}에는 영향을 주지
     * 않는다 — 최초 연결이든 재연결이든 이 값이 {@code Last-Event-ID}의 시작점이 되지 않는다.
     */
    public SseEmitter subscribe(Long roomId, Long lastEventId) {
        SseEmitter emitter = new SseEmitter(sseProperties.emitterTimeoutMs());

        register(roomId, emitter);

        emitter.onCompletion(() -> disconnect(roomId, emitter, SseMetrics.CLOSE_COMPLETED));
        emitter.onTimeout(() -> disconnect(roomId, emitter, SseMetrics.CLOSE_TIMEOUT));
        emitter.onError(e -> disconnect(roomId, emitter, SseMetrics.CLOSE_SEND_ERROR));

        if (lastEventId != null) {
            List<BufferedEvent> missed = sseEventBuffer.getEventsAfter(roomId, lastEventId);
            for (BufferedEvent event : missed) {
                send(roomId, emitter, event.eventName(), event.id(), event.data());
            }
        }

        incrementParticipantCount(roomId, PARTICIPANT_JOINED);

        log.info("sse 연결 완료: roomId={}, lastEventId={}", roomId, lastEventId);

        return emitter;
    }

    /**
     * <b>이 인스턴스에</b> 붙어 있는 연결에만 이벤트를 쏜다. 다른 인스턴스에는 Redis 채널이
     * 이미 같은 이벤트를 날랐고, 거기서도 각자 이 메서드가 불린다.
     *
     * <p>{@code SseEventSubscriber}가 부르는 것이 유일한 경로다. 이벤트를 만든 쪽이 여기를
     * 직접 부르면 자기 방 클라이언트에게 같은 이벤트가 두 번 간다.
     *
     * <p><b>여기서 붙는 연결이 없다고 돌아가도 이벤트는 안 사라진다.</b> 버퍼 저장과 발행은
     * 이미 끝난 뒤라, 이 인스턴스에 구독자가 없다는 사실만 뜻한다.
     *
     * <p>{@code upbid.sse.broadcast}로 이 인스턴스의 fan-out 비용을 잰다(#234). 다만 이제
     * 이 시간은 입찰 응답 시간에 얹히지 않는다. 톰캣 스레드는 발행까지만 하고 돌아가고,
     * 실제 쓰기는 Redis 구독 스레드가 한다.
     *
     * <p>쏠 대상이 없으면 재지 않는다. 0에 가까운 값이 히스토그램에 섞이면 p95가 실제보다
     * 낮게 나온다.
     */
    public void deliverLocal(Long roomId, String name, long id, Object data) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        sseMetrics.recordBroadcast(name, () -> {
            for (SseEmitter emitter : emitters) {
                send(roomId, emitter, name, id, data);
            }
        });
    }

    /**
     * 참여자 수를 이 인스턴스에 붙은 연결에만 전달한다. {@code deliverLocal}과 달리 id가
     * 없다 — 재연결 replay 버퍼에 안 쌓이는 이벤트라 순차 번호 자체가 없기 때문이다.
     *
     * <p>{@code ParticipantCountSubscriber}가 부르는 것이 유일한 경로다.
     */
    public void deliverParticipantCountLocal(Long roomId, int count) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        sseMetrics.recordBroadcast(PARTICIPANT_COUNT_EVENT, () -> {
            for (SseEmitter emitter : emitters) {
                send(roomId, emitter, PARTICIPANT_COUNT_EVENT, null, new ParticipantCountDto(count));
            }
        });
    }

    public int getParticipantCount(Long roomId) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);
        return emitters == null ? 0 : emitters.size();
    }

    /**
     * @param id 버퍼에 저장된 순차 ID. 클라이언트는 이 값을 {@code Last-Event-ID}로 저장해
     *           재연결 시 서버에 보낸다. {@code null}이면 SSE 프레임에 {@code id:} 필드
     *           자체를 안 실어서 브라우저의 {@code Last-Event-ID}를 건드리지 않는다
     *           ({@code deliverParticipantCountLocal}이 이 경로를 쓴다).
     */
    private void send(Long roomId, SseEmitter emitter, String name, Long id, Object data) {
        sseMetrics.recordSendAttempt(name);
        Timer.Sample sample = sseMetrics.startSend();

        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event().name(name).data(data);
            if (id != null) {
                event.id(String.valueOf(id));
            }
            emitter.send(event);

            sseMetrics.recordSendSuccess(name);
        } catch (IOException | IllegalStateException e) {
            sseMetrics.recordSendFailure(name, e);
            disconnect(roomId, emitter, SseMetrics.CLOSE_SEND_ERROR);
            log.debug("sse 전송 중 끊긴 연결 정리: roomId={}, name={}, remaining={}, cause={}",
                    roomId, name, getParticipantCount(roomId), cause(e));
            emitter.completeWithError(e);
        } catch (RuntimeException e) {
            sseMetrics.recordSendFailure(name, e);
            disconnect(roomId, emitter, SseMetrics.CLOSE_SEND_ERROR);
            log.warn("sse 전송 실패로 연결 종료: roomId={}, name={}", roomId, name, e);
            emitter.completeWithError(e);
        } finally {
            sseMetrics.finishSend(name, sample);
        }
    }

    /**
     * 끊긴 연결은 장애가 아니라 정상적인 종료다. 스택트레이스를 남기면 봐야 할 로그가 묻히므로
     * 예외 종류와 메시지만 남긴다. Broken pipe인지 Connection reset인지, 아니면 이미 완료된
     * emitter였는지는 이 한 줄로 구분된다.
     */
    private String cause(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * <b>{@code unregister}가 이 사이클에서 실제로 뭔가를 지웠을 때만</b> 안쪽을 탄다. 같은
     * emitter에 대해 {@code onError}·{@code onCompletion}이 겹쳐 불려도(#250 참고)
     * {@code compute}가 두 번째 호출부터는 항상 {@code false}를 주므로, 참여자 수 갱신도
     * 정확히 한 번만 나간다.
     */
    private void disconnect(Long roomId, SseEmitter emitter, String reason) {
        if (unregister(roomId, emitter)) {
            sseMetrics.recordConnectionClosed(reason);
            log.info("sse 연결 종료: roomId={}, reason={}", roomId, reason);
            incrementParticipantCount(roomId, PARTICIPANT_LEFT);
        }
    }

    private void register(Long roomId, SseEmitter emitter) {
        roomEmitters
                .computeIfAbsent(roomId, id -> ConcurrentHashMap.newKeySet())
                .add(emitter);
        sseMetrics.recordConnectionOpened();
    }

    /**
     * 연결 하나를 걷어내고, 그 방의 마지막 연결이었으면 방 자체를 지운다.
     *
     * <p>방을 안 지우면 {@code upbid.sse.rooms}가 "지금 연결이 붙어 있는 방 수"가 아니라
     * "한 번이라도 연결이 있었던 방 수"가 되어 한 번 오른 뒤 영영 안 내려온다(실측으로 확인).
     * 빈 Set 도 방마다 하나씩 계속 쌓인다.
     *
     * <p><b>{@code get} → {@code isEmpty} → {@code remove} 로 나눠 쓰면 안 된다.</b> 비었는지
     * 확인한 뒤 지우기 전에 다른 스레드가 그 방에 새로 붙으면, 그 연결이 든 Set 을 통째로
     * 버려서 방금 붙은 사람이 아무 이벤트도 못 받는다. {@code compute} 는 키 하나에 대해
     * 원자적이라 그 틈이 없다.
     */
    private boolean unregister(Long roomId, SseEmitter emitter) {
        AtomicBoolean removed = new AtomicBoolean();

        roomEmitters.compute(roomId, (id, emitters) -> {
            if (emitters == null) {
                return null;
            }

            removed.set(emitters.remove(emitter));

            return emitters.isEmpty() ? null : emitters;
        });

        return removed.get();
    }

    /**
     * 연결/해제 한 건에 대해 참여자 수를 증감으로 반영한다. 절대값을 읽어서 보내면
     * 같은 서버에서 거의 동시에 일어난 두 연결 변화의 도착 순서가 뒤바뀔 때 최신 값이
     * 오래된 값에 덮어써질 수 있는데, 증감은 그 레이스가 없다(#311).
     */
    private void incrementParticipantCount(Long roomId, int delta) {
        participantCountPublisher.increment(roomId, delta);
    }

    /**
     * 이 인스턴스가 아는 로컬 참여자 수(절대값)를 Redis에 다시 쓰고, 전역 합계를 발행한다.
     * heartbeat가 방마다 주기적으로 불러 TTL을 갱신하고 드리프트를 바로잡는 용도로만 쓴다
     * (#311) — 연결/해제 한 건 한 건은 {@link #incrementParticipantCount}를 쓴다.
     */
    private void broadcastParticipantCount(Long roomId) {
        participantCountPublisher.publish(roomId, getParticipantCount(roomId));
    }

    /**
     * 끊긴 연결은 write를 시도할 때만 드러난다.
     * 주기적으로 ping을 보내 끊긴 연결을 걷어내고
     * 동시에 프록시, 로드밸런서의 idle timeout(통상 60초)에 연결이 끊기는 것을 방지한다.
     *
     * <p>ping이 실패해 걷어낸 연결의 참여자 수 갱신은 {@code disconnect()}가 직접 맡는다
     * (#311) — 예전에는 여기서 방 단위로 모아 한 번씩 재발행했지만, 그러면 heartbeat가
     * 아닌 다른 경로(도메인 이벤트 send 실패 등)로 먼저 걷힌 연결은 다음 heartbeat도 못
     * 잡아서 무기한 stale할 수 있었다.
     *
     * <p>ping과 별개로, 방마다 <b>연결 변화가 없어도</b> 절대값을 다시 발행한다(#311). 그
     * 방에 한동안 입장·퇴장이 없으면 이 방에 대한 이 서버의 필드가 {@code FIELD_TTL}로
     * 만료돼서, 다른 서버가 나중에 합계를 낼 때 이 서버 몫이 통째로 빠질 수 있었다 — 실제로
     * 수동 테스트에서 재현됐다(서버 두 대 중 한쪽이 조용하면 합계가 그쪽 몫만큼 낮게 나옴).
     *
     * <p>이 작업은 물품 마감 예약과 {@code spring.task.scheduling.pool}을 같이 쓴다. 접속이
     * 많아 한 바퀴가 길어지면 마감이 밀릴 수 있어서, 한 바퀴에 걸린 시간을
     * {@code upbid.sse.heartbeat}로 잰다.
     */
    @Scheduled(fixedRateString = "${upbid.sse.heartbeat-interval-ms}")
    public void sendHeartbeat() {
        sseMetrics.recordHeartbeat(() ->
                roomEmitters.forEach((roomId, emitters) -> {
                    for (SseEmitter emitter : emitters) {
                        ping(roomId, emitter);
                    }
                    broadcastParticipantCount(roomId);
                }));
    }

    /**
     * Heartbeat 전송.
     *
     * 클라이언트가 브라우저를 종료하거나 네트워크가 단절된 경우
     * 실제 write 시점에 IOException(Broken pipe, Connection reset 등)이 발생할 수 있다.
     *
     * 또한 이미 완료(completed)된 emitter에 전송을 시도하면
     * IllegalStateException이 발생할 수 있다.
     *
     * 예외 발생 시 해당 emitter를 제거하여 죽은 연결을 정리한다.
     *
     * @return 살아 있으면 true, 걷어냈으면 false
     */
    private boolean ping(Long roomId, SseEmitter emitter) {
        String eventName = SseMetrics.HEARTBEAT_EVENT;
        sseMetrics.recordSendAttempt(eventName);
        Timer.Sample sample = sseMetrics.startSend();

        try {
            emitter.send(SseEmitter.event().comment("keep-alive"));
            sseMetrics.recordSendSuccess(eventName);
            return true;
        } catch (IOException | IllegalStateException e) {
            sseMetrics.recordSendFailure(eventName, e);
            disconnect(roomId, emitter, SseMetrics.CLOSE_SEND_ERROR);
            log.debug("sse heartbeat 중 끊긴 연결 정리: roomId={}, remaining={}, cause={}",
                    roomId, getParticipantCount(roomId), cause(e));
            emitter.completeWithError(e);
            return false;
        } catch (RuntimeException e) {
            sseMetrics.recordSendFailure(eventName, e);
            disconnect(roomId, emitter, SseMetrics.CLOSE_SEND_ERROR);
            log.warn("sse heartbeat 전송 실패로 연결 종료: roomId={}", roomId, e);
            emitter.completeWithError(e);
            return false;
        } finally {
            sseMetrics.finishSend(eventName, sample);
        }
    }

    /**
     * 경매방 종료 처리.
     *
     * <p>모든 SSE 연결을 종료하고 이벤트 버퍼를 비운다.
     * 단순히 emitter를 Map에서 제거하면 HTTP 연결은 살아있고
     * EventSource가 자동 재연결할 수 있으므로 {@code complete()}를 호출한다.
     *
     * <p>종료된 방에 대한 재연결은 구독 시점의 방 상태 검증으로 차단한다.
     *
     * <p>{@code SseEventSubscriber}가 {@code ROOM_CLOSED}를 받고 부른다. 인스턴스는 자기에게
     * 붙은 연결만 알기 때문에, 방을 닫은 인스턴스 하나가 아니라 이벤트를 받은 전부가 각자
     * 불러야 모든 연결이 끊긴다.
     */
    public void closeRoom(Long roomId) {
        Set<SseEmitter> closing = roomEmitters.remove(roomId);

        if (closing != null) {
            closing.forEach(emitter -> {
                sseMetrics.recordConnectionClosed(SseMetrics.CLOSE_ROOM_CLOSED);
                complete(emitter);
            });
        }

        // 버퍼와 순차 ID 카운터는 둘 다 Redis 에 있어 이 한 번으로 함께 지워진다.
        sseEventBuffer.clear(roomId);
        // 참여자 수 집계 키도 같이 지운다(#311). 종료된 방은 재구독이 막혀 있어 급하진
        // 않지만, KEY_TTL(2일)까지 남겨둘 이유가 없다.
        participantCountPublisher.clear(roomId);

        log.info("sse 방 종료: roomId={}, 끊은 연결={}", roomId, closing == null ? 0 : closing.size());
    }

    /**
     * 이미 완료된 emitter에 {@code complete()}를 부르면 {@code IllegalStateException}이 난다.
     * 방을 닫는 중이라 그 연결은 어차피 정리 대상이므로 삼킨다.
     */
    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("이미 종료된 sse 연결", e);
        }
    }
}
