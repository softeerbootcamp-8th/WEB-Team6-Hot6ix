package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 참여자 수 전역 집계에서 이 인스턴스를 구분하는 식별자(호스트명+포트).
 *
 * <p>포트는 {@code server.port} 설정값이 아니라 {@link WebServerInitializedEvent}로 받는
 * 실제 바인딩된 포트를 쓴다. 그래야 {@code server.port=0}으로 랜덤 포트를 쓰는 테스트
 * 환경에서도 정확한 값이 나온다.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SseProperties.class)
public class SseServerIdentifier implements ApplicationListener<WebServerInitializedEvent> {

    private final SseProperties sseProperties;

    private volatile String id;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        id = sseProperties.serverHostname() + ":" + event.getWebServer().getPort();
    }

    /**
     * 웹 서버가 완전히 뜬 뒤(요청을 실제로 받기 시작한 뒤)에만 호출한다. 참여자 수 갱신은
     * 항상 실제 SSE 구독·해제 시점에 일어나므로 이 시점보다 이르지 않다.
     */
    public String id() {
        if (id == null) {
            throw new IllegalStateException("웹 서버가 기동되기 전에는 서버 식별자를 알 수 없다");
        }
        return id;
    }
}
