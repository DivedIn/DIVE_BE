package com.site.xidong.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class MockFeedbackService {

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor videoProcessingExecutor;

    private final AtomicInteger requestCounter = new AtomicInteger(0);

    @Async("videoProcessingExecutor")
    public void generateMockFeedback() {
        int reqNum = requestCounter.incrementAndGet();

        log.info("[요청 {}] 시작 - 활성: {}/{}, 큐: {}/{}, 스레드: {}", reqNum, videoProcessingExecutor.getActiveCount(),
                videoProcessingExecutor.getPoolSize(), videoProcessingExecutor.getQueueSize(),
                videoProcessingExecutor.getQueueCapacity(), Thread.currentThread().getName());

        try {
            Thread.sleep(120_000);
            log.info("[요청 {}] 완료", reqNum);
        } catch (InterruptedException e) {
            log.error("[요청 {}] 중단됨", reqNum);
            Thread.currentThread().interrupt();
        }
    }

    public void reset() {
        requestCounter.set(0);
    }
}
