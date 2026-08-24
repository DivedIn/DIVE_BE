package com.site.xidong.domain.feedback.service;

import com.site.xidong.domain.feedback.dto.AnswerDTO;
import com.site.xidong.domain.feedback.dto.FeedbackReturnDTO;
import com.site.xidong.domain.feedback.entity.Feedback;
import com.site.xidong.domain.feedback.repository.FeedbackRepository;
import com.site.xidong.domain.video.entity.Video;
import com.site.xidong.domain.video.repository.VideoRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final VideoRepository videoRepository;
    private final FeedbackRepository feedbackRepository;
    private final ClaudeApiClient claudeApiClient;

    @Value("${claude.mock.enabled}")
    private boolean mockEnabled;

    @Value("${whisper.mock.delay-ms:20000}")
    private long mockDelayMs;

    // [커넥션 점유 개선] 의도적으로 @Transactional을 달지 않는다.
    // Claude 응답 대기(mock sleep 또는 실제 API 호출)는 DB I/O가 아니므로 커넥션을 물고 있으면 안 된다.
    // 아래 두 리포지토리 호출은 각각 Spring Data JPA가 자동으로 짧은 트랜잭션을 열고/닫아 처리한다.
    public FeedbackReturnDTO getFeedback(AnswerDTO answerDTO) {
        Video video = videoRepository.findByIdWithQuestion(answerDTO.getVideoId())
                .orElseThrow(() -> new CustomException(ErrorCode.VIDEO_NOT_FOUND));

        String feedbackText = mockEnabled
                ? getMockFeedbackText(video.getQuestion().getContents(), mockDelayMs)
                : claudeApiClient.requestFeedback(video.getQuestion().getContents(), answerDTO.getAnswer());

        Feedback feedback = feedbackRepository.save(
                Feedback.builder().contents(feedbackText).video(video).build()
        );
        return FeedbackReturnDTO.from(feedback);
    }

    public Feedback findFeedback(Long feedbackId) {
        return feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private String getMockFeedbackText(String question, long delayMs) {
        log.info("부하테스트 모드: Claude API 호출 생략 ({}ms 대기)", delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return String.format("""
                ### 내용 측면 피드백

                **강점:**
                - 질문 "%s"에 대한 핵심 개념을 정확히 이해하고 있습니다.
                - 실무 경험을 바탕으로 한 구체적인 예시가 답변의 신뢰도를 높였습니다.

                **개선점:**
                - 좀 더 구조화된 답변으로 전개하면 더 명확할 것입니다.

                ### 전달력 측면 피드백

                **강점:**
                - 논리적인 흐름으로 청자의 이해를 돕는 구조입니다.

                **개선점:**
                - 중요한 포인트에서 강조나 휴지(pause)를 활용하면 더 효과적입니다.

                **종합 평가:** 면접 답변으로서 우수한 수준입니다.
                """, question);
    }
}
