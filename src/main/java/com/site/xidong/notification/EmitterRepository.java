package com.site.xidong.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
@Component
public class EmitterRepository {
    // username을 키로 SseEmitter를 해시맵에 저장
    private Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter save(String username, SseEmitter sseEmitter) {
        emitterMap.put(getKey(username), sseEmitter);
        log.info("Saved SseEmitter for {}", username);
        return sseEmitter;
    }

    public Optional<SseEmitter> get(String username) {
        log.info("Got SseEmitter for {}", username);
        return Optional.ofNullable(emitterMap.get(getKey(username)));
    }

    public void delete(String username) {
        emitterMap.remove(getKey(username));
        log.info("Deleted SseEmitter for {}", username);
    }

    private String getKey(String username) {
        return "Emitter:username:" + username;
    }
}
