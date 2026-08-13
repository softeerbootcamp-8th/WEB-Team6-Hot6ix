package com.hot6ix.upbid.global.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 가 필요한 테스트의 바탕. {@link AbstractMySqlContainerTest}와 같은 모양이다.
 *
 * <p>쓰는 방법이 둘이다. 스프링 컨텍스트를 띄우는 테스트는 {@code @ServiceConnection}이
 * {@code spring.data.redis} 설정을 알아서 채워주므로 상속만 하면 되고, 컨텍스트 없이 도는
 * 테스트는 {@link #redisConnectionFactory()}로 연결을 직접 만든다.
 *
 * <p>이미지는 로컬 {@code docker-compose.yml}과 같은 태그로 맞춘다.
 */
public abstract class AbstractRedisContainerTest {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    static {
        REDIS_CONTAINER.start();
    }

    /**
     * 컨테이너에 붙는 연결 팩토리를 만든다. <b>부른 쪽이 {@code destroy()}로 닫는다</b> —
     * 여기서 들고 있으면 컨텍스트를 띄우는 테스트에도 안 쓰는 연결이 하나 열린 채로 남는다.
     */
    protected static LettuceConnectionFactory redisConnectionFactory() {

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(
                        REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379)));

        connectionFactory.afterPropertiesSet();

        return connectionFactory;
    }
}
