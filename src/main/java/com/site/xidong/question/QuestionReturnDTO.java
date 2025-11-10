package com.site.xidong.question;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionReturnDTO {
    private long id;
    private String contents;

    public QuestionReturnDTO(long id, String contents) {
        this.id = id;
        this.contents = contents;
    }
}
