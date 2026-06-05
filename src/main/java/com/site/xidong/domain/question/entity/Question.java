package com.site.xidong.domain.question.entity;

import com.site.xidong.domain.questionset.entity.QuestionSet;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QUESTION_SET_ID")
    private QuestionSet questionSet;

    private String contents;

    @Builder
    public Question(QuestionSet questionSet, String contents) {
        this.questionSet = questionSet;
        this.contents = contents;
    }

    public void update(String contents) {
        this.contents = contents;
    }
}
