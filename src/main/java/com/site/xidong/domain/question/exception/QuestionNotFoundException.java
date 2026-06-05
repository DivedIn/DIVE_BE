package com.site.xidong.domain.question.exception;

import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;

public class QuestionNotFoundException extends CustomException {
    public QuestionNotFoundException() {
        super(ErrorCode.QUESTION_NOT_FOUND);
    }
}
