package com.site.xidong.domain.question.controller;

import com.site.xidong.domain.questionset.exception.QuestionSetNotFoundException;
import com.site.xidong.domain.questionset.dto.QuestionSetReturnDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.site.xidong.domain.question.service.QuestionService;
import com.site.xidong.domain.question.dto.QuestionReturnDTO;
import com.site.xidong.domain.question.exception.QuestionNotFoundException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {
    private final QuestionService questionService;

    @PostMapping("/{setId}/create")
    public ResponseEntity<QuestionReturnDTO> create(@PathVariable Long setId, String contents) {
        QuestionReturnDTO questionReturnDTO;
        try {
           questionReturnDTO = questionService.create(setId, contents);
        } catch (QuestionSetNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.status(HttpStatus.OK).body(questionReturnDTO);
    }

    @PutMapping("/{setId}/{id}/update")
    public ResponseEntity<QuestionReturnDTO> update(@PathVariable Long setId, @PathVariable Long id, String contents)  {
        QuestionReturnDTO questionReturnDTO;
        try {
            questionReturnDTO = questionService.update(setId, id, contents);

        } catch (QuestionNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e){
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.status(HttpStatus.OK).body(questionReturnDTO);
    }

    @DeleteMapping("/{setId}/delete")
    public ResponseEntity<?> delete(@PathVariable Long setId, @RequestBody List<Long> questionIds) {
        try {
            questionService.delete(setId, questionIds);
        } catch (QuestionNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
