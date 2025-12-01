package com.site.xidong.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${slack.webhook-url}")
    private String webhookUrl;

    public void sendMessage(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", message);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, payload, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Slack 전송 실패: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error(e.getMessage(), e);
        }

    }
}
