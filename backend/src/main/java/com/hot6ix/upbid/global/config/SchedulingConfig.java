package com.hot6ix.upbid.global.config;

import io.lettuce.core.SocketOptions;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "upbid");
    }

    // TCP keepalive가 꺼져 있으면 연결이 죽어도 OS 소켓은 계속 ESTABLISHED로 보여서,
    // 다음 커맨드가 나갈 때까지는 죽은 걸 알 방법이 없다 (#299).
    //
    // LettuceClientOptionsBuilderCustomizer를 쓰는 이유: LettuceClientConfigurationBuilderCustomizer로
    // builder.clientOptions(...)를 호출하면 Boot가 이미 채워둔 ClientOptions(운영 Redis 자체 서명 CA
    // 트러스트스토어 포함, #294)를 통째로 새 것으로 덮어써서 TLS 신뢰 설정이 날아간다. 이 타입은
    // Boot가 구성 중인 ClientOptions.Builder에 socketOptions만 얹기 때문에 그 문제가 없다.
    @Bean
    public LettuceClientOptionsBuilderCustomizer lettuceKeepAliveCustomizer() {
        return builder -> builder.socketOptions(SocketOptions.builder().keepAlive(true).build());
    }
}
