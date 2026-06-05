package com.site.xidong.domain.video.entity;

import com.site.xidong.domain.comment.entity.Comment;
import com.site.xidong.domain.feedback.entity.Feedback;
import com.site.xidong.domain.question.entity.Question;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.global.response.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Video extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name = "VIDEO_ID")
    private Long id;

    @Column(name = "video_path", nullable = false, length = 500)
    private String videoPath;

    @Column(name = "video_name")
    private String videoName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID")
    private SiteUser siteUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QUESTION_ID")
    private Question question;

    @OneToMany(mappedBy = "video", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comment> commentList;

    @OneToOne(mappedBy = "video", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private Feedback feedback;

    private String thumbnail;

    private boolean isOpen;

    @Column(nullable = false)
    private String processingStatus;

    @Builder
    public Video(String videoPath, String videoName, SiteUser siteUser, Question question,
                 boolean isOpen, String processingStatus) {
        this.videoPath = videoPath;
        this.videoName = videoName;
        this.siteUser = siteUser;
        this.question = question;
        this.isOpen = isOpen;
        this.processingStatus = processingStatus;
    }

    public void updateThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void updateStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public void updateVisibility(boolean isOpen) {
        this.isOpen = isOpen;
    }

    public void linkFeedback(Feedback feedback) {
        this.feedback = feedback;
    }
}
