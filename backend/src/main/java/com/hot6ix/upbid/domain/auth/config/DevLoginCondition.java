package com.hot6ix.upbid.domain.auth.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * {@code dev-login} 을 이 실행에서 쓸 수 있는지 판정한다.
 *
 * <p>조건이 둘인데 {@code @Profile} 과 {@code @ConditionalOnProperty} 를 같이 달면 AND 로
 * 묶여서 OR 을 표현할 수 없다. 그래서 조건을 직접 쓴다.
 *
 * <ul>
 *   <li>{@code local}, {@code perf} — 지금까지처럼 그냥 켠다</li>
 *   <li>그 밖의 프로파일 — {@code upbid.dev-login.token} 이 있을 때만 켠다. 값을 안 주면
 *       빈 자체가 안 생겨서 엔드포인트가 404 다</li>
 * </ul>
 */
public class DevLoginCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {

        Environment environment = context.getEnvironment();

        if (environment.acceptsProfiles(Profiles.of("local", "perf"))) {
            return true;
        }

        return StringUtils.hasText(environment.getProperty("upbid.dev-login.token"));
    }
}
