package com.site.xidong.domain.queue.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_processing_queue")
@Getter
@NoArgsConstructor
public class VideoProcessingQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private int requestNo;

    @Column(nullable = false)
    private String videoKey;

    @Column(nullable = false)
    private Boolean isOpen;

    @Column(nullable = false)
    private Long startTime;

    @Column(nullable = false)
    private Boolean usePresignedUrl;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus status = QueueStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime startedAt;

    @Column
    private Integer retryCount = 0;

    @Column
    private LocalDateTime nextRetryAt;

    @Builder
    public VideoProcessingQueue(Long questionId, int requestNo, String videoKey, Boolean isOpen,
                                Long startTime, Boolean usePresignedUrl, String username) {
        this.questionId = questionId;
        this.requestNo = requestNo;
        this.videoKey = videoKey;
        this.isOpen = isOpen;
        this.startTime = startTime;
        this.usePresignedUrl = usePresignedUrl;
        this.username = username;
    }

    public void markProcessing() {
        this.status = QueueStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.status = QueueStatus.COMPLETED;
    }

    // [재시도] retryCount가 MAX_RETRY 미만이면 지수 백오프를 걸고 PENDING으로 되돌려
    // 스케줄러가 다시 집어가게 한다. 소진되면 FAILED로 확정해 더 이상 재수거되지 않는 DLQ 상태로 둔다.
    public void markFailed() {
        if (this.retryCount < MAX_RETRY) {
            this.retryCount++;
            this.status = QueueStatus.PENDING;
            this.nextRetryAt = LocalDateTime.now().plusSeconds(backoffSeconds(this.retryCount));
        } else {
            this.status = QueueStatus.FAILED;
            this.nextRetryAt = null;
        }
    }

    private long backoffSeconds(int retryCount) {
        return BASE_DELAY_SECONDS * (1L << retryCount);
    }

    public static final int MAX_RETRY = 3;
    private static final long BASE_DELAY_SECONDS = 10L;

    public enum QueueStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}