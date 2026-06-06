package com.site.xidong.domain.video.service;

import com.site.xidong.domain.feedback.service.AwsTranscribe;
import com.site.xidong.domain.feedback.service.LocalWhisperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttService {

    private final S3Client s3Client;
    private final AwsTranscribe awsTranscribe;
    private final LocalWhisperService localWhisperService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    public double getVideoDuration(String videoKey) {
        log.info("비디오 길이 확인 시작: {}", videoKey);
        try {
            HeadObjectResponse head = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(videoKey).build());

            Map<String, String> metadata = head.metadata();
            if (metadata.containsKey("duration")) {
                try {
                    double d = Double.parseDouble(metadata.get("duration"));
                    log.info("메타데이터에서 duration 확인: {}초", d);
                    return d;
                } catch (NumberFormatException ignored) {
                    log.warn("메타데이터 duration 파싱 실패, 추정 방식 사용");
                }
            }

            long fileSizeBytes = head.contentLength();
            if (fileSizeBytes <= 0) {
                log.error("파일 크기를 확인할 수 없음: {}", videoKey);
                return 0;
            }
            double bitrate = getEstimatedBitrate(videoKey, head.contentType());
            double estimatedDuration = (fileSizeBytes / (1024.0 * 1024.0) * 8) / bitrate;
            log.info("비디오 길이 추정 완료: {}초 ({}MB, {}Mbps)",
                    Math.round(estimatedDuration),
                    Math.round(fileSizeBytes / (1024.0 * 1024.0) * 100) / 100.0,
                    bitrate);
            return estimatedDuration;
        } catch (Exception e) {
            log.error("비디오 길이 확인 실패: {}", videoKey, e);
            return 0;
        }
    }

    public String transcribeShortVideo(String videoKey) {
        try {
            String presignedUrl = createPresignedUrl(videoKey, Duration.ofMinutes(10));
            return localWhisperService.transcribeFromUrl(presignedUrl);
        } catch (Exception e) {
            log.error("짧은 영상 STT 실패: {}", videoKey, e);
            return "";
        }
    }

    public String transcribeLongVideo(String videoKey, double durationSeconds) {
        log.info("긴 영상 처리 시작: {}, 길이 {}초", videoKey, durationSeconds);
        try {
            String presignedUrl = createPresignedUrl(videoKey, Duration.ofMinutes(60));
            double chunkDuration = durationSeconds / 4;

            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < 4; i++) {
                final int chunkIndex = i;
                final double startSec = i * chunkDuration;
                futures.add(executor.submit(() -> transcribeChunk(presignedUrl, chunkIndex, startSec, chunkDuration)));
            }

            List<String> transcripts = new ArrayList<>();
            for (Future<String> future : futures) {
                try {
                    String t = future.get();
                    if (t != null && !t.trim().isEmpty()) transcripts.add(t);
                } catch (Exception e) {
                    log.error("청크 처리 중 오류: {}", e.getMessage());
                }
            }

            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) executor.shutdownNow();

            String combined = String.join(" ", transcripts);
            log.info("긴 영상 텍스트 병합 완료: 길이={}", combined.length());
            return combined;
        } catch (Exception e) {
            log.error("긴 영상 STT 실패: {}", videoKey, e);
            return "";
        }
    }

    private String transcribeChunk(String presignedUrl, int chunkIndex, double startSec, double durationSec) throws Exception {
        Path tempChunk = Files.createTempFile("chunk-" + chunkIndex + "-", ".webm");
        Path tempAudio = Files.createTempFile("audio-" + chunkIndex + "-", ".mp3");
        try {
            extractChunk(presignedUrl, tempChunk, startSec, durationSec);
            convertToMp3(tempChunk, tempAudio);

            String audioKey = "audio/chunk-" + chunkIndex + "-" + UUID.randomUUID() + ".mp3";
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(audioKey).contentType("audio/mpeg").build(),
                    RequestBody.fromFile(tempAudio));

            String audioUri = "s3://" + bucket + "/" + audioKey;
            String jobName = awsTranscribe.startTranscriptionJob(audioUri);
            String transcriptUri = awsTranscribe.getTranscriptionResult(jobName);
            String transcript = awsTranscribe.parseTranscriptionJson(transcriptUri);
            log.info("청크 {} 음성 변환 완료: 길이={}", chunkIndex, transcript.length());
            return transcript;
        } finally {
            Files.deleteIfExists(tempChunk);
            Files.deleteIfExists(tempAudio);
        }
    }

    private void extractChunk(String presignedUrl, Path output, double startSec, double durationSec) throws Exception {
        Process p = new ProcessBuilder(
                "ffmpeg", "-i", presignedUrl,
                "-ss", String.format("%.2f", startSec),
                "-t", String.format("%.2f", durationSec),
                "-c:v", "copy", "-c:a", "copy",
                output.toString()
        ).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        p.waitFor(60, TimeUnit.SECONDS);
    }

    private void convertToMp3(Path input, Path output) throws Exception {
        Process p = new ProcessBuilder(
                "ffmpeg", "-i", input.toString(),
                "-vn", "-acodec", "libmp3lame", "-ar", "44100", "-ac", "2", "-f", "mp3",
                output.toString()
        ).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        p.waitFor(60, TimeUnit.SECONDS);
    }

    private String createPresignedUrl(String videoKey, Duration duration) {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            PresignedGetObjectRequest req = presigner.presignGetObject(r -> r
                    .signatureDuration(duration)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(videoKey).build()));
            return req.url().toString();
        }
    }

    private double getEstimatedBitrate(String videoKey, String contentType) {
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("webm")) return 2.0;
            if (ct.contains("mp4"))  return 3.0;
            if (ct.contains("avi"))  return 4.0;
            if (ct.contains("mov"))  return 3.5;
            if (ct.contains("mkv"))  return 2.5;
        }
        String key = videoKey.toLowerCase();
        if (key.endsWith(".webm")) return 2.0;
        if (key.endsWith(".mp4"))  return 3.0;
        if (key.endsWith(".avi"))  return 4.0;
        if (key.endsWith(".mov"))  return 3.5;
        if (key.endsWith(".mkv"))  return 2.5;
        if (key.endsWith(".flv"))  return 1.5;
        if (key.endsWith(".wmv"))  return 2.0;
        if (key.endsWith(".m4v"))  return 3.0;
        return 2.5;
    }
}
