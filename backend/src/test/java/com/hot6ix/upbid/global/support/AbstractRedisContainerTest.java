package com.hot6ix.upbid.global.support;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 를 실제로 띄워 쓰는 테스트의 공통 컨테이너. {@link AbstractMySqlContainerTest}와 같은 방식이다.
 *
 * <p>Spring 컨텍스트는 안 띄운다. 이 컨테이너를 쓰는 테스트들이 보는 것은 Redis 자료구조와
 * 스크립트의 동작이라, 컨텍스트 로딩까지는 필요하지 않다.
 */
public abstract class AbstractRedisContainerTest {

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    protected static final LettuceConnectionFactory CONNECTION_FACTORY;
    protected static final StringRedisTemplate STRING_REDIS_TEMPLATE;

    static {
        REDIS_CONTAINER.start();

        CONNECTION_FACTORY = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379)));
        CONNECTION_FACTORY.afterPropertiesSet();

        STRING_REDIS_TEMPLATE = new StringRedisTemplate(CONNECTION_FACTORY);
    }
}
