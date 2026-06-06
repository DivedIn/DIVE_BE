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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public List<VideoReturnDTO> getOpenVideos() {
        return videoRepository.findAllOpenVideos().stream()
                .map(VideoReturnDTO::from)
                .toList();
    }

    public List<VideoReturnDTO> getMyVideos(String username) {
        return videoRepository.findMyVideos(username).stream()
                .map(VideoReturnDTO::from)
                .toList();
    }

    @Transactional
    public VideoReturnDTO changeVisibility(Long videoId, String username, Boolean isOpen) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new CustomException(ErrorCode.VIDEO_NOT_FOUND));
        if (!video.getSiteUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        video.updateVisibility(isOpen);
        return VideoReturnDTO.from(videoRepository.save(video));
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
        VideoProcessingQueue request = VideoProcessingQueue.builder()
                .questionId(questionId)
                .requestNo(requestNo)
                .videoKey(videoKey)
                .isOpen(isOpen)
                .username(username)
                .startTime(startTime)
                .usePresignedUrl(true)
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
