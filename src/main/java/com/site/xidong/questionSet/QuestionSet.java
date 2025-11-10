package com.site.xidong.questionSet;

import com.site.xidong.question.Question;
import com.site.xidong.siteUser.SiteUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_SET_ID")
    private long id;

    private String category;

    private String title;

    private String description; // TODO: 글자 수 제한

    @OneToMany(mappedBy = "questionSet", cascade = CascadeType.ALL)
    private List<Question> questions = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "ID")
    private SiteUser siteUser;

    private boolean isOpen;

    private int refCount;

    private LocalDateTime createdAt;
}
