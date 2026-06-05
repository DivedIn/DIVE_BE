package com.site.xidong.domain.video.dto;

import com.site.xidong.domain.video.entity.Video;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
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

    public static VideoReturnDTO from(Video video) {
        return VideoReturnDTO.builder()
                .videoId(video.getId())
                .videoPath(video.getVideoPath())
                .videoName(video.getVideoName())
                .imageUrl(video.getSiteUser().getImageUrl())
                .username(video.getSiteUser().getUsername())
                .nickname(video.getSiteUser().getNickname())
                .thumbnail(video.getThumbnail())
                .question(video.getQuestion().getContents())
                .category(video.getQuestion().getQuestionSet().getCategory())
                .createdAt(video.getCreatedAt())
                .updatedAt(video.getUpdatedAt())
                .isOpen(video.isOpen())
                .build();
    }
}
