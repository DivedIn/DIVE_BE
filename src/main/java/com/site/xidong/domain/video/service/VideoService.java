package com.site.xidong.domain.video.service;

import com.site.xidong.domain.feedback.dto.FeedbackReturnDTO;
import com.site.xidong.domain.feedback.entity.Feedback;
import com.site.xidong.domain.feedback.service.FeedbackService;
import com.site.xidong.domain.question.exception.QuestionNotFoundException;
import com.site.xidong.domain.queue.entity.VideoProcessingQueue;
import com.site.xidong.domain.queue.repository.VideoProcessingQueueRepository;
import com.site.xidong.domain.video.dto.VideoReturnDTO;
import com.site.xidong.domain.video.dto.VideoWithFeedbackDTO;
import com.site.xidong.domain.video.entity.Video;
import com.site.xidong.domain.video.repository.VideoRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import com.site.xidong.global.filter.TraceIdFilter;
import com.site.xidong.global.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VideoService {

    private static final int CORE_POOL_SIZE = 10;
    private static final int QUEUE_CAPACITY = 50;
    private static final double CAPACITY_THRESHOLD = 0.8;

    private final VideoRepository videoRepository;
    private final FeedbackService feedbackService;
    private final VideoProcessingQueueRepository queueRepository;
    private final VideoProcessingService videoProcessingService;
    private final VideoVisibilityTxService videoVisibilityTxService;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor videoProcessingExecutor;

    public CompletableFuture<Void> createInitial(
            String username, Long questionId, int requestNo,
            String videoKey, Boolean isOpen, long startTime) {
        return videoProcessingService.createInitial(username, questionId, requestNo, videoKey, isOpen, startTime);
    }

    public VideoWithFeedbackDTO getVideoWithFeedback(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(QuestionNotFoundException::new);
        return toVideoWithFeedback(video);
    }

    // [N+1] fetch join으로 siteUser/question/question.questionSet을 한 쿼리에 실어 오므로
    // size건을 반환하는 데 드는 쿼리는 요청당 1개로 고정된다.
    // [페이지네이션] size+1건을 가져와 CursorPageResponse.of()에 그대로 넘기면
    // count 쿼리 없이 hasNext/nextCursor가 계산된다.
    public CursorPageResponse<VideoReturnDTO> getOpenVideos(Long cursor, int size) {
        List<VideoReturnDTO> overFetched = videoRepository
                .findOpenVideosAfterCursor(cursor, PageRequest.of(0, size + 1)).stream()
                .map(VideoReturnDTO::from)
                .toList();
        return CursorPageResponse.of(overFetched, size, VideoReturnDTO::getVideoId);
    }

    public CursorPageResponse<VideoReturnDTO> getMyVideos(String username, Long cursor, int size) {
        List<VideoReturnDTO> overFetched = videoRepository
                .findMyVideosAfterCursor(username, cursor, PageRequest.of(0, size + 1)).stream()
                .map(VideoReturnDTO::from)
                .toList();
        return CursorPageResponse.of(overFetched, size, VideoReturnDTO::getVideoId);
    }

    // [낙관적 락] 큐 클레임(claimPendingTasks)은 SELECT ... FOR UPDATE로 미리 락을 걸고
    // 경쟁자를 기다리게 하는 비관적 락이었다 — 큐는 계속 여러 워커가 같은 후보 행을 노리는,
    // 충돌이 "흔한" 자원이라 그게 맞다. 반대로 영상 공개 여부 토글은 같은 영상을 같은 순간에
    // 두 곳에서 건드리는 일이 "가끔"만 벌어지는 자원이라, 평소엔 락 비용을 전혀 안 들이고
    // 버전이 실제로 어긋난 그 순간에만(ObjectOptimisticLockingFailureException) 값을 치르는
    // 낙관적 락이 더 싸다. 1회 재시도해도 또 부딪히면 409로 클라이언트에게 넘긴다.
    public VideoReturnDTO changeVisibility(Long videoId, String username, Boolean isOpen) {
        try {
            return videoVisibilityTxService.attempt(videoId, username, isOpen);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("낙관적 락 충돌, 1회 재시도: videoId={}", videoId);
            try {
                return videoVisibilityTxService.attempt(videoId, username, isOpen);
            } catch (ObjectOptimisticLockingFailureException e2) {
                log.error("낙관적 락 재시도도 충돌: videoId={}", videoId);
                throw new CustomException(ErrorCode.VIDEO_UPDATE_CONFLICT);
            }
        }
    }

    @Transactional
    public void deleteVideo(Long videoId, String username) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new CustomException(ErrorCode.VIDEO_NOT_FOUND));
        if (!video.getSiteUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        videoRepository.delete(video);
    }

    @Transactional
    public Long enqueue(String username, Long questionId, int requestNo, String videoKey, Boolean isOpen, long startTime) {
        // [관측 가능성] 지금(HTTP 요청 스레드)의 traceId를 큐 행에 실어둔다 — 실제 처리는
        // 나중에 스케줄러 tick에서 전혀 다른 스레드가 집어가므로, 여기서 안 실어두면 유실된다.
        VideoProcessingQueue request = VideoProcessingQueue.builder()
                .questionId(questionId)
                .requestNo(requestNo)
                .videoKey(videoKey)
                .isOpen(isOpen)
                .username(username)
                .startTime(startTime)
                .usePresignedUrl(true)
                .traceId(MDC.get(TraceIdFilter.TRACE_ID_KEY))
                .build();
        VideoProcessingQueue saved = queueRepository.save(request);
        log.info("DB 큐 저장 완료: queueId={}, videoKey={}, 대기: {}개",
                saved.getId(), videoKey, queueRepository.countPendingTasks());
        return saved.getId();
    }

    public boolean checkThreadPoolCapacity() {
        ThreadPoolExecutor executor = videoProcessingExecutor.getThreadPoolExecutor();
        int totalLoad = executor.getActiveCount() + executor.getQueue().size();
        int maxCapacity = CORE_POOL_SIZE + QUEUE_CAPACITY;
        double loadRatio = (double) totalLoad / maxCapacity;
        boolean hasCapacity = loadRatio < CAPACITY_THRESHOLD;
        if (!hasCapacity) {
            log.warn("스레드풀 용량 부족: {}/{} (임계값: {}%)",
                    totalLoad, maxCapacity, (int) (CAPACITY_THRESHOLD * 100));
        }
        return hasCapacity;
    }

    private VideoWithFeedbackDTO toVideoWithFeedback(Video video) {
        VideoReturnDTO videoDTO = VideoReturnDTO.from(video);
        if (video.getFeedback() == null) {
            return VideoWithFeedbackDTO.builder().video(videoDTO).feedback(null).build();
        }
        Feedback feedback = feedbackService.findFeedback(video.getFeedback().getId());
        return VideoWithFeedbackDTO.builder()
                .video(videoDTO)
                .feedback(FeedbackReturnDTO.from(feedback))
                .build();
    }
}
