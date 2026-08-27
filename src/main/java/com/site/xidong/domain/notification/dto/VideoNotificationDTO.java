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
    // [관측 가능성] 202 응답의 queueId와 이 SSE 이벤트의 videoId는 서로 다른 값이라
    // 클라이언트가 둘을 이어 붙일 방법이 없었다. 같은 traceId를 양쪽에 실어서 correlate한다.
    private String traceId;
}
