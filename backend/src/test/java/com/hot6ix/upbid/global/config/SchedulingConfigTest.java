package com.hot6ix.upbid.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SchedulingConfig#lettuceKeepAliveCustomizer}가 {@code ClientOptions.Builder}에
 * keepalive만 얹고, Boot가 이미 구성해 둔 다른 옵션(TLS 신뢰 설정 등)은 그대로 두는지 검증한다.
 */
class SchedulingConfigTest {

    @Test
    @DisplayName("keepalive 커스터마이저는 socketOptions.keepAlive를 켠다")
    void enablesSocketKeepAlive() {
        ClientOptions.Builder builder = ClientOptions.builder();

        new SchedulingConfig().lettuceKeepAliveCustomizer().customize(builder);

        ClientOptions options = builder.build();
        assertThat(options.getSocketOptions().isKeepAlive()).isTrue();
    }

    @Test
    @DisplayName("keepalive 커스터마이저는 이미 설정된 timeoutOptions를 덮어쓰지 않는다")
    void doesNotOverwriteOtherClientOptions() {
        ClientOptions.Builder builder = ClientOptions.builder().timeoutOptions(io.lettuce.core.TimeoutOptions.enabled());

        new SchedulingConfig().lettuceKeepAliveCustomizer().customize(builder);

        ClientOptions options = builder.build();
        assertThat(options.getTimeoutOptions().isTimeoutCommands()).isTrue();
        assertThat(options.getSocketOptions().isKeepAlive()).isTrue();
    }
}
