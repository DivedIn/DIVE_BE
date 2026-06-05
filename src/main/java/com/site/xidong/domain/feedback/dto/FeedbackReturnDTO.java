package com.site.xidong.domain.feedback.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FeedbackReturnDTO {
    private Long feedbackId;
    private Long videoId;
    private String contents;
    private LocalDateTime createdAt;
}
