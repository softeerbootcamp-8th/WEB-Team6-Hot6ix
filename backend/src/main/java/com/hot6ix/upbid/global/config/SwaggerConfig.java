package com.hot6ix.upbid.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class SwaggerConfig {

    private static final String COOKIE_AUTH = "cookieAuth";

    private static final String DEV_LOGIN_PATH = "/api/v1/auth/dev-login";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Upbid API")
                        .description("## 인증 방법\n\n" +
                                "**로컬 환경**: 상단 `개발용 인증 > [로컬 전용] 테스트 로그인`을 먼저 호출하세요. " +
                                "테스트 유저가 자동 생성되고 SESSION 쿠키가 발급됩니다.\n\n" +
                                "**그 외 환경**: 카카오 로그인 후 발급된 SESSION 쿠키가 자동으로 포함됩니다.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")));
    }

    /**
     * 배포 환경의 문서에서 {@code dev-login} 경로를 지운다.
     *
     * <p>{@code DevLoginCondition} 이 배포에서도 토큰을 준 동안에는 빈을 만들기 때문에, 측정 창구를
     * 여는 동안 공개된 {@code /v3/api-docs} 에 테스트 로그인이 그대로 실린다. 엔드포인트 자체는
     * nginx 와 {@code DevLoginGate} 가 막지만 문서에 남을 이유는 없다.
     *
     * <p>{@code local} 과 {@code perf} 에서는 등록하지 않는다. 프론트 DEV 패널을 안 쓰고 Swagger UI
     * 에서 바로 세션을 받는 흐름이 있고, {@code info} 설명이 그 사용법을 안내하고 있기 때문이다.
     *
     * <p>경로만 지우면 그 경로가 유일했던 태그가 남아 Swagger UI 에 빈 섹션이 생긴다. 그래서 남은
     * 오퍼레이션이 하나도 참조하지 않는 태그도 같이 지운다.
     */
    @Bean
    @Profile("!local & !perf")
    public OpenApiCustomizer hideDevLoginFromDeployedDocs() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().remove(DEV_LOGIN_PATH);

            if (openApi.getTags() == null) {
                return;
            }

            Set<String> usedTags = openApi.getPaths().values().stream()
                    .flatMap(pathItem -> pathItem.readOperations().stream())
                    .filter(operation -> operation.getTags() != null)
                    .flatMap(operation -> operation.getTags().stream())
                    .collect(Collectors.toSet());

            openApi.getTags().removeIf(tag -> !usedTags.contains(tag.getName()));
        };
    }
}
