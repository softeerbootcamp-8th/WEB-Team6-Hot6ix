package com.hot6ix.upbid.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String COOKIE_AUTH = "cookieAuth";

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
}
