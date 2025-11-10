package com.site.xidong.question;

import com.site.xidong.questionSet.QuestionSet;
import com.site.xidong.questionSet.QuestionSetNotFoundException;
import com.site.xidong.questionSet.QuestionSetRepository;
import com.site.xidong.security.SiteUserSecurityDTO;
import com.site.xidong.siteUser.SiteUser;
import com.site.xidong.siteUser.SiteUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class QuestionService {
    private final QuestionRepository questionRepository;
    private final QuestionSetRepository questionSetRepository;
    private final SiteUserRepository siteUserRepository;

    public QuestionReturnDTO create(Long id, String contents) throws Exception, QuestionSetNotFoundException {
        Optional<QuestionSet> selectedSet = questionSetRepository.findById(id);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Question newQuestion;
        QuestionReturnDTO questionReturnDTO;
        if(selectedSet.isPresent()) {
            QuestionSet questionSet = selectedSet.get();
            if(!siteUser.getUsername().equals(questionSet.getSiteUser().getUsername())) {
                throw new Exception("질문 추가 권한이 없습니다.");
            } else {
                Question question = Question.builder()
                        .questionSet(questionSet)
                        .contents(contents)
                        .build();
                newQuestion = questionRepository.save(question);
                questionReturnDTO = new QuestionReturnDTO(newQuestion.getId(), newQuestion.getContents());
            }
        } else {
            throw new QuestionNotFoundException("면접세트를 찾을 수 없습니다.");
        }
        return questionReturnDTO;
    }

    public QuestionReturnDTO update(Long setId, Long id, String contents) throws Exception, QuestionNotFoundException {
        Optional<Question> selectedQ = questionRepository.findByQuestionSetIdAndId(setId, id);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Question updatedQ;
        if(selectedQ.isPresent()) {
            Question question = selectedQ.get();
            if(!siteUser.getUsername().equals(question.getQuestionSet().getSiteUser().getUsername())) {
                throw new Exception("삭제 권한이 없습니다.");
            } else {
                question.setContents(contents);
                updatedQ = questionRepository.save(question);
            }
        } else {
            throw new QuestionNotFoundException("질문을 찾을 수 없습니다.");
        }
        QuestionReturnDTO questionReturnDTO = QuestionReturnDTO.builder()
                .id(updatedQ.getId())
                .contents(updatedQ.getContents())
                .build();
        return questionReturnDTO;
    }

    public void delete(Long setId, List<Long> questionIds) throws Exception, QuestionNotFoundException{
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        for(Long id : questionIds) {
            Optional<Question> selectedQ = questionRepository.findByQuestionSetIdAndId(setId, id);
            if (selectedQ.isPresent()) {
                Question question = selectedQ.get();
                if (!siteUser.getUsername().equals(question.getQuestionSet().getSiteUser().getUsername())) {
                    throw new Exception("삭제 권한이 없습니다.");
                } else {
                    questionRepository.delete(question);
                }
            } else {
                throw new QuestionNotFoundException("질문을 찾을 수 없습니다.");
            }
        }
    }
}
