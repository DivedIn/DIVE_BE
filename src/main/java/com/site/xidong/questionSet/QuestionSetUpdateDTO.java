package com.site.xidong.questionSet;

import lombok.Data;

@Data
public class QuestionSetUpdateDTO {
    private String title;
    private String description;
    private String category;
    private Boolean isOpen;
}
