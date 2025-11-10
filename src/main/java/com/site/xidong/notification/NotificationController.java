package com.site.xidong.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
@RestController
@RequestMapping("/notification")
@Slf4j
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/subscribe")
    public ResponseEntity<SseEmitter> subscribe() throws Exception {
        SseEmitter sseEmitter;
        sseEmitter = notificationService.connectNotification();
        return ResponseEntity.status(HttpStatus.OK).body(sseEmitter);
    }
}
