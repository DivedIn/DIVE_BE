package com.site.xidong.feedback;

import lombok.Getter;

@Getter
public enum Status {

    DONE("DONE"), INCOMPLETE("INCOMPLETE");
    private String status;

    Status(String status) {
        this.status = status;
    }
}
