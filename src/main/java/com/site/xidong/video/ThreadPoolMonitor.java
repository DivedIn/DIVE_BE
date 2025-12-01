package com.site.xidong.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThreadPoolMonitor {

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor videoProcessingExecutor;

    private final SlackService slackService;

    public void logThreadPoolStatus(String context) {

        ThreadPoolExecutor executor = videoProcessingExecutor.getThreadPoolExecutor();

        int activeCount = executor.getActiveCount();
        int maxPoolSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();
        int queueCapacity = executor.getQueue().remainingCapacity() + queueSize;
        long completedTaskCount = executor.getCompletedTaskCount();
        long taskCount = executor.getTaskCount();

        // 큐 사용률 계산
        int queueUsagePercent = (queueCapacity > 0)
                ? (queueSize * 100 / queueCapacity) : 0;

        // 스레드 사용률 계산
        int threadUsagePercent = (maxPoolSize > 0)
                ? (activeCount * 100 / maxPoolSize) : 0;

        log.info("┌─ 스레드 풀 상태 [{}] ─────────────────────", context);
        log.info("│ 활성 스레드: {}/{} ({}%)", activeCount, maxPoolSize, threadUsagePercent);
        log.info("│ 대기 작업: {}/{} ({}%)", queueSize, queueCapacity, queueUsagePercent);
        log.info("│ 완료/총 작업: {}/{}", completedTaskCount, taskCount);

        if (queueUsagePercent >= 80) {
            slackService.sendMessage(
                    "[Alert] 영상 작업 Queue가 가득 차는 중!₩n" +
                    "현재 큐 사이즈: " + queueSize + "\n" +
                    "활성 스레드: " + activeCount + "/" + maxPoolSize
            );
        }

    }
}
