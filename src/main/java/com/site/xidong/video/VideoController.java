package com.site.xidong.video;

import com.site.xidong.utils.S3Uploader;
import io.micrometer.core.annotation.Timed;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@Log4j2
@RequestMapping("/video")
public class VideoController {

    private final S3Uploader s3Uploader;
    private final VideoService videoService;
    private final ThreadPoolTaskExecutor executor;

    @Value("${video.processing.use-presigned-url}")
    private boolean usePresignedUrl;

    @Value("${video.processing.async-enabled}")
    private boolean asyncEnabled;

    public VideoController(S3Uploader s3Uploader, VideoService videoService, @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.s3Uploader = s3Uploader;
        this.videoService = videoService;
        this.executor = executor;
    }

    @GetMapping("/presigned")
    public ResponseEntity<Map<String, String>> getPresignedUrl(
            @RequestParam Long questionId) {
        try {
            String fileName = DateTime.now() + "_video.webm";
            Map<String, String> response = s3Uploader.generatePresignedUrl(fileName);
            response.put("videoKey", fileName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate presigned URL", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/complete-upload")
    @Timed
    public ResponseEntity<Map<String, String>> completeUpload(
            @RequestBody VideoUploadCompleteRequest request) {

        long startTime = System.currentTimeMillis();
        log.info("요청 접수: [{}], Mode: {}, Async: {}, Tomcat Thread: {}",
                request, usePresignedUrl ? "PRESIGNED" : "FILE_BASED", asyncEnabled, Thread.currentThread().getName());

        try {
            if (!asyncEnabled) {
                // 동기 방식 (기존 로직 유지)
                VideoReturnDTO videoReturnDTO = videoService.createInitialSync(
                        request.getQuestionId(),
                        request.getVideoKey(),
                        request.isOpen(),
                        startTime
                );
                return ResponseEntity.ok(Map.of("status", "completed"));
            }

            // === 옵션 2: 하이브리드 방식 ===

            // Step 1: 스레드풀 용량 확인
            boolean hasCapacity = videoService.checkThreadPoolCapacity();

            if (hasCapacity) {
                // Fast Path: 즉시 처리 (실험의 1~10번)
                log.info("Fast Path: 즉시 처리 시작 - videoKey: {}", request.getVideoKey());

                CompletableFuture<Void> future = videoService.createInitialAsync(
                        request.getQuestionId(),
                        request.getVideoKey(),
                        request.isOpen(),
                        startTime,
                        usePresignedUrl
                );

                future.exceptionally(throwable -> {
                    log.error("비디오 처리 실패: questionId={}, videoKey={}",
                            request.getQuestionId(), request.getVideoKey(), throwable);
                    return null;
                });

                return ResponseEntity.accepted()
                        .body(Map.of(
                                "status", "processing",
                                "path", "fast",
                                "message", "즉시 처리 중입니다"
                        ));

            } else {
                // Slow Path: DB 큐에 저장 (실험의 61~70번)
                log.warn("Slow Path: DB 큐 저장 - videoKey: {} (스레드풀 포화)", request.getVideoKey());

                Long queueId = videoService.enqueue(
                        request.getQuestionId(),
                        request.getVideoKey(),
                        request.isOpen(),
                        startTime,
                        usePresignedUrl
                );

                return ResponseEntity.accepted()
                        .body(Map.of(
                                "status", "queued",
                                "path", "slow",
                                "queueId", queueId.toString(),
                                "message", "대기열에 추가되었습니다"
                        ));
            }

        } catch (Exception e) {
            log.error("비디오 초기 처리 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("Tomcat 요청 처리 완료: 처리 시간: {}ms, 활성: {}, 큐: {}",
                    duration, executor.getActiveCount(), executor.getQueueSize());
        }
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<VideoWithFeedbackDTO> getVideo(@PathVariable Long videoId) {
        VideoWithFeedbackDTO videoWithFeedbackDTO;
        try {
            videoWithFeedbackDTO = videoService.getVideoWithFeedback(videoId);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.status(HttpStatus.OK).body(videoWithFeedbackDTO);
    }

    @GetMapping("/all")
    public ResponseEntity<List<VideoReturnDTO>> getOpenVideos() {
        List<VideoReturnDTO> videoReturnDTOs = videoService.getOpenVideos();
        return ResponseEntity.status(HttpStatus.OK).body(videoReturnDTOs);
    }

    @GetMapping("/myVideos")
    public ResponseEntity<List<VideoReturnDTO>> getMyVideos() {
        List<VideoReturnDTO> videoReturnDTOs = videoService.getMyVideos();
        return ResponseEntity.status(HttpStatus.OK).body(videoReturnDTOs);
    }

    @PutMapping("/{videoId}/change/visibility")
    public ResponseEntity<?> change(@PathVariable Long videoId, @RequestBody Boolean isOpen) throws Exception {
        try {
            videoService.changeVisibility(videoId, isOpen);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/{videoId}/delete")
    public ResponseEntity<?> delete(@PathVariable Long videoId) throws Exception {
        try {
            videoService.deleteVideo(videoId);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}