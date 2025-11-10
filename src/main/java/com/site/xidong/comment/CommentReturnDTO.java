package com.site.xidong.comment;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentReturnDTO {
    private long commentId;
    private String imageUrl;
    private String username;
    private String nickname;
    private String videoPath;
    private String videoName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String contents;
}
