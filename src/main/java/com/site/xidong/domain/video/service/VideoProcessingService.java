package com.site.xidong.domain.video.service;

import com.site.xidong.domain.feedback.dto.AnswerDTO;
import com.site.xidong.domain.feedback.dto.FeedbackReturnDTO;
import com.site.xidong.domain.feedback.entity.Feedback;
import com.site.xidong.domain.feedback.service.FeedbackService;
import com.site.xidong.domain.notification.dto.VideoNotificationDTO;
import com.site.xidong.domain.notification.service.NotificationService;
import com.site.xidong.domain.question.entity.Question;
import com.site.xidong.domain.question.exception.QuestionNotFoundException;
import com.site.xidong.domain.question.repository.QuestionRepository;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.user.repository.SiteUserRepository;
import com.site.xidong.domain.video.entity.Video;
import com.site.xidong.domain.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {

    private final VideoRepository videoRepository;
    private final SiteUserRepository siteUserRepository;
    private final QuestionRepository questionRepository;
    private final ThumbnailService thumbnailService;
    private final SttService sttService;
    private final FeedbackService feedbackService;
    private final NotificationService notificationService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Async("videoProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> createInitial(
            String username, Long questionId, int requestNo,
            String videoKey, Boolean isOpen, long startTime) {
        try {
            SiteUser user = siteUserRepository.findSiteUserByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + username));
            Question question = questionRepository.findById(questionId)
                    .orElseThrow(QuestionNotFoundException::new);

            String videoUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, videoKey);
            Video video = Video.builder()
                    .videoPath(videoUrl)
                    .videoName(videoKey)
                    .siteUser(user)
                    .question(question)
                    .isOpen(isOpen)
                    .processingStatus("PROCESSING")
                    .build();
            Video saved = videoRepository.save(video);
            log.info("비디오 초기 저장 완료: ID={}", saved.getId());

            processVideo(saved.getId(), requestNo, videoKey, username, startTime);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("비디오 초기 처리 실패", e);
            throw new RuntimeException("비디오 초기 처리에 실패했습니다", e);
        }
    }

    // Direct call is fine: @Transactional(REQUIRED) joins the REQUIRES_NEW tx already in ThreadLocal.
    @Transactional(propagation = Propagation.REQUIRED)
    public void processVideo(Long videoId, int requestNo, String videoKey, String username, long startTime) {
        long procStart = System.currentTimeMillis();
        try {
            double durationSeconds = sttService.getVideoDuration(videoKey);
            boolean isLongVideo = durationSeconds > 300;

            String thumbnailUrl = thumbnailService.generate(videoKey);
            updateVideoThumbnailAndStatus(videoId, thumbnailUrl);

            String answer = isLongVideo
                    ? sttService.transcribeLongVideo(videoKey, durationSeconds)
                    : sttService.transcribeShortVideo(videoKey);

            log.info("STT 완료: videoId={}, 길이={}, 소요={}ms",
                    videoId, answer.length(), System.currentTimeMillis() - procStart);

            if (!isValidAnswer(answer)) {
                log.warn("유효한 답변 없음: videoId={}", videoId);
                handleInvalidAnswer(videoId, username, answer);
                return;
            }

            handleValidAnswer(videoId, username, answer);

            log.info("비디오 처리 완료: videoId={}, requestNo={}, 총 소요={}ms",
                    videoId, requestNo, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("비디오 비동기 처리 중 오류: videoId={}", videoId, e);
            handleError(videoId, username);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateVideoThumbnailAndStatus(Long videoId, String thumbnailUrl) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found: " + videoId));
        video.updateThumbnail(thumbnailUrl);
        video.updateStatus("TRANSCRIBING");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleInvalidAnswer(Long videoId, String username, String answer) {
        log.warn("유효한 답변 없음: videoId={}, answer='{}'", videoId, answer);
        videoRepository.findById(videoId).ifPresent(video -> {
            video.updateStatus("NO_RESPONSE");
            notificationService.send(username, "video-processed",
                    VideoNotificationDTO.builder()
                            .videoId(videoId).status("NO_RESPONSE")
                            .message("녹화된 답변이 감지되지 않았습니다. 다시 시도해주세요.")
                            .build());
        });
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleValidAnswer(Long videoId, String username, String answer) {
        long start = System.currentTimeMillis();
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found: " + videoId));

        FeedbackReturnDTO feedbackDTO = feedbackService.getFeedback(
                AnswerDTO.builder().videoId(videoId).answer(answer).build());
        Feedback feedback = feedbackService.findFeedback(feedbackDTO.getFeedbackId());

        video.updateStatus("COMPLETED");
        video.linkFeedback(feedback);

        notificationService.send(username, "video-processed",
                VideoNotificationDTO.builder()
                        .videoId(videoId).status("COMPLETED")
                        .message("비디오 처리가 완료되었습니다.")
                        .feedbackId(feedbackDTO.getFeedbackId())
                        .build());

        log.info("피드백 생성 완료: videoId={}, 소요={}ms", videoId, System.currentTimeMillis() - start);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleError(Long videoId, String username) {
        videoRepository.findById(videoId).ifPresent(video -> {
            video.updateStatus("ERROR");
            notificationService.send(username, "video-processed",
                    VideoNotificationDTO.builder()
                            .videoId(videoId).status("ERROR")
                            .message("비디오 처리 중 오류가 발생했습니다.")
                            .build());
        });
    }

    private boolean isValidAnswer(String answer) {
        return answer != null
                && !answer.trim().isEmpty()
                && answer.trim().length() >= 10
                && !answer.trim().matches("(?i).*\\b(background noise|unintelligible|inaudible)\\b.*");
    }
}
