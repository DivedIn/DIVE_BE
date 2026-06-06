package com.site.xidong.domain.feedback.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsTranscribe {

    private static final int POLLING_INTERVAL_MS = 5_000;
    private static final int MAX_POLLING_ATTEMPTS = 120; // 최대 10분 (5초 × 120)

    private final S3Client s3Client;
    private final TranscribeClient transcribeClient;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadToS3(byte[] videoBytes) {
        log.info("uploadToS3 시작 - videoBytes 크기: {}", videoBytes.length);

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-i", "pipe:0",
                "-vn",
                "-acodec", "libmp3lame",
                "-ar", "44100",
                "-ac", "2",
                "-f", "mp3",
                "pipe:1"
        );
        pb.redirectErrorStream(false);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.STT_FAILED);
        }

        StringBuilder errorOutput = new StringBuilder();
        Thread inputThread = new Thread(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(videoBytes);
            } catch (IOException e) {
                log.error("ffmpeg stdin 처리 중 오류", e);
            }
        });
        Thread errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            } catch (IOException e) {
                log.error("ffmpeg stderr 읽기 중 오류", e);
            }
        });

        inputThread.start();
        errorThread.start();

        byte[] audioBytes;
        try (InputStream stdout = process.getInputStream()) {
            audioBytes = IOUtils.toByteArray(stdout);
            inputThread.join();
            errorThread.join();
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new CustomException(ErrorCode.STT_FAILED);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.STT_FAILED);
        }

        if (process.exitValue() != 0 || audioBytes.length == 0) {
            log.error("ffmpeg 음성 추출 실패. 종료 코드: {}, 오류: {}", process.exitValue(), errorOutput);
            throw new CustomException(ErrorCode.STT_FAILED);
        }

        String s3Key = "audio/extracted-" + UUID.randomUUID() + ".mp3";
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(s3Key).contentType("audio/mpeg").build(),
                RequestBody.fromBytes(audioBytes)
        );

        String fileUri = "s3://" + bucket + "/" + s3Key;
        log.info("오디오 S3 업로드 완료: {}", fileUri);
        return fileUri;
    }

    public String startTranscriptionJob(String s3Uri) {
        String jobName = "TranscriptionJob-" + System.currentTimeMillis();
        transcribeClient.startTranscriptionJob(StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .media(Media.builder().mediaFileUri(s3Uri).build())
                .mediaFormat(MediaFormat.MP3)
                .languageCode(LanguageCode.KO_KR)
                .build());
        log.info("AWS Transcribe 작업 시작: {}", jobName);
        return jobName;
    }

    public String getTranscriptionResult(String jobName) {
        for (int attempt = 1; attempt <= MAX_POLLING_ATTEMPTS; attempt++) {
            GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(
                    GetTranscriptionJobRequest.builder().transcriptionJobName(jobName).build());
            TranscriptionJob job = response.transcriptionJob();

            switch (job.transcriptionJobStatus()) {
                case FAILED:
                    log.error("Transcribe 작업 실패: {}", job.failureReason());
                    throw new CustomException(ErrorCode.STT_FAILED);
                case COMPLETED:
                    log.info("Transcribe 변환 완료: {}", jobName);
                    return job.transcript().transcriptFileUri();
                default:
                    log.info("Transcribe 변환 중... 상태: {}, 시도: {}/{}", job.transcriptionJobStatus(), attempt, MAX_POLLING_ATTEMPTS);
            }

            try {
                Thread.sleep(POLLING_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CustomException(ErrorCode.STT_FAILED);
            }
        }

        log.error("Transcribe 폴링 타임아웃: {}", jobName);
        throw new CustomException(ErrorCode.STT_TIMEOUT);
    }

    public String parseTranscriptionJson(String transcriptUrl) {
        try (InputStream is = new URL(transcriptUrl).openStream()) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);
            return root.path("results").path("transcripts").get(0).path("transcript").asText();
        } catch (Exception e) {
            log.error("Transcribe JSON 파싱 실패: {}", transcriptUrl, e);
            throw new CustomException(ErrorCode.STT_FAILED);
        }
    }
}
