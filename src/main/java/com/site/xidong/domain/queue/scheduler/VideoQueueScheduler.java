package com.site.xidong.domain.queue.scheduler;

import com.site.xidong.domain.queue.entity.VideoProcessingQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoQueueScheduler {

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
}
