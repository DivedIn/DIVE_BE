package com.site.xidong.domain.video.dto;

import lombok.Data;

@Data
public class VideoUploadCompleteRequest {
    private Long questionId;
    private String videoKey;
    private int requestNo;
    private boolean isOpen;
}
