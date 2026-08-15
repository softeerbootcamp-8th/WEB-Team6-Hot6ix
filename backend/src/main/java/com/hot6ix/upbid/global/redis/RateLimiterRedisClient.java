package com.hot6ix.upbid.global.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SslOptions;
import io.lettuce.core.TimeoutOptions;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Rate limiter 전용 Redis 연결. 마감 예약과 ShedLock 이 쓰는 기본 연결
 * ({@code RedisConnectionFactory}/{@code StringRedisTemplate})과 완전히 분리한다.
 *
 * <p><b>이 커넥션을 빈으로 노출하지 않는 것이 핵심이다.</b> Boot 의 Redis 자동설정은
 * {@code @ConditionalOnMissingBean(RedisConnectionFactory.class)} 로 동작하므로, 같은 타입의
 * 빈을 하나라도 애플리케이션이 직접 등록하면 기본 자동설정이 통째로 물러난다. 그러면 마감
 * 예약과 ShedLock 이 의도치 않게 이 리미터 전용 설정(짧은 타임아웃)으로 갈아타 버린다.
 * 그래서 {@link LettuceConnectionFactory}와 {@link StringRedisTemplate}을 private 필드로만
 * 들고 있고, 밖에는 {@link #execute}만 노출한다.
 *
 * <p>호스트·포트·인증정보·TLS 트러스트스토어는 {@link DataRedisConnectionDetails}(Boot 가
 * {@code spring.data.redis.*}로 자동 구성한 빈, Testcontainers {@code @ServiceConnection}에서도
 * 동일하게 채워진다)에서 그대로 읽어 기본 연결과 같은 대상을 본다. 커맨드 타임아웃만
 * {@link RateLimiterRedisProperties}로 짧게 가져가, Redis 가 응답 없이 멈춰도 입찰 요청이
 * 그만큼 오래 붙들리지 않게 한다.
 */
@Component
@EnableConfigurationProperties(RateLimiterRedisProperties.class)
public class RateLimiterRedisClient implements DisposableBean {

    private final LettuceConnectionFactory connectionFactory;
    private final StringRedisTemplate redisTemplate;

    public RateLimiterRedisClient(
            DataRedisConnectionDetails connectionDetails,
            RateLimiterRedisProperties properties) {

        this.connectionFactory = createConnectionFactory(connectionDetails, properties);
        this.connectionFactory.afterPropertiesSet();
        this.redisTemplate = new StringRedisTemplate(this.connectionFactory);
    }

    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }

    @Override
    public void destroy() {
        connectionFactory.destroy();
    }

    private static LettuceConnectionFactory createConnectionFactory(
            DataRedisConnectionDetails connectionDetails, RateLimiterRedisProperties properties) {

        DataRedisConnectionDetails.Standalone standalone = connectionDetails.getStandalone();

        RedisStandaloneConfiguration serverConfig =
                new RedisStandaloneConfiguration(standalone.getHost(), standalone.getPort());
        serverConfig.setDatabase(standalone.getDatabase());

        if (StringUtils.hasText(connectionDetails.getUsername())) {
            serverConfig.setUsername(connectionDetails.getUsername());
        }
        if (StringUtils.hasText(connectionDetails.getPassword())) {
            serverConfig.setPassword(RedisPassword.of(connectionDetails.getPassword()));
        }

        SslBundle sslBundle = connectionDetails.getSslBundle();

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(properties.commandTimeout())
                        .clientOptions(buildClientOptions(sslBundle));

        if (sslBundle != null) {
            clientConfigBuilder.useSsl();
        }

        return new LettuceConnectionFactory(serverConfig, clientConfigBuilder.build());
    }

    /**
     * Boot 의 {@code LettuceConnectionConfiguration}이 SSL 번들을 {@code ClientOptions}에
     * 반영하는 것과 같은 방식을 따른다. 직접 만들지 않으면(#294 에서 겪은 것과 같은 함정) 운영
     * Redis 의 자체 서명 인증서를 신뢰하지 못해 TLS 핸드셰이크가 실패한다.
     */
    private static ClientOptions buildClientOptions(SslBundle sslBundle) {

        ClientOptions.Builder builder = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().keepAlive(true).build())
                .timeoutOptions(TimeoutOptions.enabled());

        if (sslBundle != null) {
            SslOptions.Builder sslOptionsBuilder = SslOptions.builder()
                    .keyManager(sslBundle.getManagers().getKeyManagerFactory())
                    .trustManager(sslBundle.getManagers().getTrustManagerFactory());

            org.springframework.boot.ssl.SslOptions bundleOptions = sslBundle.getOptions();
            if (bundleOptions.getCiphers() != null) {
                sslOptionsBuilder.cipherSuites(bundleOptions.getCiphers());
            }
            if (bundleOptions.getEnabledProtocols() != null) {
                sslOptionsBuilder.protocols(bundleOptions.getEnabledProtocols());
            }

            builder.sslOptions(sslOptionsBuilder.build());
        }

        return builder.build();
    }
}
