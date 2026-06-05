package com.site.xidong.domain.questionset.service;

import com.site.xidong.domain.question.entity.Question;
import com.site.xidong.domain.question.repository.QuestionRepository;
import com.site.xidong.domain.questionset.dto.QuestionSetCreateDTO;
import com.site.xidong.domain.questionset.dto.QuestionSetReturnDTO;
import com.site.xidong.domain.questionset.dto.QuestionSetUpdateDTO;
import com.site.xidong.domain.questionset.entity.QuestionSet;
import com.site.xidong.domain.questionset.exception.QuestionSetNotFoundException;
import com.site.xidong.domain.questionset.repository.QuestionSetRepository;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.user.repository.SiteUserRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionSetService {

    private final QuestionSetRepository questionSetRepository;
    private final QuestionRepository questionRepository;
    private final SiteUserRepository siteUserRepository;

    public List<QuestionSetReturnDTO> findAll() {
        return questionSetRepository.findAllOpenQuestionSetsWithQuestions().stream()
                .map(QuestionSetReturnDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public QuestionSetReturnDTO create(String username, QuestionSetCreateDTO dto) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        QuestionSet questionSet = QuestionSet.builder()
                .category(dto.getCategory())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .siteUser(siteUser)
                .isOpen(dto.isOpen())
                .refCount(0)
                .build();
        return QuestionSetReturnDTO.from(questionSetRepository.save(questionSet));
    }

    public List<QuestionSetReturnDTO> findMySets(String username) {
        return questionSetRepository.findMySetsWithQuestions(username).stream()
                .map(QuestionSetReturnDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public QuestionSetReturnDTO updateQuestionSet(Long setId, String username, QuestionSetUpdateDTO dto) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        QuestionSet questionSet = questionSetRepository.findById(setId)
                .orElseThrow(QuestionSetNotFoundException::new);
        if (!questionSet.getSiteUser().getUsername().equals(siteUser.getUsername())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        questionSet.update(dto.getTitle(), dto.getDescription(), dto.getCategory(), dto.getIsOpen());
        return QuestionSetReturnDTO.from(questionSet);
    }

    @Transactional
    public void delete(Long setId, String username) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        QuestionSet questionSet = questionSetRepository.findById(setId)
                .orElseThrow(QuestionSetNotFoundException::new);
        if (!siteUser.getUsername().equals(questionSet.getSiteUser().getUsername())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        questionSetRepository.delete(questionSet);
    }

    @Transactional
    public QuestionSetReturnDTO bringNew(Long fromId, String username, List<Long> questionIds) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        QuestionSet fromSet = questionSetRepository.findById(fromId)
                .orElseThrow(QuestionSetNotFoundException::new);

        QuestionSet newSet = QuestionSet.builder()
                .category(fromSet.getCategory())
                .title(fromSet.getTitle() + " 복사본")
                .description(fromSet.getDescription())
                .siteUser(siteUser)
                .isOpen(true)
                .refCount(0)
                .build();

        List<Question> questions = questionIds.stream()
                .map(id -> questionRepository.findByQuestionSetIdAndId(fromId, id)
                        .orElseThrow(QuestionSetNotFoundException::new))
                .map(q -> Question.builder().questionSet(newSet).contents(q.getContents()).build())
                .collect(Collectors.toList());
        newSet.addQuestions(questions);
        fromSet.incrementRefCount(1);
        return QuestionSetReturnDTO.from(questionSetRepository.save(newSet));
    }

    @Transactional
    public void bringN(Long fromId, String username, List<Long> questionIds, List<Long> toIds) {
        siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        QuestionSet fromSet = questionSetRepository.findById(fromId)
                .orElseThrow(QuestionSetNotFoundException::new);
        for (Long setId : toIds) {
            QuestionSet toSet = questionSetRepository.findByIdWithQuestions(setId)
                    .orElseThrow(QuestionSetNotFoundException::new);
            List<Question> newQuestions = questionIds.stream()
                    .map(qId -> questionRepository.findByQuestionSetIdAndId(fromId, qId)
                            .orElseThrow(QuestionSetNotFoundException::new))
                    .map(q -> Question.builder().questionSet(toSet).contents(q.getContents()).build())
                    .collect(Collectors.toList());
            toSet.addQuestions(newQuestions);
        }
        fromSet.incrementRefCount(toIds.size());
    }
}
