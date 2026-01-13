package com.site.xidong.mock;

import com.site.xidong.queue.VideoProcessingQueue;
import com.site.xidong.queue.VideoProcessingQueueRepository;
import com.site.xidong.video.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test")
@Slf4j
public class LoadTestController {
    private final VideoService videoService;
    private final VideoProcessingQueueRepository queueRepository;
    @Qualifier("threadPoolTaskExecutor")
    private final ThreadPoolTaskExecutor videoProcessingExecutor;
    private final MockFeedbackService mockFeedbackService;

    @PostMapping("/feedback")
    public ResponseEntity<?> mockFeedbackRequest() {
        try {
            mockFeedbackService.generateMockFeedback();
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        queueRepository.deleteAll();
        log.info("모든 큐 초기화 완료");
        return ResponseEntity.ok("Reset complete");
    }

    @PostMapping("/mock-video")
    public ResponseEntity<Map<String, Object>> mockVideoUpload(
            @RequestParam(defaultValue = "2") int processingMinutes) {

        long startTime = System.currentTimeMillis();

        // 스레드풀 용량 체크
        boolean hasCapacity = videoService.checkThreadPoolCapacity();

        if (hasCapacity) {
            // Fast Path
            log.info("Fast Path: Mock 작업 시작");

            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Mock 영상 처리 시작 - Thread: {}",
                            Thread.currentThread().getName());

                    // 실제 영상 처리 대신 Sleep
                    Thread.sleep(processingMinutes * 60 * 1000);

                    log.info("Mock 영상 처리 완료 - Thread: {}",
                            Thread.currentThread().getName());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, videoProcessingExecutor);

            return ResponseEntity.accepted().body(Map.of(
                    "status", "processing",
                    "path", "fast",
                    "message", "즉시 처리 중"
            ));

        } else {
            // Slow Path
            log.warn("Slow Path: DB 큐 저장");

            VideoProcessingQueue queueItem = VideoProcessingQueue.builder()
                    .questionId(999L)  // Mock
                    .videoKey("mock-" + System.currentTimeMillis() + ".webm")
                    .isOpen(true)
                    .startTime(startTime)
                    .usePresignedUrl(false)
                    .username("test-user")
                    .build();

            VideoProcessingQueue saved = queueRepository.save(queueItem);

            return ResponseEntity.accepted().body(Map.of(
                    "status", "queued",
                    "path", "slow",
                    "queueId", saved.getId(),
                    "message", "대기열에 추가됨"
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        ThreadPoolExecutor executor = videoProcessingExecutor.getThreadPoolExecutor();

        int activeCount = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        long completedTasks = executor.getCompletedTaskCount();
        long dbQueueCount = queueRepository.countByStatus(
                VideoProcessingQueue.QueueStatus.PENDING
        );

        Map<String, Object> status = Map.of(
                "threadPool", Map.of(
                        "queued", queueSize,
                        "completed", completedTasks,
                        "total", activeCount + queueSize,
                        "capacity", 60  // 10 + 50
                ),
                "dbQueue", Map.of(
                        "pending", dbQueueCount
                ),
                "timestamp", LocalDateTime.now()
        );

        return ResponseEntity.ok(status);
    }
}
