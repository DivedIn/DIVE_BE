package com.site.xidong.domain.video.service;

import com.site.xidong.domain.feedback.dto.AnswerDTO;
import com.site.xidong.domain.feedback.dto.FeedbackReturnDTO;
import com.site.xidong.domain.feedback.entity.Feedback;
import com.site.xidong.domain.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 영상 처리 오케스트레이션 전담. DB 저장이 필요한 지점은 전부 {@link VideoProcessingTxService}
 * (별도 빈)에 위임한다 — 이 클래스 안에는 @Transactional이 하나도 없다.
 *
 * [커넥션 점유 개선] STT/썸네일/Claude mock 같은 외부 I/O는 DB 커넥션을 물 이유가 없는데,
 * 예전엔 같은 클래스 안에서 트랜잭션 메서드를 this.xxx()로 직접 호출(self-invocation)하는 바람에
 * @Transactional이 프록시를 못 타고 무력화되어, 상위에서 열어둔 트랜잭션 하나가 이 메서드들이
 * 감싸고 있던 20초 넘는 sleep 구간까지 통째로 깔고 앉아 있었다. 저장 로직을 물리적으로 다른 빈
 * (VideoProcessingTxService)으로 옮기면, 그 빈을 호출하는 순간 항상 실제 프록시를 거치므로
 * 이 문제가 애초에 발생할 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {

    private final SttService sttService;
    private final ThumbnailService thumbnailService;
    private final FeedbackService feedbackService;
    private final VideoProcessingTxService txService;

    @Async("videoProcessingExecutor")
    public CompletableFuture<Void> createInitial(
            String username, Long questionId, int requestNo,
            String videoKey, Boolean isOpen, long startTime) {
        try {
            Long videoId = txService.saveInitialVideo(username, questionId, videoKey, isOpen);

            // processVideo()는 트랜잭션 밖에서 실행된다: STT/Claude mock 대기(외부 I/O) 동안 커넥션을 물지 않는다.
            processVideo(videoId, requestNo, videoKey, username, startTime);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("비디오 초기 처리 실패", e);
            throw new RuntimeException("비디오 초기 처리에 실패했습니다", e);
        }
    }

    // 트랜잭션 없음. STT/썸네일/Claude 등 외부 I/O를 실행하고,
    // 실제 DB 저장이 필요한 지점만 txService(다른 빈)에 위임해 짧은 트랜잭션으로 처리한다.
    public void processVideo(Long videoId, int requestNo, String videoKey, String username, long startTime) {
        long procStart = System.currentTimeMillis();
        try {
            double durationSeconds = sttService.getVideoDuration(videoKey);
            boolean isLongVideo = durationSeconds > 300;

            String thumbnailUrl = thumbnailService.generate(videoKey);
            txService.updateVideoThumbnailAndStatus(videoId, thumbnailUrl);

            String answer = isLongVideo
                    ? sttService.transcribeLongVideo(videoKey, durationSeconds)
                    : sttService.transcribeShortVideo(videoKey);

            log.info("STT 완료: videoId={}, 길이={}, 소요={}ms",
                    videoId, answer.length(), System.currentTimeMillis() - procStart);

            if (!isValidAnswer(answer)) {
                log.warn("유효한 답변 없음: videoId={}", videoId);
                txService.handleInvalidAnswer(videoId, username, answer);
                return;
            }

            handleValidAnswer(videoId, username, answer);

            log.info("비디오 처리 완료: videoId={}, requestNo={}, 총 소요={}ms",
                    videoId, requestNo, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("비디오 비동기 처리 중 오류: videoId={}", videoId, e);
            txService.handleError(videoId, username);
        }
    }

    // 트랜잭션 없음. feedbackService.getFeedback() 안에서 Claude 응답 대기(mock sleep)가 일어나므로
    // 이 메서드가 커넥션을 물고 있으면 안 된다. DB 반영은 txService.completeVideoWithFeedback()에서 짧게 처리한다.
    public void handleValidAnswer(Long videoId, String username, String answer) {
        long start = System.currentTimeMillis();

        FeedbackReturnDTO feedbackDTO = feedbackService.getFeedback(
                AnswerDTO.builder().videoId(videoId).answer(answer).build());
        Feedback feedback = feedbackService.findFeedback(feedbackDTO.getFeedbackId());

        txService.completeVideoWithFeedback(videoId, username, feedback, feedbackDTO.getFeedbackId());

        log.info("피드백 생성 완료: videoId={}, 소요={}ms", videoId, System.currentTimeMillis() - start);
    }

    private boolean isValidAnswer(String answer) {
        return answer != null
                && !answer.trim().isEmpty()
                && answer.trim().length() >= 10
                && !answer.trim().matches("(?i).*\\b(background noise|unintelligible|inaudible)\\b.*");
    }
}
