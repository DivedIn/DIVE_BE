package com.site.xidong.comment;

import com.site.xidong.siteUser.SiteUser;
import com.site.xidong.video.Video;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMMENT_ID")
    private long id;

    @ManyToOne
    @JoinColumn(name = "ID")
    private SiteUser siteUser;

    @ManyToOne
    @JoinColumn(name = "VIDEO_ID")
    private Video video;

    private String contents;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
