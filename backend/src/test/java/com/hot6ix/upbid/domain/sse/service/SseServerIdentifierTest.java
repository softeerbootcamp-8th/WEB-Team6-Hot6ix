package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;

class SseServerIdentifierTest {

    private static final SseProperties PROPS = new SseProperties(30_000L, 3_600_000L, 50, "test-host", 128);

    private final SseServerIdentifier identifier = new SseServerIdentifier(PROPS);

    @Test
    @DisplayName("웹 서버가 기동되기 전에 id()를 부르면 예외가 난다")
    void throwsBeforeWebServerInitialized() {

        assertThatThrownBy(identifier::id).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("웹 서버 기동 이벤트를 받으면 호스트명과 실제 바인딩된 포트를 조합해 식별자를 만든다")
    void buildsIdFromHostnameAndActualBoundPort() {

        identifier.onApplicationEvent(webServerInitializedEvent(54321));

        assertThat(identifier.id()).isEqualTo("test-host:54321");
    }

    @Test
    @DisplayName("server.port 설정값이 아니라 실제 바인딩된 포트를 쓴다")
    void usesActualBoundPortNotConfiguredPort() {

        // server.port=0(랜덤 포트)으로 뜬 상황을 흉내낸다 — 설정값은 몰라도 실제 포트만 안다.
        identifier.onApplicationEvent(webServerInitializedEvent(58888));

        assertThat(identifier.id()).endsWith(":58888");
    }

    private WebServerInitializedEvent webServerInitializedEvent(int port) {
        WebServer webServer = mock(WebServer.class);
        when(webServer.getPort()).thenReturn(port);

        WebServerInitializedEvent event = mock(WebServerInitializedEvent.class);
        when(event.getWebServer()).thenReturn(webServer);

        return event;
    }
}
