package com.site.xidong.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri("https://hooks.slack.com/services/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")
                .build();
    }

    // [재시도] ClaudeApiClient 전용. 기본 restTemplate 빈은 Slack 웹훅으로 rootUri가 고정돼 있어
    // (절대 URL을 넘기면 무시되긴 하지만) 다른 목적의 빈을 재사용하는 건 혼동을 낳는다.
    // 또한 ClaudeApiClient가 메서드 안에서 직접 new RestTemplate()을 만들던 걸 DI로 바꿔야
    // @Retryable 재시도 동작을 테스트에서 목(mock)으로 검증할 수 있다.
    @Bean(name = "claudeRestTemplate")
    public RestTemplate claudeRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
