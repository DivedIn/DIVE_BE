package com.site.xidong.domain.notification.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VideoNotificationDTO {
    private Long videoId;
    private String status;
    private String message;
    private Long feedbackId;
}
