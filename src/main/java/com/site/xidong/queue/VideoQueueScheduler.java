package com.site.xidong.queue;

import com.site.xidong.video.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoQueueScheduler {
    private final VideoProcessingQueueRepository queueRepository;
    private final VideoService videoService;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processQueuedTasks() {
        try {
            if (!videoService.checkThreadPoolCapacity()){
                return;
            }

            List<VideoProcessingQueue> pendingTasks = queueRepository.findPendingTasks(
                    PageRequest.of(0, 10)
            );

            if (pendingTasks.isEmpty()) return;

            log.info("DB 큐 처리 시작: {}개 작업 발견", pendingTasks.size());

            for (VideoProcessingQueue task : pendingTasks) {
                if (!videoService.checkThreadPoolCapacity()) {
                    log.info("스레드풀 재포화, 나머지 작업은 다음 주기에 처리");
                    break;
                }

                try {
                    task.markProcessing();
                    queueRepository.save(task);

                    CompletableFuture<Void> future = videoService.createInitialAsync(
                            task.getQuestionId(),
                            task.getVideoKey(),
                            task.getIsOpen(),
                            task.getStartTime(),
                            task.getUsePresignedUrl()
                    );

                    future.whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("큐 작업 실패: queueId={}", task.getId(), throwable);
                            task.markFailed();
                        } else {
                            log.info("큐 작업 완료: queueId={}", task.getId());
                            task.markCompleted();
                        }
                        queueRepository.save(task);
                    });

                    log.info("DB 큐 -> 스레드풀 이동: queueId={}, videoKey={}",
                            task.getId(), task.getVideoKey());
                } catch (Exception e) {
                    log.error("큐 작업 처리 실패: queueId={}", task.getId(), e);
                    task.markFailed();
                }
            }


        } catch (Exception e) {
            log.error("스케줄러 실행 오류", e);
        }
    }
}
