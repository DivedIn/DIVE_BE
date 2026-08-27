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
    // [재시도] nextRetryAt과의 비교는 반드시 애플리케이션(JVM) 시계인 :now로 한다.
    // JPQL의 CURRENT_TIMESTAMP는 DB 서버의 시계로 평가되는데, nextRetryAt은 애플리케이션 쪽
    // LocalDateTime.now()로 계산해 저장한 값이라 DB 서버와 애플리케이션 서버의 시간대가
    // 다르면(운영 DB가 UTC로 도는 경우 등) 백오프가 영원히 풀리지 않는 사고로 이어진다.
    @Query("SELECT q FROM VideoProcessingQueue q WHERE q.status = 'PENDING' "
            + "AND (q.nextRetryAt IS NULL OR q.nextRetryAt <= :now) ORDER BY q.createdAt ASC")
    List<VideoProcessingQueue> findPendingTasks(@Param("now") LocalDateTime now, Pageable pageable);

    // [SKIP LOCKED] 인스턴스가 여러 대 떠 있을 때, 서로 다른 인스턴스가 같은 오래된 행들을
    // 노리다가 락을 기다리며 대기하거나(최악의 경우 lock timeout 예외로 이어지거나) 하지 않도록
    // 이미 다른 트랜잭션이 잠근 행은 건너뛰고 그다음으로 오래된 PENDING 행을 대신 잠근다.
    // JPQL의 @Lock은 SKIP LOCKED 옵션을 지원하지 않아 네이티브 쿼리로 직접 쓴다.
    // (그래서 이 메서드엔 @Lock/@QueryHints를 따로 안 붙인다 — SQL에 이미 FOR UPDATE SKIP LOCKED가
    // 박혀 있는데 @Lock까지 같이 쓰면 Hibernate가 또 락 절을 덧붙이려다 쿼리가 깨진다.)
    // Pageable 대신 LIMIT을 직접 받는 이유도 같다 — 네이티브 쿼리에 Pageable의 페이징 변환을
    // 얹기보다 LIMIT :limit을 그대로 쓰는 편이 SKIP LOCKED 구문과 섞을 때 안전하다.
    @Query(value = "SELECT * FROM video_processing_queue "
            + "WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= :now) "
            + "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<VideoProcessingQueue> findPendingTasksForUpdate(@Param("now") LocalDateTime now, @Param("limit") int limit);

    // [Reaper] PROCESSING 상태로 startedAt 이후 threshold(예: 5분)를 넘긴 좀비 작업을 찾는다.
    // 워커가 죽거나 응답 없이 멈춘 경우, 이 행들을 다시 회수해 재시도/DLQ 판정을 내린다.
    // 참고: 이 쿼리는 아직 SKIP LOCKED로 안 바꿨다 — Reaper는 30초에 한 번, PROCESSING인
    // (보통 소수의) 좀비 행만 노려서 findPendingTasksForUpdate만큼 여러 인스턴스가 자주
    // 동시에 부딪힐 가능성이 낮다고 보고 이번 범위에서 뺐다. 인스턴스 수가 늘면 여기도 같이 바꿔야 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT q FROM VideoProcessingQueue q WHERE q.status = 'PROCESSING' AND q.startedAt < :threshold")
    List<VideoProcessingQueue> findStuckProcessingTasksForUpdate(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(q) FROM VideoProcessingQueue q WHERE q.status = 'PENDING'")
    long countPendingTasks();

    long countByStatus(VideoProcessingQueue.QueueStatus status);
}
