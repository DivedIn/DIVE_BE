package com.site.xidong.config;

import com.site.xidong.domain.notification.service.NotificationSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * [SSE 멀티서버화] SseEmitter는 JVM 메모리(로컬 emitterMap)에만 있어서, 유저가 인스턴스 A에
 * SSE로 붙어 있는데 영상 처리가 인스턴스 B에서 끝나면 B는 그 emitter를 찾을 수 없어 알림이
 * 유실됐다. Redis Pub/Sub로 "누가 처리했든" 모든 인스턴스에 알림을 브로드캐스트하고, 실제로
 * emitter를 들고 있는 인스턴스만 로컬로 전달하게 한다.
 *
 * RedisConnectionFactory는 spring-boot-starter-data-redis가 spring.data.redis.host/port로
 * 자동 구성해준다 — 여기서는 채널 토픽과 리스너 컨테이너만 등록한다.
 */
@Configuration
public class RedisConfig {

    public static final String NOTIFICATION_CHANNEL = "video-notification";

    @Bean
    public ChannelTopic notificationTopic() {
        return new ChannelTopic(NOTIFICATION_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationSubscriber notificationSubscriber,
            ChannelTopic notificationTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(notificationSubscriber, notificationTopic);
        return container;
    }
}
