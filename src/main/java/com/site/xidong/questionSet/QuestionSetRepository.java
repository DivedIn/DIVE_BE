package com.site.xidong.questionSet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {
    @Query("SELECT q FROM QuestionSet q WHERE q.isOpen = true")
    List<QuestionSet> findAllOpenQuestionSets();

    @Query("SELECT q FROM QuestionSet q WHERE q.siteUser.username = :username")
    List<QuestionSet> findMySets(String username);

    @Query("SELECT qs FROM QuestionSet qs LEFT JOIN FETCH qs.questions WHERE qs.id = :id")
    Optional<QuestionSet> findByIdWithQuestions(Long id);

    @Query("SELECT qs FROM QuestionSet qs JOIN FETCH qs.questions WHERE qs.isOpen = true")
    List<QuestionSet> findAllOpenQuestionSetsWithQuestions();

    @Query("SELECT qs FROM QuestionSet qs JOIN FETCH qs.questions WHERE qs.siteUser.username = :username")
    List<QuestionSet> findMySetsWithQuestions(String username);
}
