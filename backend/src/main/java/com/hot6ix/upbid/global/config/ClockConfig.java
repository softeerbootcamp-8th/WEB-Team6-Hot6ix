package com.hot6ix.upbid.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각을 읽는 통로. 마감·연장처럼 <b>시각이 곧 판정 기준</b>인 코드가
 * {@code LocalDateTime.now()}를 직접 부르면 그 경계를 테스트로 고정할 수 없다. 마감 1밀리초
 * 전과 정각과 1밀리초 뒤가 각각 어떻게 갈리는지는 취향이 아니라 명세이므로 테스트로 박아야 한다.
 *
 * <p>시각을 스스로 읽지 않고 주입받으면 테스트가 시계를 원하는 값으로 갈아 끼울 수 있고,
 * "락을 300밀리초 기다리는 동안 시각이 흘렀다" 같은 상황도 재현할 수 있다.
 *
 * <p>{@code systemDefaultZone()}은 지금 코드가 쓰는 {@code LocalDateTime.now()}와 같은 시계다.
 * 이 빈을 넣는다고 동작이 달라지지 않는다. 여전히 서버 타임존에 묶여 있지만, 이제 그 자체가
 * 문제는 아니다 — 응답 경계의 {@link com.hot6ix.upbid.global.common.ServerTime}이 같은
 * {@code ZoneId.systemDefault()}로 오프셋을 붙이므로, 서버가 어느 시간대로 뜨든 응답값이
 * 사실과 일치한다(이슈 #183).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
