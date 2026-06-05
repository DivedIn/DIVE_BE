package com.site.xidong.domain.questionset.controller;

import com.site.xidong.domain.questionset.dto.BringNRequest;
import com.site.xidong.domain.questionset.dto.QuestionSetCreateDTO;
import com.site.xidong.domain.questionset.dto.QuestionSetReturnDTO;
import com.site.xidong.domain.questionset.dto.QuestionSetUpdateDTO;
import com.site.xidong.domain.questionset.service.QuestionSetService;
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
@RequestMapping("/questionSet")
public class QuestionSetController {

    private final QuestionSetService questionSetService;

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<QuestionSetReturnDTO>>> getQuestionSets() {
        return ResponseEntity.ok(ApiResponse.success(questionSetService.findAll()));
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<QuestionSetReturnDTO>> create(
            @AuthenticationPrincipal UserDetails ud,
            @RequestBody QuestionSetCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(questionSetService.create(ud.getUsername(), dto)));
    }

    @GetMapping("/mySets")
    public ResponseEntity<ApiResponse<List<QuestionSetReturnDTO>>> getMyQuestionSets(
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(ApiResponse.success(questionSetService.findMySets(ud.getUsername())));
    }

    @PutMapping("/{setId}/update")
    public ResponseEntity<ApiResponse<QuestionSetReturnDTO>> updateQuestionSet(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long setId,
            @RequestBody QuestionSetUpdateDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(questionSetService.updateQuestionSet(setId, ud.getUsername(), dto)));
    }

    @DeleteMapping("/{setId}/delete")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long setId) {
        questionSetService.delete(setId, ud.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{fromId}/new")
    public ResponseEntity<ApiResponse<QuestionSetReturnDTO>> bringNew(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long fromId,
            @RequestBody List<Long> questionIds) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(questionSetService.bringNew(fromId, ud.getUsername(), questionIds)));
    }

    @PostMapping("/{fromId}")
    public ResponseEntity<Void> bringN(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long fromId,
            @RequestBody BringNRequest request) {
        questionSetService.bringN(fromId, ud.getUsername(), request.getQuestionIds(), request.getToIds());
        return ResponseEntity.ok().build();
    }
}
