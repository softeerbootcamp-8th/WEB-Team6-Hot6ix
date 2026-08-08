package com.hot6ix.upbid.global.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 개발용 정적 페이지를 {@code local}·{@code perf} 밖에서 막는다.
 *
 * <p>이 페이지들은 방 생성부터 입찰까지 한 화면에서 누르게 만든 내부 도구다.
 * {@code src/main/resources/static/} 에 있으면 <b>프로파일과 무관하게 서빙</b>되므로,
 * 운영에 올라가면 {@code https://도메인/dev-console.html} 이 그냥 열린다. 부르는 API 는
 * 서버가 권한을 다시 검증하고 {@code dev-login} 도 운영에는 없어서 로그인 우회는 안 되지만,
 * <b>API 구조와 개발 흐름이 통째로 드러나고</b> 판매자로 로그인한 사람에게는 의도하지 않은
 * 조작 화면이 열린다.
 *
 * <p><b>방어선이 둘이다.</b> {@code build.gradle} 이 {@code bootJar} 에서 이 파일들을 빼서
 * 애초에 배포 아티팩트에 안 실리게 하고(그게 1차), 이 인터셉터가 혹시 실리더라도 앱이 스스로
 * 404 를 내게 한다(2차). 인프라(nginx)에서 막지 않는 이유는 로컬에서 nginx 없이 띄우면
 * 다시 열리기 때문이다.
 *
 * <p>{@code @Profile("!local & !perf")} 라 개발할 때는 이 빈 자체가 안 만들어진다.
 */
@Profile("!local & !perf")
@Configuration
public class DevPageGuard implements WebMvcConfigurer {

    /** 늘리려면 여기에 적는다. 정적 파일이라 컨트롤러 매핑이 없어 경로로 막는 수밖에 없다. */
    private static final Set<String> DEV_PAGES = Set.of(
            "/dev-console.html",
            "/sse-test.html");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) {

                if (DEV_PAGES.contains(request.getRequestURI())) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return false;
                }

                return true;
            }
        }).addPathPatterns("/**");
    }
}
