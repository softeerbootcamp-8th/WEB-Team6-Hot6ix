package com.hot6ix.upbid.global.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
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
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceKeepAliveCustomizer() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().keepAlive(true).build())
                .build());
    }
}
