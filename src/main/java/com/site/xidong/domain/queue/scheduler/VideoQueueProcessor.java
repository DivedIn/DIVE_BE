package com.site.xidong.domain.queue.scheduler;

import com.site.xidong.domain.queue.entity.VideoProcessingQueue;
import com.site.xidong.domain.queue.repository.VideoProcessingQueueRepository;
import com.site.xidong.domain.video.service.VideoProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoQueueProcessor {

    private final VideoProcessingQueueRepository queueRepository;
    private final VideoProcessingService videoProcessingService;

    // [멱등성] PENDING → PROCESSING 전환을 "조회 + 상태변경"이 한 트랜잭션 안에서, 비관적 락을 쥔 채 끝나도록 묶는다.
    // 이 트랜잭션이 커밋되기 전까지는 같은 행을 노리는 다른 트랜잭션의 findPendingTasksForUpdate가 여기서 대기하고,
    // 커밋된 뒤에는 이미 PROCESSING이라 그 트랜잭션의 WHERE status = 'PENDING' 에 더 이상 걸리지 않는다.
    @Transactional
    public List<VideoProcessingQueue> claimPendingTasks(int limit) {
        List<VideoProcessingQueue> tasks = queueRepository.findPendingTasksForUpdate(
                LocalDateTime.now(), PageRequest.of(0, limit));
        tasks.forEach(VideoProcessingQueue::markProcessing);
        return tasks;
    }

    public void dispatchTask(VideoProcessingQueue task) {
        try {
            log.info("[DB Queue] 작업 시작: {}", task.getId());

            CompletableFuture<Void> future = videoProcessingService.createInitial(
                    task.getUsername(),
                    task.getQuestionId(),
                    task.getRequestNo(),
                    task.getVideoKey(),
                    task.getIsOpen(),
                    task.getStartTime()
            );

            future.whenComplete((result, throwable) -> updateTaskStatus(task.getId(), throwable));
            log.info("작업 제출 완료: queueId={}", task.getId());
        } catch (Exception e) {
            log.error("작업 제출 실패: queueId={}", task.getId(), e);
            task.markFailed();
            queueRepository.save(task);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    // [Reaper] PROCESSING인 채로 threshold를 넘겨 멈춰버린(워커 강제 종료 등) 좀비 작업을 회수한다.
    // markFailed()가 재시도 가능 여부를 판단해 PENDING(백오프 후 재수거)이나 FAILED(DLQ)로 되돌린다.
    @Transactional
    public void reapStuckTasks(LocalDateTime threshold) {
        List<VideoProcessingQueue> stuckTasks = queueRepository.findStuckProcessingTasksForUpdate(threshold);
        if (stuckTasks.isEmpty()) return;

        for (VideoProcessingQueue task : stuckTasks) {
            log.warn("[Reaper] 좀비 작업 회수: queueId={}, startedAt={}", task.getId(), task.getStartedAt());
            task.markFailed();
        }
        queueRepository.saveAll(stuckTasks);
    }
}
