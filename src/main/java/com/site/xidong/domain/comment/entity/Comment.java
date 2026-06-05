package com.site.xidong.domain.comment.entity;

import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.video.entity.Video;
import com.site.xidong.global.response.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMMENT_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID")
    private SiteUser siteUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VIDEO_ID")
    private Video video;

    private String contents;

    @Builder
    public Comment(SiteUser siteUser, Video video, String contents) {
        this.siteUser = siteUser;
        this.video = video;
        this.contents = contents;
    }

    public void update(String contents) {
        this.contents = contents;
    }
}
