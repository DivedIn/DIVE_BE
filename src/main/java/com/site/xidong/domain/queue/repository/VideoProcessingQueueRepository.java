package com.site.xidong.domain.queue.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import java.util.List;
import import com.site.xidong.domain.queue.entity.VideoProcessingQueue;;

public interface VideoProcessingQueueRepository extends JpaRepository<VideoProcessingQueue, Long> {
    @Query("SELECT q FROM VideoProcessingQueue q WHERE q.status = 'PENDING' ORDER BY q.createdAt ASC")
    List<VideoProcessingQueue> findPendingTasks(Pageable pageable);

    @Query("SELECT COUNT(q) FROM VideoProcessingQueue q WHERE q.status = 'PENDING'")
    long countPendingTasks();

    long countByStatus(VideoProcessingQueue.QueueStatus status);
}
