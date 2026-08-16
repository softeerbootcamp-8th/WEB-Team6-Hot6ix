package com.hot6ix.upbid.domain.bid.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 입찰 Stream Consumer Group을 애플리케이션 기동 시 준비한다. */
@Configuration
@EnableConfigurationProperties(BidStreamProperties.class)
public class BidStreamConfig {

    @Bean
    public ApplicationRunner bidStreamGroupInitializer(
            StringRedisTemplate redis, BidStreamProperties properties) {

        return args -> {
            try {
                redis.execute((RedisCallback<String>) connection ->
                        connection.streamCommands().xGroupCreate(
                                properties.key().getBytes(StandardCharsets.UTF_8),
                                properties.group(), ReadOffset.from("0-0"), true));
            } catch (DataAccessException e) {
                if (!containsBusyGroup(e)) {
                    throw e;
                }
            }
        };
    }

    /** 여러 앱이 동시에 기동해 이미 그룹이 생긴 경우만 정상적인 멱등 초기화로 본다. */
    private static boolean containsBusyGroup(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
