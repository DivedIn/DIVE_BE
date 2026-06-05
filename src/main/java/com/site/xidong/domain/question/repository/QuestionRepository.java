package com.site.xidong.domain.question.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import com.site.xidong.domain.question.entity.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    @Query("SELECT q FROM Question q WHERE q.questionSet.id = :setId AND q.id = :id")
    Optional<Question> findByQuestionSetIdAndId(Long setId, Long id);

}
