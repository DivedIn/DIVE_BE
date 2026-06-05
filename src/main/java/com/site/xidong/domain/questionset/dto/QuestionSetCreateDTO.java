package com.site.xidong.domain.questionset.dto;

import lombok.Data;

@Data
public class QuestionSetCreateDTO {
    private String title;
    private String category;
    private boolean isOpen;
    private String description;
}
