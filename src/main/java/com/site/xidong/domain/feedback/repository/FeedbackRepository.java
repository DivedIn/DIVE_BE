package com.site.xidong.domain.feedback.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.site.xidong.domain.feedback.entity.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

}
