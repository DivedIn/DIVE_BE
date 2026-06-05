package com.site.xidong.domain.question.entity;


import com.site.xidong.domain.questionset.entity.QuestionSet;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_ID")
    private long id;

    @ManyToOne
    @JoinColumn(name = "QUESTION_SET_ID")
    private QuestionSet questionSet;

    private String contents;
}
