package com.site.xidong.feedback;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Log4j2
@RequiredArgsConstructor
@RequestMapping("/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    @PostMapping("/create")
    public FeedbackReturnDTO createFeedback(AnswerDTO answerDTO) throws Exception {
        FeedbackReturnDTO feedbackDTO = null;
        try {
            feedbackDTO = feedbackService.getFeedback(answerDTO);
        } catch (Exception e) {
            log.error(e);
        }
        return feedbackDTO;
    }
}
