package com.hot6ix.upbid.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {

    /**
     * 주입되는 EntityManager는 공유 프록시라 호출 시점의 트랜잭션에 묶인 실제
     * EntityManager로 위임한다. 그래서 싱글턴 Bean으로 둬도 스레드 안전하다.
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
