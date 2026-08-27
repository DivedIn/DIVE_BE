package com.site.xidong.domain.feedback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class ClaudeApiClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-3-7-sonnet-20250219";
    private static final int MAX_TOKENS = 1024;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${claude.api.key}")
    private String apiKey;

    public ClaudeApiClient(ObjectMapper objectMapper, @Qualifier("claudeRestTemplate") RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * Claude API를 호출해 피드백 텍스트를 반환한다.
     * [재시도] 일시적 네트워크 오류/5xx 등으로 callApi()가 CustomException을 던지면
     * 1초 → 2초 간격으로 최대 3번까지 재시도한다.
     */
    @Retryable(retryFor = CustomException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String requestFeedback(String question, String answer) {
        String prompt = answer + "은 CS 면접 질문 [" + question + "]에 대한 답변 영상을 음성으로 변환한 후 " +
                "STT 변환한거야. 그러니 오타라고 생각하지 말고 융통성 있게 받아들여줘. " +
                "이 답변을 실제 개발자 채용 면접 답변이라고 생각하고 내용 측면과 전달력 측면에서 " +
                "피드백해줘. 결과는 한국어로 전달해줘.";

        String requestBody = buildRequestBody(prompt);
        ResponseEntity<String> response = callApi(requestBody);
        return parseResponse(response.getBody());
    }

    private String buildRequestBody(String prompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", MODEL);
            root.put("max_tokens", MAX_TOKENS);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);
            root.set("messages", messages);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.AI_API_ERROR);
        }
    }

    private ResponseEntity<String> callApi(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Claude API 응답 오류: {}", response.getStatusCode());
                throw new CustomException(ErrorCode.AI_API_ERROR);
            }
            return response;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude API 호출 실패", e);
            throw new CustomException(ErrorCode.AI_API_ERROR);
        }
    }

    private String parseResponse(String responseBody) {
        try {
            ObjectNode root = objectMapper.readValue(responseBody, ObjectNode.class);
            return root.path("content").path(0).path("text").asText();
        } catch (Exception e) {
            log.error("Claude API 응답 파싱 실패", e);
            throw new CustomException(ErrorCode.AI_API_ERROR);
        }
    }
}
