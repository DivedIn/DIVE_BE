package com.site.xidong.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    @Query("SELECT q FROM Question q WHERE q.questionSet.id = :setId AND q.id = :id")
    Optional<Question> findByQuestionSetIdAndId(Long setId, Long id);

}
