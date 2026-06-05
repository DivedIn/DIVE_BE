package com.site.xidong.domain.video.dto;

import com.site.xidong.domain.feedback.dto.FeedbackReturnDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoWithFeedbackDTO {

    private VideoReturnDTO video;
    private FeedbackReturnDTO feedback;

}