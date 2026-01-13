package com.site.xidong.video;

import com.site.xidong.feedback.*;
import com.site.xidong.notification.NotificationService;
import com.site.xidong.notification.VideoNotificationDTO;
import com.site.xidong.question.Question;
import com.site.xidong.question.QuestionNotFoundException;
import com.site.xidong.question.QuestionRepository;
import com.site.xidong.queue.VideoProcessingQueue;
import com.site.xidong.queue.VideoProcessingQueueRepository;
import com.site.xidong.security.SiteUserSecurityDTO;
import com.site.xidong.siteUser.SiteUser;
import com.site.xidong.siteUser.SiteUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VideoService {

    private static final String DEFAULT_THUMBNAIL_URL = "https://dive-s3-ver2.s3.ap-northeast-2.amazonaws.com/Gk9C7kwWkAATlwl.jpeg";
    private static final int CORE_POOL_SIZE = 10;
    private static final int QUEUE_CAPACITY = 50;
    private static final double CAPACITY_THRESHOLD = 0.8;
    private final VideoRepository videoRepository;
    private final SiteUserRepository siteUserRepository;
    private final QuestionRepository questionRepository;
    private final AwsTranscribe awsTranscribe;
    private final FeedbackService feedbackService;
    private final NotificationService notificationService;
    private final VideoProcessingQueueRepository queueRepository;
    private final LocalWhisperService localWhisperService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor videoProcessingExecutor;

    @Autowired @Lazy
    private VideoService self;

    @Autowired
    private ThreadPoolMonitor threadPoolMonitor;

    private String getS3UrlPrefix() {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }

    @Async("videoProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> createInitialAsync(Long questionId, String videoKey, Boolean isOpen, long startTime, boolean presigned) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            SiteUserSecurityDTO userDetails = (SiteUserSecurityDTO) auth.getPrincipal();
            SiteUser user = siteUserRepository.findSiteUserByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new QuestionNotFoundException());

            // 비디오 URL 생성
            String videoUrl = String.format("%s/%s", getS3UrlPrefix(), videoKey);

            // Step 5: Video 객체 생성 및 저장
            Video video = Video.builder()
                    .videoPath(videoUrl)
                    .videoName(videoKey)
                    .siteUser(user)
                    .question(question)
                    .createdAt(LocalDateTime.now())
                    .isOpen(isOpen)
                    .processingStatus("PROCESSING")
                    .build();

            Video savedVideo = videoRepository.save(video);
            log.info("비디오 초기 저장 완료: ID={}", savedVideo.getId());

            self.processVideoAsync(savedVideo.getId(), videoKey, user.getUsername(), startTime, presigned);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("비디오 초기 처리 실패", e);
            throw new RuntimeException("비디오 초기 처리에 실패했습니다", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VideoReturnDTO createInitialSync(Long questionId, String videoKey, Boolean isOpen, long startTime) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            SiteUserSecurityDTO userDetails = (SiteUserSecurityDTO) auth.getPrincipal();
            SiteUser user = siteUserRepository.findSiteUserByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new QuestionNotFoundException());

            // 비디오 URL 생성
            String videoUrl = String.format("%s/%s", getS3UrlPrefix(), videoKey);

            // Step 5: Video 객체 생성 및 저장
            Video video = Video.builder()
                    .videoPath(videoUrl)
                    .videoName(videoKey)
                    .siteUser(user)
                    .question(question)
                    .createdAt(LocalDateTime.now())
                    .isOpen(isOpen)
                    .processingStatus("PROCESSING")
                    .build();

            Video savedVideo = videoRepository.save(video);
            log.info("비디오 초기 저장 완료: ID={}", savedVideo.getId());

            self.processVideoSync(savedVideo.getId(), videoKey, user.getUsername(), startTime);

            VideoReturnDTO videoReturnDTO = VideoReturnDTO.builder()
                    .videoId(video.getId())
                    .videoPath(video.getVideoPath())
                    .videoName(video.getVideoName())
                    .imageUrl(video.getSiteUser().getImageUrl())
                    .username(video.getSiteUser().getUsername())
                    .nickname(video.getSiteUser().getNickname())
                    .thumbnail(video.getThumbnail())
                    .question(video.getQuestion().getContents())
                    .category(video.getQuestion().getQuestionSet().getCategory())
                    .createdAt(video.getCreatedAt())
                    .updatedAt(video.getUpdatedAt())
                    .isOpen(video.isOpen())
                    .build();
            return videoReturnDTO;

        } catch (Exception e) {
            log.error("비디오 초기 처리 실패", e);
            throw new RuntimeException("비디오 초기 처리에 실패했습니다", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED) // 트랜잭션 전파 기법: 기존 트랜잭션 사용
    public void processVideoAsync(Long videoId, String videoKey, String username, long startTime, boolean presigned) {

        log.info("=== 영상 처리 비동기 작업 시작 - Thread: {}, videoId: {}, Queue size: {} ===",
                Thread.currentThread().getName(), videoId, videoProcessingExecutor.getQueueSize()); //TODO: 톰캣 스레드 말고 스프링 스레드 이름 출력해야함

        try {
            threadPoolMonitor.logThreadPoolStatus("영상 처리 작업 시작");
            // S3 클라이언트 초기화
            AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
            S3Client s3Client = S3Client.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(credentialsProvider)
                    .build();

            // 비디오 길이 확인
            long start = System.currentTimeMillis();
            double durationInSeconds = getVideoDurationFromS3(s3Client, bucket, videoKey); // FFmpeg로 길이 확인
            boolean isLongVideo = durationInSeconds > 300; // 5분 이상
            long end = System.currentTimeMillis();
            long duration = end - start;
            log.info("비디오 길이 확인 소요 시간: {}ms", duration);

            byte[] videoBytes = new byte[0];
            if (!presigned) {
                start = System.currentTimeMillis();
                log.info("S3에서 비디오 파일 다운로드 시작: {}", videoKey);
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(videoKey)
                        .build();

                ResponseInputStream<?> s3Object = s3Client.getObject(getObjectRequest);
                InputStream videoStream = s3Object;  // ResponseInputStream는 InputStream을 상속
                videoBytes = IOUtils.toByteArray(videoStream);
                log.info("S3에서 비디오 파일 다운로드 완료: {} bytes", videoBytes.length);
                end = System.currentTimeMillis();
                duration = end - start;
                log.info("영상 파일 다운로드 소요 시간: {}ms", duration);
            }

            // 썸네일 생성 (Presigned URL 사용)
            start = System.currentTimeMillis();
            String thumbnailKey = videoKey.replace(".webm", "-thumb.jpg");
            String thumbnailUrl = "";
            if (presigned) {
                thumbnailUrl = createThumbnailWithPresignedUrl(s3Client, videoKey, thumbnailKey);
            } else {
                thumbnailUrl = createThumbnail(videoBytes, thumbnailKey, s3Client);
            }
            log.info("썸네일 생성 및 업로드 완료: {}", thumbnailUrl);
            end = System.currentTimeMillis();
            duration = end - start;
            log.info("썸네일 생성 소요 시간: {}ms", duration);

            // 비디오 상태 업데이트
            updateVideoThumbnailAndStatus(videoId, thumbnailUrl);

            // 오디오 처리
            String answer = "";
            if (isLongVideo) {
                log.info("긴 영상 처리: {} 초", durationInSeconds);
                answer = processLongVideoWithPresignedUrl(s3Client, videoId, videoKey, durationInSeconds);
            } else {
                log.info("짧은 영상 처리: {} 초", durationInSeconds);
                if (presigned) {
                    answer = processShortVideoWithPresignedUrl(s3Client, videoId, videoKey);
                } else {
                    log.info("짧은 영상 처리 시작: 비디오 ID {}", videoId);
                    answer = processShortVideo(videoBytes);
                }
            }

            // 답변 유효성 검사
            boolean isValidAnswer = isValidAnswer(answer);

            log.info("답변 유효성 검사 결과: {}, 정제된 텍스트 길이: {}",
                    isValidAnswer,
                    answer != null ? answer.trim().length() : 0);

            if (!isValidAnswer) {
                log.warn("비디오 ID: {} 유효한 답변이 없습니다. 원본 답변: '{}'", videoId, answer);

                handleInvalidAnswer(videoId, username, answer);
                return; // 함수 종료
            }

            // 피드백 처리 및 최종 업데이트
            handleValidAnswer(videoId, username, answer);


            long endTime = System.currentTimeMillis(); // End time
            long durationMs = endTime - startTime; // Total duration
            log.info("=== 영상 처리 비동기 작업 완료 - Thread: {}, videoId: {}, Queue size: {}, 총 소요 시간: {}ms ===",
                    Thread.currentThread().getName(), videoId, videoProcessingExecutor.getQueueSize(), durationMs);
            threadPoolMonitor.logThreadPoolStatus("작업 완료");
        } catch (Exception e) {
            log.error("비디오 ID: {} 비동기 처리 중 오류 발생", videoId, e);
            handleError(videoId, username);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED) // 트랜잭션 전파 기법: 기존 트랜잭션 사용
    public void processVideoSync(Long videoId, String videoKey, String username, long startTime) {

        log.info("=== 영상 처리 동기 작업 시작 - Thread: {}, videoId: {} ===",
                Thread.currentThread().getName(), videoId); //TODO: 톰캣 스레드 이름 출력해야함

        try {
            // S3 클라이언트 초기화
            AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
            S3Client s3Client = S3Client.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(credentialsProvider)
                    .build();

            // 비디오 길이 확인
            long start = System.currentTimeMillis();
            double durationInSeconds = getVideoDurationFromS3(s3Client, bucket, videoKey); // FFmpeg로 길이 확인
            boolean isLongVideo = durationInSeconds > 300; // 5분 이상
            long end = System.currentTimeMillis();
            long duration = end - start;
            log.info("비디오 길이 확인 소요 시간: {}ms", duration);

            // 썸네일 생성 (Presigned URL 사용)
            start = System.currentTimeMillis();
            String thumbnailKey = videoKey.replace(".webm", "-thumb.jpg");
            String thumbnailUrl = createThumbnailWithPresignedUrl(s3Client, videoKey, thumbnailKey);
            log.info("썸네일 생성 및 업로드 완료: {}", thumbnailUrl);
            end = System.currentTimeMillis();
            duration = end - start;
            log.info("썸네일 생성 소요 시간: {}ms", duration);

            // 비디오 상태 업데이트
            updateVideoThumbnailAndStatus(videoId, thumbnailUrl);

            // 오디오 처리
            String answer;
            if (isLongVideo) {
                log.info("긴 영상 처리: {} 초", durationInSeconds);
                answer = processLongVideoWithPresignedUrl(s3Client, videoId, videoKey, durationInSeconds);
            } else {
                log.info("짧은 영상 처리: {} 초", durationInSeconds);
                answer = processShortVideoWithPresignedUrl(s3Client, videoId, videoKey);
            }

            // 답변 유효성 검사
            boolean isValidAnswer = isValidAnswer(answer);

            log.info("답변 유효성 검사 결과: {}, 정제된 텍스트 길이: {}",
                    isValidAnswer,
                    answer != null ? answer.trim().length() : 0);

            if (!isValidAnswer) {
                log.warn("비디오 ID: {} 유효한 답변이 없습니다. 원본 답변: '{}'", videoId, answer);

                handleInvalidAnswer(videoId, username, answer);
                return; // 함수 종료
            }

            // 피드백 처리 및 최종 업데이트
            handleValidAnswer(videoId, username, answer);


            long endTime = System.currentTimeMillis(); // End time
            long durationMs = endTime - startTime; // Total duration
            log.info("=== 영상 처리 동기 작업 완료 - Thread: {}, videoId: {}, 총 소요 시간: {}ms ===",
                    Thread.currentThread().getName(), videoId, durationMs);

        } catch (Exception e) {
            log.error("비디오 ID: {} 동기 처리 중 오류 발생", videoId, e);
            handleError(videoId, username);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateVideoThumbnailAndStatus(Long videoId, String thumbnailUrl) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found with id: " + videoId));
        video.setThumbnail(thumbnailUrl);
        video.setProcessingStatus("TRANSCRIBING");
        videoRepository.save(video);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleInvalidAnswer(Long videoId, String username, String answer) {
        log.warn("비디오 ID: {} 유효한 답변이 없습니다. 원본 답변: '{}'", videoId, answer);
        Video video = videoRepository.findById(videoId).orElse(null);
        if (video != null) {
            video.setProcessingStatus("NO_RESPONSE");
            videoRepository.save(video);
            VideoNotificationDTO noResponseData = VideoNotificationDTO.builder()
                    .videoId(videoId)
                    .status("NO_RESPONSE")
                    .message("녹화된 답변이 감지되지 않았습니다. 다시 시도해주세요.")
                    .build();
            notificationService.send(username, "video-processed", noResponseData);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleValidAnswer(Long videoId, String username, String answer) throws Exception {
        long start = System.currentTimeMillis();
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found with id: " + videoId));
        AnswerDTO answerDTO = AnswerDTO.builder()
                .videoId(videoId)
                .answer(answer)
                .build();
        FeedbackReturnDTO feedbackReturnDTO = feedbackService.getFeedback(answerDTO);
        Feedback feedback = feedbackService.findFeedback(feedbackReturnDTO.getFeedbackId());
        video.setProcessingStatus("COMPLETED");
        video.setFeedback(feedback);
        videoRepository.save(video);

        VideoNotificationDTO notification = VideoNotificationDTO.builder()
                .videoId(videoId)
                .status("COMPLETED")
                .message("비디오 처리가 완료되었습니다.")
                .feedbackId(feedbackReturnDTO.getFeedbackId())
                .build();
        notificationService.send(username, "video-processed", notification);
        log.info("비디오 ID: {} 처리 완료 알림 전송됨", videoId);
        long end = System.currentTimeMillis();
        long duration = end - start;
        log.info("피드백 생성 소요 시간: {}ms", duration);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleError(Long videoId, String username) {
        Video video = videoRepository.findById(videoId).orElse(null);
        if (video != null) {
            video.setProcessingStatus("ERROR");
            videoRepository.save(video);
            VideoNotificationDTO errorData = VideoNotificationDTO.builder()
                    .videoId(videoId)
                    .status("ERROR")
                    .message("비디오 처리 중 오류가 발생했습니다.")
                    .build();
            notificationService.send(username, "video-processed", errorData);
        }
    }

    private boolean isValidAnswer(String answer) {
        return answer != null &&
                !answer.trim().isEmpty() &&
                answer.trim().length() >= 10 &&
                !answer.trim().matches("(?i).*\\\\b(background noise|unintelligible|inaudible)\\\\b.*");
    }

    private double getVideoDurationFromS3(S3Client s3Client, String bucket, String videoKey) {
        log.info("비디오 길이 확인 시작 (초고속): {}", videoKey);

        try {
            // 1. HTTP HEAD 요청으로 파일 크기와 메타데이터만 확인 (0.2-1초)
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(videoKey)
                    .build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);

            // 2. 메타데이터에 실제 duration이 있으면 우선 사용 (가장 정확)
            log.info("메타데이터로 비디오 길이 확인");
            Map<String, String> metadata = headResponse.metadata();
            if (metadata.containsKey("duration")) {
                try {
                    double duration = Double.parseDouble(metadata.get("duration"));
                    log.info("메타데이터에서 duration 확인: {}초 ({})", duration, videoKey);
                    return duration;
                } catch (NumberFormatException e) {
                    log.warn("메타데이터 duration 파싱 실패, 추정 방식 사용: {}", videoKey);
                }
            }

            // 3. 파일 크기 기반 duration 추정
            log.info("파일 크기 기반으로 비디오 길이 확인");
            long fileSizeBytes = headResponse.contentLength();
            String contentType = headResponse.contentType();

            if (fileSizeBytes <= 0) {
                log.error("파일 크기를 확인할 수 없음: {}", videoKey);
                return 0;
            }

            // 4. 파일 확장자와 Content-Type으로 예상 비트레이트 결정
            double estimatedBitrateMbps = getEstimatedBitrate(videoKey, contentType);

            // 5. Duration 계산: 파일크기(MB) ÷ 비트레이트(Mbps) = 초
            double fileSizeMB = fileSizeBytes / (1024.0 * 1024.0);
            double estimatedDuration = (fileSizeMB * 8) / estimatedBitrateMbps; // 8 bits per byte

            log.info("초고속 비디오 길이 추정 완료: {}초 (파일: {}MB, 예상비트레이트: {}Mbps) - {}",
                    Math.round(estimatedDuration), Math.round(fileSizeMB * 100) / 100.0, estimatedBitrateMbps, videoKey);

            return estimatedDuration;

        } catch (Exception e) {
            log.error("초고속 비디오 길이 확인 실패: {}", videoKey, e);
            return 0;
        }
    }

    /**
     * 파일 형식별 예상 비트레이트 반환 (Mbps)
     * 실제 사용 데이터를 기반으로 지속적으로 조정 필요
     */
    private double getEstimatedBitrate(String videoKey, String contentType) {
        String fileName = videoKey.toLowerCase();

        // Content-Type 우선 확인
        if (contentType != null) {
            contentType = contentType.toLowerCase();
            if (contentType.contains("webm")) return 2.0;
            if (contentType.contains("mp4")) return 3.0;
            if (contentType.contains("avi")) return 4.0;
            if (contentType.contains("mov")) return 3.5;
            if (contentType.contains("mkv")) return 2.5;
        }

        // 파일 확장자로 추정
        if (fileName.endsWith(".webm")) return 2.0;      // WebM: 효율적 압축
        if (fileName.endsWith(".mp4")) return 3.0;       // MP4: 일반적
        if (fileName.endsWith(".avi")) return 4.0;       // AVI: 큰 용량
        if (fileName.endsWith(".mov")) return 3.5;       // MOV: 고품질
        if (fileName.endsWith(".mkv")) return 2.5;       // MKV: 가변적
        if (fileName.endsWith(".flv")) return 1.5;       // FLV: 낮은 품질
        if (fileName.endsWith(".wmv")) return 2.0;       // WMV: 압축률 좋음
        if (fileName.endsWith(".m4v")) return 3.0;       // M4V: MP4와 유사

        // 기본값: 중간 비트레이트
        return 2.5;
    }


    // 짧은 비디오 처리
    private String processShortVideoWithPresignedUrl(S3Client s3Client, Long videoId, String videoKey) {
        try {
            log.info("짧은 영상 처리 시작: 비디오 ID {}", videoId);
            long start = System.currentTimeMillis();

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(videoKey)
                    .build();
            try (S3Presigner presigner = S3Presigner.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build()) {
                PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(r -> r
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(getObjectRequest));
                String presignedUrl = presignedRequest.url().toString();

                return localWhisperService.transcribeFromUrl(presignedUrl);
            }
        } catch (Exception e) {
            log.error("짧은 영상 처리 실패: 비디오 ID {}", videoId, e);
            return "";
        }
    }

    private String processShortVideo(byte[] videoBytes) {
        long start = System.currentTimeMillis();
        AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();

        // S3에 오디오 파일 업로드
        String audioUri = awsTranscribe.uploadToS3(credentialsProvider, videoBytes);
        log.info("오디오 S3 업로드 완료: {}", audioUri);

        long end = System.currentTimeMillis();
        long duration = end - start;
        log.info("음성 변환 소요 시간: {}ms", duration);

        start = System.currentTimeMillis();
        // AWS Transcribe 작업 시작
        String jobName = awsTranscribe.startTranscriptionJob(credentialsProvider, audioUri);
        log.info("AWS Transcribe 작업 시작: {}", jobName);

        // 변환된 텍스트 가져오기
        String transcriptUri = awsTranscribe.getTranscriptionResult(credentialsProvider, jobName);

        end = System.currentTimeMillis();
        duration = end - start;
        log.info("STT 소요 시간: {}ms", duration);

        return awsTranscribe.parseTranscriptionJson(transcriptUri);
    }

    // 긴 비디오 처리 (4개로 분할 및 병렬 처리)
    private String processLongVideoWithPresignedUrl(S3Client s3Client, Long videoId, String videoKey, double duration) {
        try {
            log.info("긴 영상 처리 시작: 비디오 ID {}, 길이 {} 초", videoId, duration);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(videoKey)
                    .build();
            try (S3Presigner presigner = S3Presigner.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build()) {
                PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(r -> r
                        .signatureDuration(Duration.ofMinutes(60))
                        .getObjectRequest(getObjectRequest));
                String presignedUrl = presignedRequest.url().toString();

                List<String> transcripts = new ArrayList<>();
                ExecutorService executor = Executors.newFixedThreadPool(4);
                List<Future<String>> futures = new ArrayList<>();
                double chunkDuration = duration / 4;

                for (int i = 0; i < 4; i++) {
                    final int chunkIndex = i;
                    final double startTime = i * chunkDuration;
                    final double chunkLength = chunkDuration;

                    Future<String> future = executor.submit(() -> {
                        Path tempChunk = Files.createTempFile("chunk-" + chunkIndex, ".webm");
                        Path tempAudio = Files.createTempFile("audio-" + chunkIndex, ".mp3");

                        try {
                            ProcessBuilder pb = new ProcessBuilder(
                                    "ffmpeg",
                                    "-i", presignedUrl,
                                    "-ss", String.format("%.2f", startTime),
                                    "-t", String.format("%.2f", chunkLength),
                                    "-c:v", "copy",
                                    "-c:a", "copy",
                                    tempChunk.toString()
                            );
                            pb.redirectErrorStream(true);
                            Process process = pb.start();
                            StringBuilder errorOutput = new StringBuilder();
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    errorOutput.append(line).append("\n");
                                }
                            }
                            process.waitFor(60, TimeUnit.SECONDS);

                            ProcessBuilder audioPb = new ProcessBuilder(
                                    "ffmpeg",
                                    "-i", tempChunk.toString(),
                                    "-vn",
                                    "-acodec", "libmp3lame",
                                    "-ar", "44100",
                                    "-ac", "2",
                                    "-f", "mp3",
                                    tempAudio.toString()
                            );
                            audioPb.redirectErrorStream(true);
                            Process audioProcess = audioPb.start();
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(audioProcess.getErrorStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    errorOutput.append(line).append("\n");
                                }
                            }
                            audioProcess.waitFor(60, TimeUnit.SECONDS);

                            String audioFileName = "audio/chunk-" + chunkIndex + "-" + UUID.randomUUID() + ".mp3";
                            PutObjectRequest putRequest = PutObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(audioFileName)
                                    .contentType("audio/mpeg")
                                    .build();
                            s3Client.putObject(putRequest, RequestBody.fromFile(tempAudio));
                            String audioUri = "s3://" + bucket + "/" + audioFileName;

                            String jobName = awsTranscribe.startTranscriptionJob(DefaultCredentialsProvider.create(), audioUri);
                            String transcriptUri = awsTranscribe.getTranscriptionResult(DefaultCredentialsProvider.create(), jobName);
                            String transcript = awsTranscribe.parseTranscriptionJson(transcriptUri);
                            log.info("청크 {} 음성 변환 완료: {}", chunkIndex, transcript.length());

                            return transcript;
                        } finally {
                            Files.deleteIfExists(tempChunk);
                            Files.deleteIfExists(tempAudio);
                        }
                    });
                    futures.add(future);
                }

                for (Future<String> future : futures) {
                    try {
                        String transcript = future.get();
                        if (transcript != null && !transcript.trim().isEmpty()) {
                            transcripts.add(transcript);
                        }
                    } catch (Exception e) {
                        log.error("청크 처리 중 오류: {}", e.getMessage());
                    }
                }

                executor.shutdown();
                if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }

                String combinedAnswer = String.join(" ", transcripts);
                log.info("긴 영상 텍스트 병합 완료: 길이 {}", combinedAnswer.length());
                return combinedAnswer;
            }
        } catch (Exception e) {
            log.error("긴 영상 처리 실패: 비디오 ID {}", videoId, e);
            return "";
        }
    }

    private String createThumbnailWithPresignedUrl(S3Client s3Client, String videoKey, String thumbnailKey) {
        try {
            log.info("썸네일 생성 시작: {}", thumbnailKey);

            // presigned URL 생성
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(videoKey)
                    .build();

            try (S3Presigner presigner = S3Presigner.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build()) {

                PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(r -> r
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(getObjectRequest));
                String presignedUrl = presignedRequest.url().toString();

                // 임시 썸네일 파일 생성
                Path tempThumbnail = Files.createTempFile("thumb_", ".jpg");

                // JavaCV를 사용해서 첫 프레임 추출
                FFmpegFrameGrabber grabber = null;
                try {
                    grabber = new FFmpegFrameGrabber(presignedUrl);

                    // 성능 최적화 옵션들
                    grabber.setOption("threads", "auto");
                    grabber.setOption("analyzeduration", "1000000"); // 1초
                    grabber.setOption("probesize", "1000000"); // 1MB

                    grabber.start();

                    // 비디오 정보 로깅
                    log.debug("비디오 정보 - 길이: {}초, 해상도: {}x{}, FPS: {}",
                            grabber.getLengthInTime() / 1000000.0,
                            grabber.getImageWidth(),
                            grabber.getImageHeight(),
                            grabber.getVideoFrameRate());

                    // 첫 프레임으로 이동 (시간 기준: 마이크로초)
                    grabber.setTimestamp(1000); // 0.001초 위치

                    // 프레임 추출
                    Frame frame = grabber.grabImage();
                    if (frame == null) {
                        // 첫 프레임이 null일 경우 다음 프레임 시도
                        log.debug("첫 프레임이 null, 다음 프레임 시도");
                        frame = grabber.grabImage();
                    }

                    if (frame == null) {
                        log.error("비디오에서 프레임을 추출할 수 없습니다: {}", videoKey);
                        return DEFAULT_THUMBNAIL_URL;
                    }

                    // Frame을 BufferedImage로 변환
                    Java2DFrameConverter converter = new Java2DFrameConverter();
                    BufferedImage image = converter.convert(frame);

                    if (image == null) {
                        log.error("프레임을 이미지로 변환할 수 없습니다");
                        return DEFAULT_THUMBNAIL_URL;
                    }

                    // 썸네일 크기 조정 (옵션)
                    BufferedImage thumbnail = resizeImage(image, 800, 600);

                    // JPEG로 저장 (품질 설정)
                    saveAsJpeg(thumbnail, tempThumbnail.toFile(), 0.85f);

                    log.info("썸네일 생성 완료: {} bytes", Files.size(tempThumbnail));

                } finally {
                    // 리소스 해제
                    if (grabber != null) {
                        try {
                            grabber.stop();
                            grabber.release();
                        } catch (Exception e) {
                            log.warn("FFmpegFrameGrabber 해제 실패: {}", e.getMessage());
                        }
                    }
                }

                // 생성된 썸네일 파일 검증
                if (!Files.exists(tempThumbnail) || Files.size(tempThumbnail) == 0) {
                    log.error("썸네일 파일이 생성되지 않았습니다.");
                    return DEFAULT_THUMBNAIL_URL;
                }

                // S3에 썸네일 업로드
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(thumbnailKey)
                        .contentType("image/jpeg")
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build();

                s3Client.putObject(putRequest, RequestBody.fromFile(tempThumbnail));

                // 임시 파일 삭제
                Files.delete(tempThumbnail);

                String url = String.format("%s/%s", getS3UrlPrefix(), thumbnailKey);
                log.info("썸네일 업로드 완료");
                return url;
            }

        } catch (Exception e) {
            log.warn("썸네일 생성 실패: {}", e.getMessage(), e);
            return DEFAULT_THUMBNAIL_URL;
        }
    }

    private String createThumbnail(byte[] videoBytes, String thumbnailKey, S3Client s3Client) {
        FFmpegFrameGrabber grabber = null;
        try {
            log.info("썸네일 생성 시작: {}", thumbnailKey);

            if (videoBytes.length == 0) {
                log.warn("비디오 데이터가 비어있음. Returning default thumbnail.");
                return DEFAULT_THUMBNAIL_URL;
            }

            // FFmpeg 프로세스 빌더 생성 - 첫 프레임을 추출
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", "pipe:0",           // 입력은 stdin에서
                    "-ss", "00:00:00.001",    // 시작 직후 (1ms)
                    "-vframes", "1",          // 1개 프레임만 추출
                    "-q:v", "2",              // 품질 설정 (낮을수록 좋은 품질)
                    "-f", "image2",           // 이미지 출력 형식
                    "-c:v", "mjpeg",          // JPEG 인코더
                    "pipe:1"                  // stdout으로 출력
            );

            Process process = pb.start();

            // FFmpeg에 비디오 데이터 전송하는 스레드
            Thread inputThread = new Thread(() -> {
                try (OutputStream ffmpegInput = process.getOutputStream()) {
                    ffmpegInput.write(videoBytes);
                    ffmpegInput.flush();
                } catch (IOException e) {
                    log.error("FFmpeg에 비디오 데이터 전송 중 오류", e);
                }
            });
            inputThread.start();

            // FFmpeg 에러 출력 로깅하는 스레드
            StringBuilder errorOutput = new StringBuilder();
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("FFmpeg 오류 스트림 읽기 실패", e);
                }
            });
            errorThread.start();

            // FFmpeg 표준 출력에서 이미지 읽기
            byte[] thumbnailBytes;
            try (InputStream ffmpegOutput = process.getInputStream()) {
                thumbnailBytes = IOUtils.toByteArray(ffmpegOutput);
            }

            // 스레드 완료 대기
            inputThread.join();
            errorThread.join();

            // 프로세스 완료 대기
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.error("FFmpeg 처리 타임아웃");
                return DEFAULT_THUMBNAIL_URL;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0 || thumbnailBytes.length == 0) {
                log.error("FFmpeg 썸네일 생성 실패. 종료 코드: {}, 에러: {}", exitCode, errorOutput);
                return DEFAULT_THUMBNAIL_URL;
            }

            log.info("FFmpeg 썸네일 생성 성공. 크기: {} bytes", thumbnailBytes.length);

            // S3에 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(thumbnailKey)
                    .acl(ObjectCannedACL.PUBLIC_READ)  // PublicRead 설정
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                    new ByteArrayInputStream(thumbnailBytes),
                    thumbnailBytes.length));
            log.info("썸네일 S3 업로드 완료: {}", thumbnailKey);
            String url = String.format("%s/%s", getS3UrlPrefix(), thumbnailKey);
            return url;
        } catch (Exception e) {
            log.warn("썸네일 생성 중 예외 발생. 기본 썸네일 반환", e);
            return DEFAULT_THUMBNAIL_URL;
        }
    }

    /**
     * 이미지 크기 조정 (비율 유지)
     */
    private BufferedImage resizeImage(BufferedImage original, int maxWidth, int maxHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        // 비율 계산
        double scaleX = (double) maxWidth / originalWidth;
        double scaleY = (double) maxHeight / originalHeight;
        double scale = Math.min(scaleX, scaleY);

        // 원본이 이미 작다면 그대로 반환
        if (scale >= 1.0) {
            return original;
        }

        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();

        // 고품질 렌더링 설정
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resized;
    }

    /**
     * JPEG 품질 설정하여 저장
     */
    private void saveAsJpeg(BufferedImage image, File outputFile, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JPEG writer를 찾을 수 없습니다");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        try (FileImageOutputStream output = new FileImageOutputStream(outputFile)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public VideoWithFeedbackDTO getVideoWithFeedback(Long videoId) throws Exception {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(QuestionNotFoundException::new);
        return returnVideoWithFeedback(video);
    }

    @SneakyThrows
    public List<VideoReturnDTO> getOpenVideos() {
        List<Video> videos = videoRepository.findAllOpenVideos();
        return videos.stream()
                .map(this::convertToDTO)
                .toList();
    }

    @SneakyThrows
    public List<VideoReturnDTO> getMyVideos() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        String username = siteUserSecurityDTO.getUsername();

        List<Video> videos = videoRepository.findMyVideos(username);
        return videos.stream()
                .map(this::convertToDTO)
                .toList();
    }

    public VideoReturnDTO changeVisibility(Long videoId, Boolean isOpen) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername())
                .orElseThrow(() -> new Exception("User not found"));

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new Exception("Video not found"));

        if (!video.getSiteUser().getUsername().equals(siteUser.getUsername())) {
            throw new Exception("수정 권한이 없습니다.");
        }
        video.setOpen(isOpen);
        video.setUpdatedAt(LocalDateTime.now());

        Video updatedVideo = videoRepository.save(video);
        return convertToDTO(updatedVideo);
    }

    public void deleteVideo(Long videoId) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername())
                .orElseThrow(() -> new Exception("User not found"));

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new Exception("Video not found"));

        if (!video.getSiteUser().getUsername().equals(siteUser.getUsername())) {
            throw new Exception("삭제 권한이 없습니다.");
        }

        videoRepository.delete(video);
    }

    private VideoReturnDTO convertToDTO(Video video) {
        VideoReturnDTO videoReturnDTO;
        try {
            videoReturnDTO = VideoReturnDTO.builder()
                    .videoId(video.getId())
                    .videoPath(video.getVideoPath())
                    .videoName(video.getVideoName())
                    .imageUrl(video.getSiteUser().getImageUrl())
                    .username(video.getSiteUser().getUsername())
                    .nickname(video.getSiteUser().getNickname())
                    .thumbnail(video.getThumbnail())
                    .question(video.getQuestion().getContents())
                    .category(video.getQuestion().getQuestionSet().getCategory())
                    .createdAt(video.getCreatedAt())
                    .updatedAt(video.getUpdatedAt())
                    .isOpen(video.isOpen())
                    .build();
        } catch (Exception e) {
            log.error("Error converting Video to DTO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert Video to DTO", e);
        }
        return videoReturnDTO;
    }

    private VideoWithFeedbackDTO returnVideoWithFeedback(Video video) {
        try {
            VideoReturnDTO videoReturnDTO = VideoReturnDTO.builder()
                    .videoId(video.getId())
                    .videoPath(video.getVideoPath())
                    .videoName(video.getVideoName())
                    .imageUrl(video.getSiteUser().getImageUrl())
                    .username(video.getSiteUser().getUsername())
                    .nickname(video.getSiteUser().getNickname())
                    .thumbnail(video.getThumbnail())
                    .question(video.getQuestion().getContents())
                    .category(video.getQuestion().getQuestionSet().getCategory())
                    .createdAt(video.getCreatedAt())
                    .updatedAt(video.getUpdatedAt())
                    .isOpen(video.isOpen())
                    .build();

            if (video.getFeedback() == null) {
                // null feedback으로 빌더 사용
                return VideoWithFeedbackDTO.builder()
                        .video(videoReturnDTO)
                        .feedback(null)
                        .build();
            } else {

                Feedback feedback = feedbackService.findFeedback(video.getFeedback().getId());
                FeedbackReturnDTO feedbackReturnDTO = FeedbackReturnDTO.builder()
                        .feedbackId(feedback.getId())
                        .videoId(video.getId())
                        .contents(feedback.getContents())
                        .createdAt(feedback.getCreatedAt())
                        .build();

                return VideoWithFeedbackDTO.builder()
                        .video(videoReturnDTO)
                        .feedback(feedbackReturnDTO)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error converting Video to DTO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert Video to DTO", e);
        }
    }

    public boolean checkThreadPoolCapacity() {
        ThreadPoolExecutor executor = videoProcessingExecutor.getThreadPoolExecutor();

        int activeCount = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        int totalLoad = activeCount + queueSize;
        int maxCapacity = CORE_POOL_SIZE + QUEUE_CAPACITY;

        double loadRatio = (double) totalLoad / maxCapacity;

        log.info("스레드풀 상태 - Active: {}, Queue: {}, Total: {}/{} ({:.1f}%)",
                activeCount, queueSize, totalLoad, maxCapacity, loadRatio * 100);

        // 실험: 48/60 (80%) 미만일 때만 즉시 처리
        boolean hasCapacity = loadRatio < CAPACITY_THRESHOLD;

        if (!hasCapacity) {
            log.warn("스레드풀 용량 부족: {}/{} (임계값: {}%)",
                    totalLoad, maxCapacity, (int)(CAPACITY_THRESHOLD * 100));
        }

        return hasCapacity;
    }

    @Transactional
    public Long enqueue(Long questionId, String videoKey, Boolean isOpen, Long startTime, Boolean usePresignedUrl) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO userDetails = (SiteUserSecurityDTO) auth.getPrincipal();

        VideoProcessingQueue request = VideoProcessingQueue.builder()
                .questionId(questionId)
                .videoKey(videoKey)
                .isOpen(isOpen)
                .startTime(startTime)
                .usePresignedUrl(usePresignedUrl)
                .username(userDetails.getUsername())
                .build();

        VideoProcessingQueue savedRequest = queueRepository.save(request);

        log.info("DB 큐 저장 완료: queueId={}, videoKey={}, 현재 대기: {}개",
                savedRequest.getId(), videoKey, queueRepository.countPendingTasks());

        return savedRequest.getId();
    }

}
