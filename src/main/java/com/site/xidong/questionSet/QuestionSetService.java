package com.site.xidong.questionSet;

import com.site.xidong.question.Question;
import com.site.xidong.question.QuestionRepository;
import com.site.xidong.question.QuestionReturnDTO;
import com.site.xidong.security.SiteUserSecurityDTO;
import com.site.xidong.siteUser.SiteUser;
import com.site.xidong.siteUser.SiteUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class QuestionSetService {
    private final QuestionSetRepository questionSetRepository;
    private final QuestionRepository questionRepository;
    private final SiteUserRepository siteUserRepository;

    public List<QuestionSetReturnDTO> findAll() { //TODO: question 목록 뜨게 수정
        List<QuestionSet> questionSets = questionSetRepository.findAllOpenQuestionSetsWithQuestions();
        List<QuestionSetReturnDTO> questionSetReturnDTOS = new ArrayList<>();
        for(QuestionSet questionSet : questionSets) {
            List<QuestionReturnDTO> questionReturnDTOS = new ArrayList<>();
            for(Question question : questionSet.getQuestions()) {
                QuestionReturnDTO questionReturnDTO = new QuestionReturnDTO(question.getId(), question.getContents());
                questionReturnDTOS.add(questionReturnDTO);
            }
            QuestionSetReturnDTO questionSetReturnDTO = QuestionSetReturnDTO.builder()
                    .username(questionSet.getSiteUser().getUsername())
                    .isOpen(questionSet.isOpen())
                    .imageUrl(questionSet.getSiteUser().getImageUrl())
                    .nickname(questionSet.getSiteUser().getNickname())
                    .refCount(questionSet.getRefCount())
                    .title(questionSet.getTitle())
                    .description(questionSet.getDescription())
                    .id(questionSet.getId())
                    .category(questionSet.getCategory())
                    .questions(questionReturnDTOS)
                    .createdAt(questionSet.getCreatedAt())
                    .build();
            questionSetReturnDTOS.add(questionSetReturnDTO);
        }
        return questionSetReturnDTOS;
    }

    public QuestionSetReturnDTO create(QuestionSetCreateDTO questionSetCreateDTO) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        QuestionSet questionSet = QuestionSet.builder()
                .category(questionSetCreateDTO.getCategory())
                .title(questionSetCreateDTO.getTitle())
                .description(questionSetCreateDTO.getDescription())
                .siteUser(siteUser)
                .isOpen(questionSetCreateDTO.isOpen())
                .createdAt(LocalDateTime.now())
                .build();
        QuestionSet newQuestionSet = questionSetRepository.save(questionSet);
        QuestionSetReturnDTO questionSetReturnDTO = QuestionSetReturnDTO.builder()
                .username(siteUser.getUsername())
                .isOpen(newQuestionSet.isOpen())
                .imageUrl(siteUser.getImageUrl())
                .nickname(siteUser.getNickname())
                .refCount(newQuestionSet.getRefCount())
                .title(newQuestionSet.getTitle())
                .description(newQuestionSet.getDescription())
                .id(newQuestionSet.getId())
                .category(newQuestionSet.getCategory())
                .questions(null)
                .createdAt(newQuestionSet.getCreatedAt())
                .build();
        return questionSetReturnDTO;
    }

    public List<QuestionSetReturnDTO> findMySets() { //TODO: question 목록 뜨게 수정
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        List<QuestionSet> questionSets = questionSetRepository.findMySetsWithQuestions(siteUser.getUsername());
        List<QuestionSetReturnDTO> questionSetReturnDTOS = new ArrayList<>();
        for(QuestionSet questionSet : questionSets) {
            List<QuestionReturnDTO> questionReturnDTOS = new ArrayList<>();
            for(Question question : questionSet.getQuestions()) {
                QuestionReturnDTO questionReturnDTO = new QuestionReturnDTO(question.getId(), question.getContents());
                questionReturnDTOS.add(questionReturnDTO);
            }
            QuestionSetReturnDTO questionSetReturnDTO = QuestionSetReturnDTO.builder()
                    .username(questionSet.getSiteUser().getUsername())
                    .isOpen(questionSet.isOpen())
                    .imageUrl(questionSet.getSiteUser().getImageUrl())
                    .nickname(questionSet.getSiteUser().getNickname())
                    .refCount(questionSet.getRefCount())
                    .title(questionSet.getTitle())
                    .description(questionSet.getDescription())
                    .id(questionSet.getId())
                    .questions(questionReturnDTOS)
                    .category(questionSet.getCategory())
                    .createdAt(questionSet.getCreatedAt())
                    .build();
            questionSetReturnDTOS.add(questionSetReturnDTO);
        }
        return questionSetReturnDTOS;
    }

    public QuestionSetReturnDTO updateQuestionSet(Long setId, QuestionSetUpdateDTO questionSetUpdateDTO) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Optional<QuestionSet> questionSet = questionSetRepository.findById(setId);
        QuestionSet target;
        QuestionSet updatedSet;
        QuestionSetReturnDTO questionSetReturnDTO;
        if(questionSet.isPresent()) {
            target = questionSet.get();
            if(!target.getSiteUser().getUsername().equals(siteUser.getUsername())) {
                throw new Exception("수정 권한이 없습니다.");
            } else {
                target.setTitle(questionSetUpdateDTO.getTitle());
                target.setDescription(questionSetUpdateDTO.getDescription());
                target.setCategory(questionSetUpdateDTO.getCategory());
                target.setOpen(questionSetUpdateDTO.getIsOpen());
                updatedSet = questionSetRepository.save(target);
                questionSetReturnDTO = setDTO(updatedSet);
                return questionSetReturnDTO;
            }
        } else {
            throw new QuestionSetNotFoundException("면접세트를 찾을 수 없습니다.");
        }
    }

    public QuestionSetReturnDTO setDTO(QuestionSet questionSet) {
        QuestionSetReturnDTO questionSetReturnDTO;
        List<QuestionReturnDTO> questionReturnDTOS = new ArrayList<>();
        for(Question question : questionSet.getQuestions()) {
            QuestionReturnDTO questionReturnDTO = new QuestionReturnDTO(question.getId(), question.getContents());
            questionReturnDTOS.add(questionReturnDTO);
        }
        questionSetReturnDTO = QuestionSetReturnDTO.builder()
                .id(questionSet.getId())
                .username(questionSet.getSiteUser().getUsername())
                .nickname(questionSet.getSiteUser().getNickname())
                .imageUrl(questionSet.getSiteUser().getImageUrl())
                .title(questionSet.getTitle())
                .description(questionSet.getDescription())
                .category(questionSet.getCategory())
                .isOpen(questionSet.isOpen())
                .questions(questionReturnDTOS)
                .refCount(questionSet.getRefCount())
                .createdAt(questionSet.getCreatedAt())
                .build();
        return questionSetReturnDTO;
    }

    public void delete(Long setId) throws Exception {
        Optional<QuestionSet> selectedSet = questionSetRepository.findById(setId);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        if(selectedSet.isPresent()) {
            QuestionSet questionSet = selectedSet.get();
            if(!siteUser.getUsername().equals(questionSet.getSiteUser().getUsername())) {
                throw new Exception("삭제 권한이 없습니다.");
            } else {
                questionSetRepository.delete(questionSet);
            }
        } else {
            throw new QuestionSetNotFoundException("면접세트를 찾을 수 없습니다.");
        }
    }

    public QuestionSetReturnDTO bringNew(Long fromId, List<Long> questionIds) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        QuestionSet fromSet = questionSetRepository.findById(fromId).get();
        QuestionSet questionSet = QuestionSet.builder()
                .category(fromSet.getCategory())
                .title(fromSet.getTitle() + " 복사본")
                .description(fromSet.getDescription())
                .siteUser(siteUser)
                .isOpen(true)
                .refCount(0)
                .build();
        List<Question> questions = new ArrayList<>();
        for(Long id : questionIds) {
            Question question = Question.builder()
                            .questionSet(questionSet)
                            .contents(questionRepository.findByQuestionSetIdAndId(fromId, id).get().getContents())
                            .build();
            questions.add(question);
        }
        questionSet.setQuestions(questions);

        QuestionSet newSet = questionSetRepository.save(questionSet);
        QuestionSetReturnDTO questionSetReturnDTO = setDTO(newSet);
        fromSet.setRefCount(fromSet.getRefCount() + 1);
        questionSetRepository.save(fromSet);
        return questionSetReturnDTO;
    }

    public void bringN(Long fromId, List<Long> questionIds, List<Long> toIds) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        QuestionSet fromSet = questionSetRepository.findById(fromId).get();
        for(Long setId : toIds) {
            QuestionSet toSet = questionSetRepository.findByIdWithQuestions(setId).get();
            List<Question> questions = toSet.getQuestions();
            for(Long questionId : questionIds) {
                Question newQuestion = Question.builder()
                        .questionSet(toSet)
                        .contents(questionRepository.findByQuestionSetIdAndId(fromId, questionId).get().getContents())
                        .build();
                questions.add(newQuestion);
            }
            questionSetRepository.save(toSet);
        }
        fromSet.setRefCount(fromSet.getRefCount() + toIds.size());
        questionSetRepository.save(fromSet);
    }
}
