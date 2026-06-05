package com.site.xidong.domain.notification.entity;

import com.site.xidong.domain.comment.entity.Comment;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.global.response.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name = "NOTIFICATION_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID")
    private SiteUser siteUser;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COMMENT_ID")
    private Comment comment;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean isRead;

    @Builder
    public Notification(SiteUser siteUser, Comment comment, String message) {
        this.siteUser = siteUser;
        this.comment = comment;
        this.message = message;
        this.isRead = false;
    }
}
