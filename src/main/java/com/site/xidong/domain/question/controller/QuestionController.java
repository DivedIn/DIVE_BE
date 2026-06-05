package com.site.xidong.domain.question.controller;

import com.site.xidong.domain.question.dto.QuestionReturnDTO;
import com.site.xidong.domain.question.service.QuestionService;
import com.site.xidong.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/question")
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping("/{setId}/create")
    public ResponseEntity<ApiResponse<QuestionReturnDTO>> create(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long setId,
            @RequestBody String contents) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(questionService.create(setId, ud.getUsername(), contents)));
    }

    @PutMapping("/{setId}/{id}/update")
    public ResponseEntity<ApiResponse<QuestionReturnDTO>> update(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long setId,
            @PathVariable Long id,
            @RequestBody String contents) {
        return ResponseEntity.ok(ApiResponse.success(questionService.update(setId, id, ud.getUsername(), contents)));
    }

    @DeleteMapping("/{setId}/delete")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long setId,
            @RequestBody List<Long> questionIds) {
        questionService.delete(setId, ud.getUsername(), questionIds);
        return ResponseEntity.noContent().build();
    }
}
