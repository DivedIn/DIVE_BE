package com.site.xidong.notification;

import com.site.xidong.security.SiteUserSecurityDTO;
import com.site.xidong.siteUser.SiteUser;
import com.site.xidong.siteUser.SiteUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final static Long DEFAULT_TIMEOUT = 60 * 60 * 1000L; // 1시간
    private final static String CONNECTION = "connection";

    private final SiteUserRepository siteUserRepository;
    private final EmitterRepository emitterRepository;

    @Transactional(readOnly = true)
    public SseEmitter connectNotification() throws Exception {
        log.info("SSE 연결 프로세스 시작");

        SseEmitter sseEmitter = null;
        String username = null;

        try {
            log.info("사용자 인증 정보 조회 중");
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                throw new RuntimeException("인증 정보가 올바르지 않습니다");
            }
            SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();

            log.info("사용자 정보 DB 조회 중");
            SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).orElseThrow(() ->
                    new RuntimeException("사용자를 찾을 수 없습니다: " + siteUserSecurityDTO.getUsername()));

            username = siteUser.getUsername();
            log.info("사용자 정보 조회 완료: {}", username);

            log.info("SseEmitter 객체 생성 중");
            // 새로운 SseEmitter를 만든다
            sseEmitter = new SseEmitter(DEFAULT_TIMEOUT);
            log.info("SseEmitter 객체 생성 완료");

            log.info("EmitterRepository에 저장 중");
            // username으로 SseEmitter를 저장
            emitterRepository.save(username, sseEmitter);
            log.info("Emitter 저장 - key: {}, emitter hash: {}", username, System.identityHashCode(sseEmitter));

            log.info("이벤트 핸들러 등록 중");
            final String finalUsername = username; // final 변수로 람다에서 사용

            sseEmitter.onCompletion(() -> {
                log.info("SSE 연결 완료 이벤트 발생 - {} 사용자의 emitter 삭제", finalUsername);
                emitterRepository.delete(finalUsername);
            });
            sseEmitter.onTimeout(() -> {
                log.info("SSE 연결 타임아웃 발생 - {} 사용자의 emitter 삭제", finalUsername);
                emitterRepository.delete(finalUsername);
            });

            sseEmitter.onError((throwable) -> {
                log.error("SSE 연결 오류 발생 - {} 사용자의 emitter 삭제: {}", finalUsername, throwable.getMessage());
                emitterRepository.delete(finalUsername);
            });

            log.info("이벤트 핸들러 등록 완료");

            log.info("초기 SSE 이벤트 전송 중");
            // 503 Service Unavailable 오류가 발생하지 않도록 첫 데이터를 보낸다.
            try {
                sseEmitter.send(SseEmitter.event()
                        .id("connection-established-001")
                        .name(CONNECTION)
                        .data("Connection completed!"));
                log.info("초기 SSE 이벤트 전송 완료");
            } catch (IOException exception) {
                log.error("SSE 이벤트 전송 실패: {}", exception.getMessage());
                // 전송 실패 시 emitter 정리
                if (username != null) {
                    emitterRepository.delete(username);
                }
                throw new Exception("Failed to Connect SSE", exception);
            }

            log.info("SSE 연결 프로세스 완료 - {} 사용자의 연결 설정됨", username);
            return sseEmitter;
        } catch (Exception e) {
            log.error("SSE 연결 중 오류 발생: {}", e.getMessage(), e);

            // 예외 발생 시 생성된 리소스 정리
            if (sseEmitter != null && username != null) {
                try {
                    emitterRepository.delete(username);
                    sseEmitter.completeWithError(e);
                } catch (Exception cleanupException) {
                    log.error("리소스 정리 중 오류 발생: {}", cleanupException.getMessage());
                }
            }
            throw e;
        }
    }

    public void send(String username, String eventName, Object data) {
        // username으로 SseEmitter를 찾아 이벤트를 발생 시킨다.
        log.info("Emitter 조회 시도 - key: {}", username);
        emitterRepository.get(username).ifPresentOrElse(sseEmitter -> {
            try {
                sseEmitter.send(SseEmitter.event().name(eventName).data(data));
                log.info("Emitter 찾음 - hash: {}", System.identityHashCode(sseEmitter));
            } catch (IOException exception) {
                // IOException이 발생하면 저장된 SseEmitter를 삭제하고 예외를 발생시킨다.
                emitterRepository.delete(username);
                throw new Error("Failed to Connect SSE");
            }
        }, () -> log.info("Emitter 없음 - {}", username));
    }
}
