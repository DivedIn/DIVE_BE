package com.site.xidong.domain.comment.controller;

import com.site.xidong.domain.comment.dto.CommentReturnDTO;
import com.site.xidong.domain.comment.service.CommentService;
import com.site.xidong.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/comment")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/{videoId}/create")
    public ResponseEntity<ApiResponse<CommentReturnDTO>> create(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long videoId,
            @RequestBody String contents) {
        return ResponseEntity.ok(ApiResponse.success(commentService.create(videoId, ud.getUsername(), contents)));
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<List<CommentReturnDTO>>> getComments(@PathVariable Long videoId) {
        return ResponseEntity.ok(ApiResponse.success(commentService.findAllComment(videoId)));
    }

    @PutMapping("/{videoId}/{commentId}/update")
    public ResponseEntity<ApiResponse<CommentReturnDTO>> update(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long videoId,
            @PathVariable Long commentId,
            @RequestBody String contents) {
        return ResponseEntity.ok(ApiResponse.success(commentService.update(videoId, commentId, ud.getUsername(), contents)));
    }

    @DeleteMapping("/{videoId}/{commentId}/delete")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long videoId,
            @PathVariable Long commentId) {
        commentService.delete(videoId, commentId, ud.getUsername());
        return ResponseEntity.noContent().build();
    }
}
