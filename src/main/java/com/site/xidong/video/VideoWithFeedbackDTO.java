package com.site.xidong.video;

import com.site.xidong.feedback.FeedbackReturnDTO;
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