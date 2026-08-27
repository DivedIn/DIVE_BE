package com.site.xidong.domain.notification.service;

import com.site.xidong.domain.notification.repository.EmitterRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationService {
    private static final Long DEFAULT_TIMEOUT = 60 * 60 * 1000L;
    private static final String CONNECTION = "connection";

    private final EmitterRepository emitterRepository;
    private final NotificationPublisher notificationPublisher;

    public SseEmitter connectNotification(String username) {
        log.info("SSE 연결 프로세스 시작: {}", username);

        SseEmitter sseEmitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitterRepository.save(username, sseEmitter);
        log.info("Emitter 저장 - key: {}, emitter hash: {}", username, System.identityHashCode(sseEmitter));

        sseEmitter.onCompletion(() -> {
            log.info("SSE 연결 완료 - {} emitter 삭제", username);
            emitterRepository.delete(username);
        });
        sseEmitter.onTimeout(() -> {
            log.info("SSE 타임아웃 - {} emitter 삭제", username);
            emitterRepository.delete(username);
        });
        sseEmitter.onError(throwable -> {
            log.error("SSE 오류 - {} emitter 삭제: {}", username, throwable.getMessage());
            emitterRepository.delete(username);
        });

        try {
            sseEmitter.send(SseEmitter.event()
                    .id("connection-established-001")
                    .name(CONNECTION)
                    .data("Connection completed!"));
            log.info("SSE 연결 완료: {}", username);
        } catch (IOException e) {
            log.error("SSE 초기 이벤트 전송 실패: {}", e.getMessage());
            emitterRepository.delete(username);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return sseEmitter;
    }

    // [SSE 멀티서버화] 이 인스턴스의 로컬 emitterMap을 더 이상 직접 뒤지지 않는다.
    // 호출부(VideoProcessingTxService 등)는 그대로 두고, Redis 채널에 publish만 해서
    // 실제로 emitter를 들고 있는 인스턴스(자기 자신일 수도 있다)가 받아가게 한다.
    public void send(String username, String eventName, Object data) {
        notificationPublisher.publish(username, eventName, data);
    }

    // [SSE 멀티서버화] NotificationSubscriber가 Redis에서 메시지를 받았을 때 호출한다.
    // 예전 send()가 하던 "로컬 emitterMap 조회 + 실제 전송" 로직이 그대로 여기로 옮겨왔다.
    // dataJson은 publish 시점에 이미 직렬화된 문자열이라 재역직렬화 없이 그대로 흘려보낸다.
    public void deliverLocally(String username, String eventName, String dataJson) {
        log.info("로컬 emitter 조회 시도 - key: {}", username);
        emitterRepository.get(username).ifPresentOrElse(sseEmitter -> {
            try {
                sseEmitter.send(SseEmitter.event().name(eventName).data(dataJson, MediaType.APPLICATION_JSON));
                log.info("로컬 emitter로 전송 완료 - hash: {}", System.identityHashCode(sseEmitter));
            } catch (IOException e) {
                emitterRepository.delete(username);
                log.error("로컬 emitter 전송 실패 - {}: {}", username, e.getMessage());
            }
        }, () -> log.info("이 인스턴스엔 emitter 없음(다른 인스턴스가 처리했거나 연결 없음) - {}", username));
    }
}
