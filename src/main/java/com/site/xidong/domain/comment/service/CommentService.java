package com.site.xidong.domain.comment.service;

import com.site.xidong.domain.comment.dto.CommentReturnDTO;
import com.site.xidong.domain.comment.entity.Comment;
import com.site.xidong.domain.comment.repository.CommentRepository;
import com.site.xidong.domain.notification.dto.CommentNotificationDTO;
import com.site.xidong.domain.notification.service.NotificationService;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.user.repository.SiteUserRepository;
import com.site.xidong.domain.video.entity.Video;
import com.site.xidong.domain.video.repository.VideoRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final SiteUserRepository siteUserRepository;
    private final VideoRepository videoRepository;
    private final NotificationService notificationService;

    @Transactional
    public CommentReturnDTO create(Long videoId, String username, String contents) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new CustomException(ErrorCode.VIDEO_NOT_FOUND));

        Comment comment = Comment.builder()
                .siteUser(siteUser)
                .video(video)
                .contents(contents)
                .build();
        Comment saved = commentRepository.save(comment);

        notificationService.send(video.getSiteUser().getUsername(), "new-comment",
                CommentNotificationDTO.builder()
                        .videoId(video.getId())
                        .message("새로운 댓글이 달렸습니다: " + saved.getContents())
                        .commentId(saved.getId())
                        .build());

        return CommentReturnDTO.from(saved);
    }

    public List<CommentReturnDTO> findAllComment(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new CustomException(ErrorCode.VIDEO_NOT_FOUND));
        return video.getCommentList().stream()
                .map(CommentReturnDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentReturnDTO update(Long videoId, Long commentId, String username, String contents) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Comment comment = commentRepository.findCommentByVideoId(videoId, commentId);
        if (!comment.getSiteUser().getUsername().equals(siteUser.getUsername())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        comment.update(contents);
        return CommentReturnDTO.from(comment);
    }

    @Transactional
    public void delete(Long videoId, Long commentId, String username) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Comment comment = commentRepository.findCommentByVideoId(videoId, commentId);
        if (!comment.getSiteUser().getUsername().equals(siteUser.getUsername())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        commentRepository.delete(comment);
    }
}
