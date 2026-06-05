package com.site.xidong.domain.question.service;

import com.site.xidong.domain.questionset.entity.QuestionSet;
import com.site.xidong.domain.questionset.exception.QuestionSetNotFoundException;
import com.site.xidong.domain.questionset.repository.QuestionSetRepository;
import com.site.xidong.domain.user.dto.SiteUserSecurityDTO;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.user.repository.SiteUserRepository;
import com.site.xidong.domain.question.repository.QuestionRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import com.site.xidong.domain.question.entity.Question;
import com.site.xidong.domain.question.dto.QuestionReturnDTO;
import com.site.xidong.domain.question.exception.QuestionNotFoundException;

@Log4j2
@Service
@RequiredArgsConstructor
public class QuestionService {
    private final QuestionRepository questionRepository;
    private final QuestionSetRepository questionSetRepository;
    private final SiteUserRepository siteUserRepository;

    public QuestionReturnDTO create(Long id, String contents) {
        Optional<QuestionSet> selectedSet = questionSetRepository.findById(id);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Question newQuestion;
        QuestionReturnDTO questionReturnDTO;
        if(selectedSet.isPresent()) {
            QuestionSet questionSet = selectedSet.get();
            if(!siteUser.getUsername().equals(questionSet.getSiteUser().getUsername())) {
                throw new CustomException(ErrorCode.FORBIDDEN);
            } else {
                Question question = Question.builder()
                        .questionSet(questionSet)
                        .contents(contents)
                        .build();
                newQuestion = questionRepository.save(question);
                questionReturnDTO = new QuestionReturnDTO(newQuestion.getId(), newQuestion.getContents());
            }
        } else {
            throw new QuestionNotFoundException();
        }
        return questionReturnDTO;
    }

    public QuestionReturnDTO update(Long setId, Long id, String contents) {
        Optional<Question> selectedQ = questionRepository.findByQuestionSetIdAndId(setId, id);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Question updatedQ;
        if(selectedQ.isPresent()) {
            Question question = selectedQ.get();
            if(!siteUser.getUsername().equals(question.getQuestionSet().getSiteUser().getUsername())) {
                throw new CustomException(ErrorCode.FORBIDDEN);
            } else {
                question.setContents(contents);
                updatedQ = questionRepository.save(question);
            }
        } else {
            throw new QuestionNotFoundException();
        }
        QuestionReturnDTO questionReturnDTO = QuestionReturnDTO.builder()
                .id(updatedQ.getId())
                .contents(updatedQ.getContents())
                .build();
        return questionReturnDTO;
    }

    public void delete(Long setId, List<Long> questionIds){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        for(Long id : questionIds) {
            Optional<Question> selectedQ = questionRepository.findByQuestionSetIdAndId(setId, id);
            if (selectedQ.isPresent()) {
                Question question = selectedQ.get();
                if (!siteUser.getUsername().equals(question.getQuestionSet().getSiteUser().getUsername())) {
                    throw new CustomException(ErrorCode.FORBIDDEN);
                } else {
                    questionRepository.delete(question);
                }
            } else {
                throw new QuestionNotFoundException();
            }
        }
    }
}
