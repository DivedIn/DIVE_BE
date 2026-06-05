package com.site.xidong.domain.feedback.controller;

import com.site.xidong.domain.feedback.dto.AnswerDTO;
import com.site.xidong.domain.feedback.dto.FeedbackReturnDTO;
import com.site.xidong.domain.feedback.service.FeedbackService;
import com.site.xidong.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<FeedbackReturnDTO>> createFeedback(@RequestBody AnswerDTO answerDTO) {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.getFeedback(answerDTO)));
    }
}
