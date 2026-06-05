package com.site.xidong.domain.comment.dto;

import com.site.xidong.domain.comment.entity.Comment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommentReturnDTO {
    private Long commentId;
    private String imageUrl;
    private String username;
    private String nickname;
    private String videoPath;
    private String videoName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String contents;

    public static CommentReturnDTO from(Comment c) {
        return CommentReturnDTO.builder()
                .commentId(c.getId())
                .imageUrl(c.getSiteUser().getImageUrl())
                .username(c.getSiteUser().getUsername())
                .nickname(c.getSiteUser().getNickname())
                .videoPath(c.getVideo().getVideoPath())
                .videoName(c.getVideo().getVideoName())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .contents(c.getContents())
                .build();
    }
}
