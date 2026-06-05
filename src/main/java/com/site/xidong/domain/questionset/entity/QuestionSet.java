package com.site.xidong.domain.questionset.entity;

import com.site.xidong.domain.question.entity.Question;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.global.response.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionSet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_SET_ID")
    private Long id;

    private String category;
    private String title;
    private String description;

    @OneToMany(mappedBy = "questionSet", cascade = CascadeType.ALL)
    private List<Question> questions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID")
    private SiteUser siteUser;

    private boolean isOpen;
    private int refCount;

    @Builder
    public QuestionSet(String category, String title, String description,
                       SiteUser siteUser, boolean isOpen, int refCount) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.siteUser = siteUser;
        this.isOpen = isOpen;
        this.refCount = refCount;
    }

    public void update(String title, String description, String category, boolean isOpen) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.isOpen = isOpen;
    }

    public void addQuestions(List<Question> questions) {
        this.questions.addAll(questions);
    }

    public void incrementRefCount(int count) {
        this.refCount += count;
    }
}
