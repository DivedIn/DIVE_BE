package com.site.xidong.questionSet;

import lombok.Getter;

import java.util.List;

@Getter
public class BringNRequest {
    private List<Long> questionIds;
    private List<Long> toIds;
}
