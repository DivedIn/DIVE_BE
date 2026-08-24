package com.site.xidong.domain.queue.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;


import java.util.List;
import com.site.xidong.domain.queue.entity.VideoProcessingQueue;

public interface VideoProcessingQueueRepository extends JpaRepository<VideoProcessingQueue, Long> {
    @Query("SELECT q FROM VideoProcessingQueue q WHERE q.status = 'PENDING' ORDER BY q.createdAt ASC")
    List<VideoProcessingQueue> findPendingTasks(Pageable pageable);

    // [멱등성] PENDING 행을 SELECT ... FOR UPDATE로 잠근 채 가져온다.
    // 이 안에서 상태를 PROCESSING으로 바꾸고 커밋해야, 락을 기다리던 다른 트랜잭션의
    // 다음 SELECT가 더 이상 이 행을 PENDING으로 보지 못해 중복 처리를 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT q FROM VideoProcessingQueue q WHERE q.status = 'PENDING' ORDER BY q.createdAt ASC")
    List<VideoProcessingQueue> findPendingTasksForUpdate(Pageable pageable);

    @Query("SELECT COUNT(q) FROM VideoProcessingQueue q WHERE q.status = 'PENDING'")
    long countPendingTasks();

    long countByStatus(VideoProcessingQueue.QueueStatus status);
}
