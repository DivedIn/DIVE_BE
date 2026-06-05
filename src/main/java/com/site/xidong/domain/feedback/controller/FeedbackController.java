package com.site.xidong.domain.feedback.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.site.xidong.domain.feedback.service.FeedbackService;
import com.site.xidong.domain.feedback.dto.FeedbackReturnDTO;
import com.site.xidong.domain.feedback.dto.AnswerDTO;

@RestController
@Log4j2
@RequiredArgsConstructor
@RequestMapping("/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    @PostMapping("/create")
    public FeedbackReturnDTO createFeedback(AnswerDTO answerDTO) {
        FeedbackReturnDTO feedbackDTO = null;
        try {
            feedbackDTO = feedbackService.getFeedback(answerDTO);
        } catch (Exception e) {
            log.error(e);
        }
        return feedbackDTO;
    }
}
