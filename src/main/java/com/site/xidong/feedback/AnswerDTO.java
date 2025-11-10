package com.site.xidong.feedback;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnswerDTO {
    private Long videoId;
    private String answer;
}
