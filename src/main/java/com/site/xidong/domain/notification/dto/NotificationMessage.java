package com.site.xidong.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Redis Pub/Sub 채널에 실어 보내는 봉투. data는 발행 시점에 이미 JSON 문자열로
 * 직렬화해 넣는다 — 구독자는 이 문자열을 그대로 SSE data로 흘려보내면 되므로
 * 원본 DTO 타입을 구독자 쪽에서 알 필요가 없다(역직렬화 왕복이 아예 없음).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private String username;
    private String eventName;
    private String dataJson;
}
