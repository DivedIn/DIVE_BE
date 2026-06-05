package com.site.xidong.domain.question.dto;

import com.site.xidong.domain.question.entity.Question;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuestionReturnDTO {
    private Long id;
    private String contents;

    public static QuestionReturnDTO from(Question q) {
        return QuestionReturnDTO.builder()
                .id(q.getId())
                .contents(q.getContents())
                .build();
    }
}
