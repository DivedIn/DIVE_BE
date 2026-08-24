package com.site.xidong.domain.queue.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;


import java.time.LocalDateTime;
import java.util.List;
import com.site.xidong.domain.queue.entity.VideoProcessingQueue;

public interface VideoProcessingQueueRepository extends JpaRepository<VideoProcessingQueue, Long> {
    @Query("SELECT q FROM VideoProcessingQueue q WHERE q.status = 'PENDING' "
            + "AND (q.nextRetryAt IS NULL OR q.nextRetryAt <= CURRENT_TIMESTAMP) ORDER BY q.createdAt ASC")
    List<VideoProcessingQueue> findPendingTasks(Pageable pageable);

    // [멱등성] PENDING 행을 SELECT ... FOR UPDATE로 잠근 채 가져온다.
    // 이 안에서 상태를 PROCESSING으로 바꾸고 커밋해야, 락을 기다리던 다른 트랜잭션의
    // 다음 SELECT가 더 이상 이 행을 PENDING으로 보지 못해 중복 처리를 막는다.
    // [재시도] 백오프 대기 중인(nextRetryAt이 미래인) 행은 아직 재수거 대상이 아니므로 걸러낸다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT q FROM VideoProcessingQueue q WHERE q.status = 'PENDING' "
            + "AND (q.nextRetryAt IS NULL OR q.nextRetryAt <= CURRENT_TIMESTAMP) ORDER BY q.createdAt ASC")
    List<VideoProcessingQueue> findPendingTasksForUpdate(Pageable pageable);

    // [Reaper] PROCESSING 상태로 startedAt 이후 threshold(예: 5분)를 넘긴 좀비 작업을 찾는다.
    // 워커가 죽거나 응답 없이 멈춘 경우, 이 행들을 다시 회수해 재시도/DLQ 판정을 내린다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT q FROM VideoProcessingQueue q WHERE q.status = 'PROCESSING' AND q.startedAt < :threshold")
    List<VideoProcessingQueue> findStuckProcessingTasksForUpdate(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(q) FROM VideoProcessingQueue q WHERE q.status = 'PENDING'")
    long countPendingTasks();

    long countByStatus(VideoProcessingQueue.QueueStatus status);
}
