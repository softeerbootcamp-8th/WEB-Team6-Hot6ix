package com.hot6ix.upbid.global.config;

import io.lettuce.core.ClientOptions;
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
    // 연결이 끊긴 동안 들어온 커맨드를 쌓아 두지 않고 그 자리에서 거절한다.
    //
    // 기본값은 자동 재연결이 켜져 있으면 커맨드를 큐에 쌓는데, Redis 가 죽어 있으면 그 커맨드가
    // spring.data.redis.timeout(5초)을 다 기다렸다가 실패한다. 조회 캐시가 붙은 뒤로는 그게
    // 곧 서비스 장애다 — 실측으로 Redis 를 내리자 공개 조회가 건마다 5.0초씩 걸렸다 (#320).
    // 캐시는 없으면 DB 로 가면 그만이라 기다릴 이유가 없다.
    @Bean
    public LettuceClientOptionsBuilderCustomizer lettuceKeepAliveCustomizer() {
        return builder -> builder
                .socketOptions(SocketOptions.builder().keepAlive(true).build())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
