package com.site.xidong.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.site.xidong.domain.notification.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * [SSE 멀티서버화] video-notification 채널을 구독한다. 이 메시지는 어느 인스턴스가 보냈든
 * (자기 자신이 보낸 것 포함) 모든 인스턴스가 받는다 — 그중 실제로 해당 username의 SSE
 * emitter를 들고 있는 인스턴스만 로컬 전송에 성공하고, 나머지는 조용히 무시(로그만 남김)한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSubscriber implements MessageListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            NotificationMessage notification = objectMapper.readValue(payload, NotificationMessage.class);
            notificationService.deliverLocally(
                    notification.getUsername(), notification.getEventName(), notification.getDataJson());
        } catch (Exception e) {
            log.error("Redis 알림 메시지 처리 실패", e);
        }
    }
}
