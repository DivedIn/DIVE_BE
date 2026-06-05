package com.site.xidong.domain.question.service;

import com.site.xidong.domain.question.dto.QuestionReturnDTO;
import com.site.xidong.domain.question.entity.Question;
import com.site.xidong.domain.question.exception.QuestionNotFoundException;
import com.site.xidong.domain.question.repository.QuestionRepository;
import com.site.xidong.domain.questionset.entity.QuestionSet;
import com.site.xidong.domain.questionset.repository.QuestionSetRepository;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.user.repository.SiteUserRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionSetRepository questionSetRepository;
    private final SiteUserRepository siteUserRepository;

    @Transactional
    public QuestionReturnDTO create(Long setId, String username, String contents) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        QuestionSet questionSet = questionSetRepository.findById(setId)
                .orElseThrow(QuestionNotFoundException::new);
        if (!siteUser.getUsername().equals(questionSet.getSiteUser().getUsername())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        Question question = Question.builder()
                .questionSet(questionSet)
                .contents(contents)
                .build();
        return QuestionReturnDTO.from(questionRepository.save(question));
    }

    @Transactional
    public QuestionReturnDTO update(Long setId, Long id, String username, String contents) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Question question = questionRepository.findByQuestionSetIdAndId(setId, id)
                .orElseThrow(QuestionNotFoundException::new);
        if (!siteUser.getUsername().equals(question.getQuestionSet().getSiteUser().getUsername())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        question.update(contents);
        return QuestionReturnDTO.from(question);
    }

    @Transactional
    public void delete(Long setId, String username, List<Long> questionIds) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        for (Long id : questionIds) {
            Question question = questionRepository.findByQuestionSetIdAndId(setId, id)
                    .orElseThrow(QuestionNotFoundException::new);
            if (!siteUser.getUsername().equals(question.getQuestionSet().getSiteUser().getUsername())) {
                throw new CustomException(ErrorCode.FORBIDDEN);
            }
            questionRepository.delete(question);
        }
    }
}
