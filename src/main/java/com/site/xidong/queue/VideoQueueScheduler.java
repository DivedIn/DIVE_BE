package com.site.xidong.queue;

import com.site.xidong.video.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoQueueScheduler {
    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor videoProcessingExecutor;

    private final VideoProcessingQueueRepository queueRepository;
    private final VideoService videoService;

    @Scheduled(fixedDelay = 2000)
    public void processQueuedTasks() {
        try {
            // 사용 가능한 스레드 수 계산
            int availableThreads = videoProcessingExecutor.getMaxPoolSize()
                    - videoProcessingExecutor.getActiveCount();

            if (availableThreads <= 0) {
                log.info("스레드풀 포화 상태, 다음 주기 대기");
                return;
            }

            // 사용 가능한 스레드 수만큼만 조
            List<VideoProcessingQueue> pendingTasks = queueRepository.findPendingTasks(
                    PageRequest.of(0, availableThreads)
            );

            if (pendingTasks.isEmpty()) return;

            log.info("DB 큐 처리 시작: {}개 작업 발견", pendingTasks.size());

            for (VideoProcessingQueue task : pendingTasks) {
                processTask(task);
            }
        } catch (Exception e) {
            log.error("스케줄러 실행 오류", e);
        }
    }

    @Transactional
    public void processTask(VideoProcessingQueue task) {
        try {
            // 상태 변경
            task.markProcessing();
            queueRepository.save(task);

            // 비동기 작업 제출
            CompletableFuture<Void> future = videoService.createInitial(
                    task.getQuestionId(),
                    task.getVideoKey(),
                    task.getIsOpen(),
                    task.getStartTime()
            );

            // 완료 핸들러는 별도 트랜잭션
            future.whenComplete((result, throwable) ->
                    updateTaskStatus(task.getId(), throwable)
            );

            log.info("작업 제출 완료: queueId={}", task.getId());

        } catch (Exception e) {
            log.error("작업 제출 실패: queueId = {}", task.getId(), e);
            task.markFailed();
            queueRepository.save(task);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // 새 트랜잭션
    public void updateTaskStatus(Long taskId, Throwable throwable) {
        VideoProcessingQueue task = queueRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("작업을 찾을 수 없습니다: " + taskId));

        if (throwable != null) {
            log.error("작업 실패: queueId={}", taskId, throwable);
            task.markFailed();
            queueRepository.save(task);
        } else {
            log.info("작업 완료: queueId={}", taskId);
            queueRepository.delete(task);
        }
    }

}