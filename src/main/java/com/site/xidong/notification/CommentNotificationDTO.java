package com.site.xidong.notification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommentNotificationDTO {
    private Long videoId;
    private String message;
    private Long commentId;
}
