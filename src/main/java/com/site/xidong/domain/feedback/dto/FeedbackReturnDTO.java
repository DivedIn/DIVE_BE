package com.site.xidong.domain.feedback.dto;

import com.site.xidong.domain.feedback.entity.Feedback;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FeedbackReturnDTO {
    private Long feedbackId;
    private Long videoId;
    private String contents;
    private LocalDateTime createdAt;

    public static FeedbackReturnDTO from(Feedback f) {
        return FeedbackReturnDTO.builder()
                .feedbackId(f.getId())
                .videoId(f.getVideo().getId())
                .contents(f.getContents())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
