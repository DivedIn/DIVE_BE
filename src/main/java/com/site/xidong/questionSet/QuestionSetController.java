package com.site.xidong.questionSet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/questionSet")
@Slf4j
public class QuestionSetController {
    private final QuestionSetService questionSetService;

    @GetMapping("/all")
    public List<QuestionSetReturnDTO> getQuestionSets() {
        List<QuestionSetReturnDTO> questionSetReturnDTOS = questionSetService.findAll();
        return questionSetReturnDTOS;
    }

    @PostMapping("/create")
    public ResponseEntity<QuestionSetReturnDTO> create(QuestionSetCreateDTO questionSetCreateDTO) {
        QuestionSetReturnDTO questionSetReturnDTO = questionSetService.create(questionSetCreateDTO);
        return ResponseEntity.status(HttpStatus.OK).body(questionSetReturnDTO);
    }

    @GetMapping("/mySets")
    public List<QuestionSetReturnDTO> getMyQuestionSets() {
        List<QuestionSetReturnDTO> questionSetReturnDTOS = questionSetService.findMySets();
        return questionSetReturnDTOS;
    }

    @PutMapping("/{setId}/update")
    public ResponseEntity<QuestionSetReturnDTO> updateQuestionSet(
            @PathVariable Long setId,
            @RequestBody QuestionSetUpdateDTO questionSetUpdateDTO) {

        QuestionSetReturnDTO questionSetReturnDTO;
        try {
            questionSetReturnDTO = questionSetService.updateQuestionSet(setId, questionSetUpdateDTO);
        } catch (QuestionSetNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.status(HttpStatus.OK).body(questionSetReturnDTO);
    }

    @DeleteMapping("/{setId}/delete")
    public ResponseEntity<?> delete(@PathVariable Long setId) throws Exception {
        try {
            questionSetService.delete(setId);
        } catch(QuestionSetNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/{fromId}/new")
    public ResponseEntity<QuestionSetReturnDTO> bringNew(@PathVariable Long fromId, @RequestBody List<Long> questionIds) {
        QuestionSetReturnDTO questionSetReturnDTO = questionSetService.bringNew(fromId, questionIds);
        return ResponseEntity.status(HttpStatus.OK).body(questionSetReturnDTO);
    }

    @PostMapping("/{fromId}")
    public ResponseEntity<?> bringN(@PathVariable Long fromId, @RequestBody BringNRequest request) {
        questionSetService.bringN(fromId, request.getQuestionIds(), request.getToIds());
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
