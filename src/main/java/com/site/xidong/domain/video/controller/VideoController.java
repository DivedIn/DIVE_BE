package com.site.xidong.domain.video.controller;

import com.site.xidong.domain.video.dto.VideoReturnDTO;
import com.site.xidong.domain.video.dto.VideoUploadCompleteRequest;
import com.site.xidong.domain.video.dto.VideoWithFeedbackDTO;
import com.site.xidong.domain.video.service.VideoService;
import com.site.xidong.global.filter.TraceIdFilter;
import com.site.xidong.global.infra.S3Uploader;
import com.site.xidong.global.response.ApiResponse;
import com.site.xidong.global.response.CursorPageResponse;
import io.micrometer.core.annotation.Timed;
import lombok.extern.log4j.Log4j2;
import org.joda.time.DateTime;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@Log4j2
@RequestMapping("/video")
public class VideoController {

    private final S3Uploader s3Uploader;
    private final VideoService videoService;
    private final ThreadPoolTaskExecutor executor;
    @Value("${db.queue.enabled}")
    private boolean useDbQueue;

    public VideoController(S3Uploader s3Uploader, VideoService videoService,
                           @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.s3Uploader = s3Uploader;
        this.videoService = videoService;
        this.executor = executor;
    }

    @GetMapping("/presigned")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPresignedUrl(@RequestParam Long questionId) {
        String fileName = DateTime.now() + "_video.webm";
        Map<String, String> response = s3Uploader.generatePresignedUrl(fileName);
        response.put("videoKey", fileName);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/complete-upload")
    @Timed
    public ResponseEntity<ApiResponse<Map<String, String>>> completeUpload(
            @AuthenticationPrincipal UserDetails ud,
            @RequestBody VideoUploadCompleteRequest request) {

        long startTime = System.currentTimeMillis();

        if (!useDbQueue) {
            CompletableFuture<Void> result = videoService.createInitial(
                    ud.getUsername(), request.getQuestionId(), request.getRequestNo(),
                    request.getVideoKey(), false, startTime);
            return ResponseEntity.accepted().body(ApiResponse.success(Map.of("mode", "일반 모드")));
        }

        log.warn("DB 큐에 요청 저장");
        Long queueId = videoService.enqueue(
                ud.getUsername(), request.getQuestionId(), request.getRequestNo(),
                request.getVideoKey(), request.isOpen(), startTime);

        // [관측 가능성] queueId만으론 나중에 SSE로 오는 video-processed 이벤트와 이어줄 수
        // 없었다(그 이벤트엔 videoId만 있음) — 같은 traceId를 여기와 SSE 페이로드 양쪽에
        // 실어서, 클라이언트가 "이 202 응답이 결국 어떤 알림으로 이어졌는지"를 맞춰볼 수 있게 한다.
        return ResponseEntity.accepted().body(ApiResponse.success(Map.of(
                "status", "queued",
                "queueId", queueId.toString(),
                "traceId", String.valueOf(MDC.get(TraceIdFilter.TRACE_ID_KEY)),
                "message", "요청이 메시지 큐에 추가되었습니다"
        )));
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<VideoWithFeedbackDTO>> getVideo(@PathVariable Long videoId) {
        return ResponseEntity.ok(ApiResponse.success(videoService.getVideoWithFeedback(videoId)));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<CursorPageResponse<VideoReturnDTO>>> getOpenVideos(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(videoService.getOpenVideos(cursor, size)));
    }

    @GetMapping("/myVideos")
    public ResponseEntity<ApiResponse<CursorPageResponse<VideoReturnDTO>>> getMyVideos(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(videoService.getMyVideos(ud.getUsername(), cursor, size)));
    }

    @PutMapping("/{videoId}/change/visibility")
    public ResponseEntity<Void> change(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long videoId,
            @RequestBody Boolean isOpen) {
        videoService.changeVisibility(videoId, ud.getUsername(), isOpen);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{videoId}/delete")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long videoId) {
        videoService.deleteVideo(videoId, ud.getUsername());
        return ResponseEntity.noContent().build();
    }
}
