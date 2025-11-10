package com.site.xidong.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository <Comment, Long> {
    @Query("SELECT c FROM Comment c WHERE c.video.id = :videoId and c.id = :commentId")
    Comment findCommentByVideoId(Long videoId, Long commentId);
}
