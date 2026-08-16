package com.hot6ix.upbid.domain.bid.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class BidStreamConfigTest extends AbstractRedisContainerTest {

    private static final String STREAM = "test:auction:bid:stream";
    private static final String GROUP = "test-bid-writers";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redis = new StringRedisTemplate(connectionFactory);
        redis.delete(STREAM);
    }

    @AfterAll
    static void tearDownRedis() {
        redis.delete(STREAM);
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("Stream이 없어도 Group을 만들고 같은 초기화를 반복할 수 있다")
    void initializesMissingStreamIdempotently() throws Exception {

        BidStreamProperties properties = new BidStreamProperties(
                STREAM, GROUP, "test-consumer", Duration.ofMillis(100),
                Duration.ofMillis(50), Duration.ofSeconds(5));
        ApplicationRunner initializer =
                new BidStreamConfig().bidStreamGroupInitializer(redis, properties);
        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);

        initializer.run(arguments);

        assertThat(redis.hasKey(STREAM)).isTrue();
        assertThat(redis.opsForStream().groups(STREAM))
                .anySatisfy(group -> assertThat(group.groupName()).isEqualTo(GROUP));
        assertThatCode(() -> initializer.run(arguments)).doesNotThrowAnyException();
    }
}
