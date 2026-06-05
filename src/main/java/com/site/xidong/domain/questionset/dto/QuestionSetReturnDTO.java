package com.site.xidong.domain.questionset.dto;

import com.site.xidong.domain.question.dto.QuestionReturnDTO;
import com.site.xidong.domain.questionset.entity.QuestionSet;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class QuestionSetReturnDTO {
    private Long id;
    private String imageUrl;
    private String username;
    private String nickname;
    private int refCount;
    private String title;
    private String description;
    private String category;
    private boolean isOpen;
    private List<QuestionReturnDTO> questions;
    private LocalDateTime createdAt;

    public static QuestionSetReturnDTO from(QuestionSet qs) {
        return QuestionSetReturnDTO.builder()
                .id(qs.getId())
                .imageUrl(qs.getSiteUser().getImageUrl())
                .username(qs.getSiteUser().getUsername())
                .nickname(qs.getSiteUser().getNickname())
                .refCount(qs.getRefCount())
                .title(qs.getTitle())
                .description(qs.getDescription())
                .category(qs.getCategory())
                .isOpen(qs.isOpen())
                .questions(qs.getQuestions().stream()
                        .map(QuestionReturnDTO::from)
                        .collect(Collectors.toList()))
                .createdAt(qs.getCreatedAt())
                .build();
    }
}
