package com.site.xidong.domain.feedback.entity;

import com.site.xidong.domain.video.entity.Video;
import com.site.xidong.global.response.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name = "FEEDBACK_ID")
    private Long id;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String contents;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VIDEO_ID")
    private Video video;

    @Builder
    public Feedback(String contents, Video video) {
        this.contents = contents;
        this.video = video;
    }
}
