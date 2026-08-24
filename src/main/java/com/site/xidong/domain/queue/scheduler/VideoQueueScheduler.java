package com.site.xidong.domain.queue.scheduler;

import com.site.xidong.domain.queue.entity.VideoProcessingQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoQueueScheduler {

    // [Reaper] PROCESSING 상태로 이 시간을 넘기면 워커가 죽은 좀비 작업으로 간주해 회수한다.
    private static final long STUCK_THRESHOLD_MINUTES = 5;

    private final VideoQueueProcessor queueProcessor;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor videoProcessingExecutor;

    @Scheduled(fixedDelay = 1000)
    public void processQueuedTasks() {
        try {
            int availableThreads = videoProcessingExecutor.getMaxPoolSize()
                    - videoProcessingExecutor.getActiveCount();
            if (availableThreads <= 0) {
                log.debug("스레드풀 포화 상태, 다음 주기 대기");
                return;
            }

            List<VideoProcessingQueue> claimedTasks = queueProcessor.claimPendingTasks(availableThreads);
            if (claimedTasks.isEmpty()) return;

            log.info("DB 큐 처리 시작: {}개 작업 확보(claim)", claimedTasks.size());
            for (VideoProcessingQueue task : claimedTasks) {
                queueProcessor.dispatchTask(task);
            }
        } catch (Exception e) {
            log.error("스케줄러 실행 오류", e);
        }
    }

    // [Reaper] 서버/스레드가 죽어 PROCESSING에 멈춘 채 남은 작업을 주기적으로 회수해
    // 재시도(PENDING) 또는 DLQ(FAILED)로 되돌린다.
    @Scheduled(fixedDelay = 30000)
    public void reapStuckTasks() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
            queueProcessor.reapStuckTasks(threshold);
        } catch (Exception e) {
            log.error("Reaper 실행 오류", e);
        }
    }
}
