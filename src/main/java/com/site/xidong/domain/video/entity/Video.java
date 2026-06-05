package com.site.xidong.domain.video.entity;

import com.site.xidong.domain.comment.entity.Comment;
import com.site.xidong.domain.feedback.entity.Feedback;
import com.site.xidong.domain.question.entity.Question;
import com.site.xidong.domain.user.entity.SiteUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name = "VIDEO_ID")
    private Long id;

    @Column(name = "video_path", nullable = false, length = 500)
    private String videoPath;

    @Column(name = "video_name")
    private String videoName;

    @ManyToOne
    @JoinColumn(name = "ID")
    private SiteUser siteUser;

    @ManyToOne
    @JoinColumn(name = "QUESTION_ID" )
    private Question question;

    @OneToMany(mappedBy = "video", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comment> commentList;

    @OneToOne(mappedBy = "video", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private Feedback feedback;

    private String thumbnail;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private boolean isOpen;

    @Column(nullable = false)
    private String processingStatus;
}
