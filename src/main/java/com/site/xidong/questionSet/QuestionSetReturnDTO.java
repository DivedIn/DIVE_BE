package com.site.xidong.questionSet;

import com.site.xidong.question.QuestionReturnDTO;
import lombok.Builder;
import lombok.Data;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Data
public class QuestionSetReturnDTO {
    /*
    프로필 이미지, nickname, refCount, title, description, id
    */
    private String imageUrl;

    private String username;

    private String nickname;

    private int refCount;

    private String title;

    private String description;

    private long id;

    private String category;

    private boolean isOpen;

    private List<QuestionReturnDTO> questions;
    private LocalDateTime createdAt;
}
