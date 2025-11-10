package com.site.xidong.feedback;

import com.site.xidong.video.Video;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name = "FEEDBACK_ID")
    private Long id;

    @Lob  // 길이 제한 없이 저장 가능
    @Column(columnDefinition = "TEXT")  // MySQL 기준 TEXT 타입으로 지정
    private String contents;

    private LocalDateTime createdAt;

    @OneToOne
    @JoinColumn(name = "VIDEO_ID")
    private Video video;
}
