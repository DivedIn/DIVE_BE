package com.site.xidong.domain.video.entity;

import com.site.xidong.domain.comment.entity.Comment;
import com.site.xidong.domain.feedback.entity.Feedback;
import com.site.xidong.domain.question.entity.Question;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.global.response.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

// [인덱스 점검] findOpenVideosAfterCursor/findMyVideosAfterCursor는 각각
// "isOpen으로 거르고 video_id로 정렬", "siteUser FK로 거르고 video_id로 정렬" 패턴이다.
// SKIP LOCKED 작업에서 실측으로 확인했듯, WHERE 컬럼 하나만 인덱싱하면 정렬용 인덱스가
// 없어 filesort가 붙고 LIMIT을 걸어도 조건에 맞는 행을 사실상 다 훑는다 — 그래서
// 단일 컬럼이 아니라 (필터, video_id) 복합 인덱스로 정렬까지 인덱스가 커버하게 했다.
// site_user FK 컬럼은 실제로는 "site_user_id"가 아니라 "id"다 — @JoinColumn(name="ID")
// 때문에 컬럼명이 그렇게 굳어져 있다(원래 티켓엔 site_user_id로 적혀 있었는데 실제
// 스키마와 다르다는 걸 이번에 코드/이전 세션의 DESCRIBE 결과로 확인했다).
@Entity
@Table(indexes = {
        @Index(name = "idx_video_is_open_id", columnList = "is_open, video_id"),
        @Index(name = "idx_video_site_user_id", columnList = "id, video_id")
})
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

    // [낙관적 락] changeVisibility()처럼 "가끔 부딪히는" 갱신에 붙인다. 평소엔 락을 전혀
    // 안 걸다가, 저장 시점에 DB의 현재 버전과 내가 읽었던 버전이 다르면(그 사이 다른
    // 트랜잭션이 먼저 커밋했으면) ObjectOptimisticLockingFailureException으로 알려준다.
    @Version
    private Long version;

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
