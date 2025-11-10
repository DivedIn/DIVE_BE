package com.site.xidong.comment;

import com.site.xidong.notification.CommentNotificationDTO;
import com.site.xidong.notification.NotificationService;
import com.site.xidong.security.SiteUserSecurityDTO;
import com.site.xidong.siteUser.SiteUser;
import com.site.xidong.siteUser.SiteUserRepository;
import com.site.xidong.video.Video;
import com.site.xidong.video.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final SiteUserRepository siteUserRepository;
    private final VideoRepository videoRepository;
    private final NotificationService notificationService;

    public CommentReturnDTO create(Long videoId, String contents) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Video video = videoRepository.findById(videoId).get();
        Comment comment = Comment.builder()
                .siteUser(siteUser)
                .video(video)
                .contents(contents)
                .createdAt(LocalDateTime.now())
                .build();
        Comment newComment = commentRepository.save(comment);

        // 알림 보내기
        CommentNotificationDTO commentNotificationDTO = CommentNotificationDTO.builder()
                        .videoId(video.getId())
                        .message("새로운 댓글이 달렸습니다: " + newComment.getContents())
                        .commentId(newComment.getId())
                        .build();

        notificationService.send(video.getSiteUser().getUsername(), "new-comment", commentNotificationDTO);

        CommentReturnDTO commentReturnDTO = CommentReturnDTO.builder()
                .commentId(newComment.getId())
                .imageUrl(newComment.getSiteUser().getImageUrl())
                .username(newComment.getSiteUser().getUsername())
                .nickname(newComment.getSiteUser().getNickname())
                .videoPath(newComment.getVideo().getVideoPath())
                .videoName(newComment.getVideo().getVideoName())
                .createdAt(newComment.getCreatedAt())
                .updatedAt(null)
                .contents(newComment.getContents())
                .build();
        return commentReturnDTO;
    }

    public List<CommentReturnDTO> findAllComment(Long videoId) {
        Video video = videoRepository.findById(videoId).get();
        List<Comment> comments = video.getCommentList();
        List<CommentReturnDTO> commentReturnDTOS = new ArrayList<>();
        for (Comment comment : comments) {
            CommentReturnDTO commentReturnDTO = CommentReturnDTO.builder()
                    .commentId(comment.getId())
                    .imageUrl(comment.getSiteUser().getImageUrl())
                    .username(comment.getSiteUser().getUsername())
                    .nickname(comment.getSiteUser().getNickname())
                    .videoPath(comment.getVideo().getVideoPath())
                    .videoName(comment.getVideo().getVideoName())
                    .createdAt(comment.getCreatedAt())
                    .updatedAt(comment.getUpdatedAt())
                    .contents(comment.getContents())
                    .build();
            commentReturnDTOS.add(commentReturnDTO);
        }
        return commentReturnDTOS;
    }

    public CommentReturnDTO update(Long videoId, Long commentId, String contents) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Comment comment = commentRepository.findCommentByVideoId(videoId, commentId);
        Comment updatedComment;
        CommentReturnDTO commentReturnDTO;
        if (!comment.getSiteUser().getUsername().equals(siteUser.getUsername())) {
            throw new Exception("수정 권한이 없습니다.");
        } else {
            comment.setContents(contents);
            comment.setUpdatedAt(LocalDateTime.now());
            updatedComment = commentRepository.save(comment);
            commentReturnDTO = CommentReturnDTO.builder()
                    .commentId(updatedComment.getId())
                    .imageUrl(updatedComment.getSiteUser().getImageUrl())
                    .username(updatedComment.getSiteUser().getUsername())
                    .nickname(updatedComment.getSiteUser().getNickname())
                    .videoPath(updatedComment.getVideo().getVideoPath())
                    .videoName(updatedComment.getVideo().getVideoName())
                    .createdAt(updatedComment.getCreatedAt())
                    .updatedAt(updatedComment.getUpdatedAt())
                    .contents(updatedComment.getContents())
                    .build();
        }
        return commentReturnDTO;
    }

    public void delete(Long videoId, Long commentId, String contents) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Comment comment = commentRepository.findCommentByVideoId(videoId, commentId);
        if (!comment.getSiteUser().getUsername().equals(siteUser.getUsername())) {
            throw new Exception("삭제 권한이 없습니다.");
        } else {
            commentRepository.delete(comment);
        }
    }
}
