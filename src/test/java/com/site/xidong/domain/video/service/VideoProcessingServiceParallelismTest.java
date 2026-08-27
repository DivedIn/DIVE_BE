package com.site.xidong.domain.video.service;

import com.site.xidong.domain.feedback.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [병렬화] duration 확인과 썸네일 생성이 실제로 동시에 실행되는지, 총 소요시간을 재서 검증한다.
 * 직렬이었다면 두 지연시간의 합만큼 걸려야 하고, 병렬이면 더 느린 쪽 시간에 수렴해야 한다.
 */
class VideoProcessingServiceParallelismTest {

    @Test
    void duration확인과_썸네일생성은_직렬합산이_아니라_더_오래걸리는_쪽_시간만큼_걸린다() {
        SttService sttService = mock(SttService.class);
        ThumbnailService thumbnailService = mock(ThumbnailService.class);
        FeedbackService feedbackService = mock(FeedbackService.class);
        VideoProcessingTxService txService = mock(VideoProcessingTxService.class);

        long durationDelayMs = 300;
        long thumbnailDelayMs = 600;

        when(sttService.getVideoDuration(anyString())).thenAnswer(inv -> {
            Thread.sleep(durationDelayMs);
            return 120.0; // isLongVideo = false
        });
        when(thumbnailService.generate(anyString())).thenAnswer(inv -> {
            Thread.sleep(thumbnailDelayMs);
            return "https://example.com/thumb.jpg";
        });
        // 빈 답변 → isValidAnswer() = false → handleInvalidAnswer로 바로 종료 (다운스트림 경로를 최소화해
        // 병렬화 구간의 시간만 순수하게 측정하기 위함)
        when(sttService.transcribeShortVideo(anyString())).thenReturn("");

        VideoProcessingService service =
                new VideoProcessingService(sttService, thumbnailService, feedbackService, txService);
        ThreadPoolTaskExecutor fanOutExecutor = new ThreadPoolTaskExecutor();
        fanOutExecutor.setCorePoolSize(4);
        fanOutExecutor.setMaxPoolSize(4);
        fanOutExecutor.initialize();
        ReflectionTestUtils.setField(service, "fanOutExecutor", fanOutExecutor);

        long start = System.currentTimeMillis();
        service.processVideo(1L, 1, "video.webm", "user", start);
        long elapsed = System.currentTimeMillis() - start;

        // 직렬이었다면 900ms(300+600) 이상. 병렬이면 더 느린 쪽인 600ms 근처에서 끝나야 한다.
        assertThat(elapsed).isLessThan(durationDelayMs + thumbnailDelayMs - 100);
        assertThat(elapsed).isGreaterThanOrEqualTo(thumbnailDelayMs);

        verify(txService).updateVideoThumbnailAndStatus(eq(1L), eq("https://example.com/thumb.jpg"));
        verify(txService).handleInvalidAnswer(eq(1L), eq("user"), eq(""));

        fanOutExecutor.shutdown();
    }
}
