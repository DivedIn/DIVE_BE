package com.site.xidong.domain.video.service;

import com.site.xidong.domain.feedback.entity.Feedback;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * [커넥션 점유 개선] VideoProcessingService에서 "DB에 실제로 쓰는" 짧은 구간만 떼어낸 빈.
 *
 * VideoProcessingService(오케스트레이션: STT/썸네일/Claude 호출 순서 결정)와
 * 이 클래스(영속성: 언제 커밋할지)를 물리적으로 다른 빈으로 분리했다.
 * self-injection(@Lazy self)으로도 같은 효과를 낼 수 있지만, 그건 같은 클래스 안에서
 * this.xxx() 대신 self.xxx()를 쓰도록 사람이 계속 신경 써야 하는 규칙이라 재발 위험이 있다.
 * 진짜 다른 빈으로 나누면 VideoProcessingService가 이 빈의 메서드를 호출하는 순간
 * "무조건" 프록시를 거치므로, @Transactional이 실수로 무력화될 방법 자체가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingTxService {

    private final VideoRepository videoRepository;
    private final SiteUserRepository siteUserRepository;
    private final QuestionRepository questionRepository;
    private final NotificationService notificationService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long saveInitialVideo(String username, Long questionId, String videoKey, Boolean isOpen) {
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
        return saved.getId();
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
    public void completeVideoWithFeedback(Long videoId, String username, Feedback feedback, Long feedbackId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found: " + videoId));

        video.updateStatus("COMPLETED");
        video.linkFeedback(feedback);

        notificationService.send(username, "video-processed",
                VideoNotificationDTO.builder()
                        .videoId(videoId).status("COMPLETED")
                        .message("비디오 처리가 완료되었습니다.")
                        .feedbackId(feedbackId)
                        .build());
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
}
