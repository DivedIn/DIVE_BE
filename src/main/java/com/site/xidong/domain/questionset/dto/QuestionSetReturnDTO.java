package com.site.xidong.domain.questionset.dto;

import com.site.xidong.domain.question.dto.QuestionReturnDTO;
import com.site.xidong.domain.questionset.entity.QuestionSet;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

// [캐시] @Jacksonized 없이는 Jackson이 이 DTO를 역직렬화할 방법이 없다(기본 생성자도,
// Jackson이 알아볼 생성자도 없음) — REST 응답으로 내보내기(직렬화)만 하던 시절엔 문제가
// 안 됐는데, Redis 캐시에서 다시 읽어올 때(역직렬화)는 바로 예외였다. 캐시를 실제로
// 태워보고서야 발견했다. QuestionReturnDTO(중첩 필드)도 동일한 이유로 같이 붙였다.
//
// [캐시] 필드명은 isOpen이 아니라 open으로 뒀다 — boolean 필드의 Lombok getter는 항상
// isXxx() 형태라 Jackson이 직렬화할 때 JSON 프로퍼티명은 "is" 접두어를 뗀 "open"이 된다
// (필드명이 isOpen이든 open이든 getter는 isOpen()으로 동일). 그런데 @Jacksonized가 만드는
// 빌더 메서드명은 "필드명 그대로"라서, 필드명을 isOpen으로 두면 빌더엔 isOpen(...)만 있고
// JSON엔 "open"이 찍혀 역직렬화가 "Unrecognized field open"으로 죽는다. 필드명을 open으로
// 맞추면 getter(isOpen)·JSON 프로퍼티명(open)·빌더 메서드명(open)이 전부 앞뒤가 맞는다.
@Getter
@Builder
@Jacksonized
public class QuestionSetReturnDTO {
    private Long id;
    private String imageUrl;
    private String username;
    private String nickname;
    private int refCount;
    private String title;
    private String description;
    private String category;
    private boolean open;
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
                .open(qs.isOpen())
                .questions(qs.getQuestions().stream()
                        .map(QuestionReturnDTO::from)
                        .collect(Collectors.toList()))
                .createdAt(qs.getCreatedAt())
                .build();
    }
}
