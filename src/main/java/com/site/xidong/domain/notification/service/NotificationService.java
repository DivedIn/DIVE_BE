package com.site.xidong.domain.notification.service;

import com.site.xidong.domain.notification.repository.EmitterRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void send(String username, String eventName, Object data) {
        log.info("Emitter 조회 시도 - key: {}", username);
        emitterRepository.get(username).ifPresentOrElse(sseEmitter -> {
            try {
                sseEmitter.send(SseEmitter.event().name(eventName).data(data));
                log.info("Emitter 찾음 - hash: {}", System.identityHashCode(sseEmitter));
            } catch (IOException e) {
                emitterRepository.delete(username);
                throw new Error("Failed to Connect SSE");
            }
        }, () -> log.info("Emitter 없음 - {}", username));
    }
}
