package com.site.xidong.questionSet;

import lombok.Data;

@Data
public class QuestionSetCreateDTO {
    private String title;
    private String category;
    private boolean isOpen;
    private String description;
}
