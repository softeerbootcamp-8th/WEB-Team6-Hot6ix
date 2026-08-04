package com.hot6ix.upbid.global.alert;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackAlertConfig {

    /** SlackAlertServiceImpl이 없는 환경에서 no-op 구현을 빈으로 등록한다. */
    @Bean
    @ConditionalOnMissingBean(SlackAlertService.class)
    public SlackAlertService noopSlackAlertService() {
        return new SlackAlertService.Noop();
    }
}
