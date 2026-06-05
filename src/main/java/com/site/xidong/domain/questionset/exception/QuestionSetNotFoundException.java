package com.site.xidong.domain.questionset.exception;

import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;

public class QuestionSetNotFoundException extends CustomException {
    public QuestionSetNotFoundException() {
        super(ErrorCode.QUESTION_SET_NOT_FOUND);
    }
}
