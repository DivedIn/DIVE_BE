package com.site.xidong.video;

import lombok.Data;

@Data
public class VideoUploadCompleteRequest {
    private Long questionId;
    private String videoKey;
    private int requestNo;
    private boolean isOpen;
}
