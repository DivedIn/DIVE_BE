package com.site.xidong.questionSet;

import lombok.Getter;

@Getter
public enum Category {
    FE("FE"), BE("BE"), ANDROID("ANDROID"), IOS("IOS"), CS("CS");
    private String category;

    Category(String category) {
        this.category = category;
    }
}
