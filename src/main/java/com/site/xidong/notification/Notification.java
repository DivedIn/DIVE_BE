package com.site.xidong.notification;

import com.site.xidong.comment.Comment;
import com.site.xidong.siteUser.SiteUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name = "NOTIFICATION_ID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ID")
    private SiteUser siteUser;

    @OneToOne
    @JoinColumn(name = "COMMENT_ID")
    private Comment comment;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean isRead;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
