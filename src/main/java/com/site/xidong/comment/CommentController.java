package com.site.xidong.comment;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Log4j2
@RequiredArgsConstructor
@RequestMapping("/comment")
public class CommentController {
    private final CommentService commentService;

    @PostMapping("/{videoId}/create")
    public ResponseEntity<CommentReturnDTO> create(@PathVariable Long videoId, @RequestBody String contents) {
        CommentReturnDTO commentReturnDTO = commentService.create(videoId, contents);
        return ResponseEntity.status(HttpStatus.OK).body(commentReturnDTO);
    }

    @GetMapping("/{videoId}")
    public List<CommentReturnDTO> getComments(@PathVariable Long videoId) {
        List<CommentReturnDTO> commentReturnDTOS = commentService.findAllComment(videoId);
        return commentReturnDTOS;
    }

    @PutMapping("/{videoId}/{commentId}/update")
    public ResponseEntity<CommentReturnDTO> update(@PathVariable Long videoId, @PathVariable Long commentId, @RequestBody String contents) throws Exception {
        CommentReturnDTO commentReturnDTO;
        try {
            commentReturnDTO = commentService.update(videoId, commentId, contents);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.status(HttpStatus.OK).body(commentReturnDTO);
    }

    @DeleteMapping("/{videoId}/{commentId}/delete")
    public ResponseEntity<CommentReturnDTO> delete(@PathVariable Long videoId, @PathVariable Long commentId, @RequestBody String contents) throws Exception {
        try {
            commentService.delete(videoId, commentId, contents);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
