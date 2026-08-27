package com.site.xidong.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.site.xidong.domain.notification.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

/**
 * [SSE 멀티서버화] NotificationService.send()는 더 이상 이 인스턴스의 로컬 emitterMap을
 * 직접 뒤지지 않고, 여기로 위임해 Redis 채널에 publish만 한다. 실제로 emitter를 들고 있는
 * 인스턴스(자기 자신일 수도, 다른 인스턴스일 수도 있다)는 NotificationSubscriber가 이
 * 메시지를 받아 로컬로 전달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic notificationTopic;

    public void publish(String username, String eventName, Object data) {
        try {
            String dataJson = objectMapper.writeValueAsString(data);
            String payload = objectMapper.writeValueAsString(
                    new NotificationMessage(username, eventName, dataJson));
            redisTemplate.convertAndSend(notificationTopic.getTopic(), payload);
            log.info("Redis 알림 publish: username={}, event={}", username, eventName);
        } catch (Exception e) {
            log.error("알림 publish 실패: username={}, event={}", username, eventName, e);
        }
    }
}
