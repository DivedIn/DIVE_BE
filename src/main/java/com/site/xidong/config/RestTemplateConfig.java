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
}
