package com.site.xidong.domain.feedback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.site.xidong.config.RetryConfig;
import com.site.xidong.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * [재시도] ClaudeApiClient.requestFeedback()에 붙인 @Retryable이 실제로 재시도하는지
 * 실측한다 — 목(mock) RestTemplate이 처음 N번은 예외를 던지고 이후 성공하도록 만들어,
 * "일시적 실패 → 재시도 → 성공"이 로그와 호출 횟수로 확인되는지를 검증한다.
 */
@SpringJUnitConfig(classes = ClaudeApiClientRetryTest.TestCtx.class)
@TestPropertySource(properties = "claude.api.key=test-key")
class ClaudeApiClientRetryTest {

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private RestTemplate claudeRestTemplate;

    // @SpringJUnitConfig는 컨텍스트(따라서 claudeRestTemplate mock 인스턴스도)를 테스트 메서드끼리
    // 공유하므로, 매 테스트 전에 이전 호출 기록을 지워야 verify(times(...))가 이 테스트만의
    // 호출 횟수를 정확히 센다.
    @BeforeEach
    void resetMock() {
        reset(claudeRestTemplate);
    }

    @Test
    void 두번_실패하고_세번째에_성공하면_재시도_끝에_결과를_반환한다() {
        String successBody = "{\"content\":[{\"text\":\"실제 피드백 결과\"}]}";
        when(claudeRestTemplate.exchange(eq("https://api.anthropic.com/v1/messages"),
                eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("일시적 네트워크 오류 #1"))
                .thenThrow(new ResourceAccessException("일시적 네트워크 오류 #2"))
                .thenReturn(ResponseEntity.ok(successBody));

        String result = claudeApiClient.requestFeedback("질문", "답변");

        assertThat(result).isEqualTo("실제 피드백 결과");
        verify(claudeRestTemplate, times(3))
                .exchange(eq("https://api.anthropic.com/v1/messages"), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    void MAX_ATTEMPTS를_넘겨_계속_실패하면_결국_예외를_던진다() {
        when(claudeRestTemplate.exchange(eq("https://api.anthropic.com/v1/messages"),
                eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("영구 장애"));

        assertThatThrownBy(() -> claudeApiClient.requestFeedback("질문", "답변"))
                .isInstanceOf(CustomException.class);

        verify(claudeRestTemplate, times(3))
                .exchange(eq("https://api.anthropic.com/v1/messages"), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Configuration
    @Import(RetryConfig.class)
    static class TestCtx {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RestTemplate claudeRestTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        ClaudeApiClient claudeApiClient(ObjectMapper objectMapper, RestTemplate claudeRestTemplate) {
            return new ClaudeApiClient(objectMapper, claudeRestTemplate);
        }
    }
}
