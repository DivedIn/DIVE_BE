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
        log.info("요청 접수: [{}], Tomcat Thread: {}",
                request, Thread.currentThread().getName());

        try {
            //DB 큐에 저장
            log.warn("DB 큐에 요청 저장");

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
                            "queueId", queueId.toString(),
                            "message", "요청이 메시지 큐에 추가되었습니다"
                    ));

        } catch (Exception e) {
            log.error("요청 처리 실패", e);
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