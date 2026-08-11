package com.hot6ix.upbid.domain.sse.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * SSE 브로드캐스트를 입찰 요청 스레드에서 떼어내는 전용 실행기.
 *
 * <p>기존에는 {@code RoomSseManager.sendBroadCast} 가 요청 스레드에서 방 전원에게 순차로
 * 전송했다. 커밋 뒤(AFTER_COMMIT)라 락은 풀린 상태지만 <b>전송에 걸린 시간이 그대로 입찰 응답에
 * 얹혔다.</b> 실측(2026-08-10, 시나리오 1 vus 40): SSE 접속을 0 → 400 으로 늘리자 브로드캐스트
 * p95 가 30ms 로 오르면서 입찰 처리량이 3,172 → 1,441 req/s 로 55% 떨어졌다.
 *
 * <p><b>지금은 측정용 최소 구현이다.</b> 실제 적용 전에 아래를 정해야 한다.
 * <ul>
 *   <li><b>방별 순서 보장</b> — 클라이언트가 {@code Last-Event-ID} 로 복구하므로 같은 방의 이벤트가
 *       순서를 바꿔 도착하면 복구 지점이 어긋난다. {@code roomId} 해시로 파티션된 실행기가 필요하다.
 *       부하 측정에서는 배경 SSE 가 순서를 검증하지 않아 이대로도 숫자가 유효하다.</li>
 *   <li><b>큐가 넘칠 때의 정책</b> — 지금은 호출 스레드가 대신 실행({@link ThreadPoolExecutor.CallerRunsPolicy})
 *       해서 비동기화 이전 동작으로 돌아간다. 버리는 편이 나은지, 버려도 재연결 복구로 충분한지 판단이 필요하다.</li>
 * </ul>
 *
 * <p>스케줄러 풀({@code spring.task.scheduling.pool})과 공유하지 않는다. 공유하면 브로드캐스트가
 * 밀릴 때 물품 마감까지 함께 밀린다.
 */
@Configuration
public class SseBroadcastConfig {

    /**
     * 스레드 수는 앱에 준 CPU(perf 환경 기준 2코어)에 맞춰 작게 잡는다. 전송이 CPU 를 쓰는
     * 작업이면 스레드를 늘려도 총량이 안 줄고 컨텍스트 스위칭만 는다.
     *
     * <p>데몬 스레드로 둔다. 이 실행기는 {@link SseBroadcastExecutor} 안에 감싸여 있어 Spring 의
     * 빈 소멸 콜백을 안 받으므로, 데몬이 아니면 종료 때 JVM 이 안 내려간다.
     *
     * <p>빈 타입을 {@code Executor} 가 아닌 {@link SseBroadcastExecutor} 로 내보내는 이유는
     * 그 클래스 주석에 적었다. 대신 Micrometer 의 실행기 자동 계측을 못 받으므로 큐 깊이 지표는
     * 없다. 큐 상한(1000)이 초당 브로드캐스트 수(실측 124건/s)보다 한참 커서 측정 구간에는
     * 넘칠 일이 없다고 보고 넘어간다.
     */
    @Bean
    public SseBroadcastExecutor sseBroadcastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("sse-bc-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return new SseBroadcastExecutor(executor);
    }
}
