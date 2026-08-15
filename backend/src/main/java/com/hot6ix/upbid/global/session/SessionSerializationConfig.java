package com.hot6ix.upbid.global.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 세션 값을 Redis에 JSON으로 저장한다. 빈 이름이 {@code springSessionDefaultRedisSerializer}로
 * 고정돼 있어야 Spring Session이 기본(JDK) 직렬화 대신 이 빈을 골라 쓴다.
 *
 * <p>JDK 기본 직렬화로 두면 Redis 안 값이 사람이 못 읽는 바이너리라 디버깅할 때 눈으로
 * 확인할 방법이 없다. record({@code PendingSignup})의 {@code serialVersionUID} 검사가
 * 면제되는 것과는 별개 문제라, JSON으로 바꿔 {@code redis-cli}로도 값을 바로 읽을 수 있게 한다.
 *
 * <p>{@code GenericJackson2JsonRedisSerializer}(Jackson 2 기반)는 spring-data-redis 4.0부터
 * {@code forRemoval = true}로 deprecated라 Jackson 3 기반인 이 클래스를 쓴다. Boot 4가 기본으로
 * 물고 오는 Jackson도 이미 Jackson 3(tools.jackson)이다.
 *
 * <p>앱 전역 {@code ObjectMapper}를 재사용하지 않고 전용 인스턴스를 쓴다 — 여기서만
 * 폴리모픽 타입 정보(<code>@class</code>)를 얹어야 해서, 외부 요청·응답 직렬화에 쓰는
 * {@code ObjectMapper}와 분리한다.
 */
@Configuration
public class SessionSerializationConfig {

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // builder()의 defaultTyping은 기본 false라, 켜주지 않으면 스칼라 값(Long 등)에
        // 타입 정보가 안 붙어서 역직렬화 시 Integer로 돌아온다(직접 재현해서 확인).
        return GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();
    }
}
