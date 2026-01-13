package com.site.xidong.queue;

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

    @Builder
    public VideoProcessingQueue(Long questionId, String videoKey, Boolean isOpen,
                                Long startTime, Boolean usePresignedUrl, String username) {
        this.questionId = questionId;
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

    public void markFailed() {
        this.status = QueueStatus.FAILED;
        this.retryCount++;
    }

    public enum QueueStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}