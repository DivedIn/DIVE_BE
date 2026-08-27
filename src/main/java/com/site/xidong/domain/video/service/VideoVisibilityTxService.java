package com.site.xidong.domain.video.service;

import com.site.xidong.domain.video.dto.VideoReturnDTO;
import com.site.xidong.domain.video.entity.Video;
import com.site.xidong.domain.video.repository.VideoRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * [낙관적 락] changeVisibility()의 "시도 한 번"을 담당하는 별도 빈.
 *
 * 재시도는 반드시 완전히 새 트랜잭션(REQUIRES_NEW)에서 돌아야 한다 — 같은 트랜잭션 안에서
 * 다시 findById()를 불러봤자 영속성 컨텍스트(1차 캐시)가 이미 실패했던 그 엔티티 인스턴스를
 * 그대로 돌려주므로 "다시 읽기"가 실질적으로 아무 의미가 없다. VideoProcessingTxService와
 * 같은 이유로 별도 빈으로 뺐다 — REQUIRES_NEW를 같은 클래스 안에서 self-invocation으로
 * 호출하면 프록시를 안 타 무력화된다.
 *
 * saveAndFlush()로 즉시 flush해 버전 충돌을 그 자리에서(트랜잭션 커밋 시점이 아니라) 던지게
 * 한다 — 그래야 changeVisibility()가 예외를 잡아 재시도할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class VideoVisibilityTxService {

    private final VideoRepository videoRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VideoReturnDTO attempt(Long videoId, String username, Boolean isOpen) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new CustomException(ErrorCode.VIDEO_NOT_FOUND));
        if (!video.getSiteUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        video.updateVisibility(isOpen);
        return VideoReturnDTO.from(videoRepository.saveAndFlush(video));
    }
}
