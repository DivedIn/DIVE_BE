package com.site.xidong.video;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder
@Data
public class VideoReturnDTO {
    private Long videoId;
    private String videoPath;
    private String videoName;
    private String imageUrl;
    private String username;
    private String nickname;
    private String thumbnail;
    private String question;
    private String category;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isOpen;
}
