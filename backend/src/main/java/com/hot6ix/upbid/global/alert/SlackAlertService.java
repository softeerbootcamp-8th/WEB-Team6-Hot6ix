package com.hot6ix.upbid.global.alert;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Slack 에러 알림 서비스.
 *
 * <p>운영 프로필({@code prod})에서만 실제 전송이 이루어지고,
 * 그 외 환경에서는 no-op 기본 구현이 동작한다.
 */
public interface SlackAlertService {

    void send(HttpServletRequest request, Exception e);

    /** 로컬·테스트 환경에서 동작하는 no-op 구현 */
    class Noop implements SlackAlertService {
        @Override
        public void send(HttpServletRequest request, Exception e) {
        }
    }
}
