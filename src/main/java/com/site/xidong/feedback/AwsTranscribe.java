package com.site.xidong.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.io.*;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@NoArgsConstructor
public class AwsTranscribe {
    // AWS 설정
    private static final Region REGION = Region.AP_NORTHEAST_2; // 서울 리전
    private static final String BUCKET_NAME = "dive-s3-ver2"; // S3 버킷명

    // S3에 오디오 파일 업로드
    public static String uploadToS3(AwsCredentialsProvider credentialsProvider, byte[] videoBytes) {
        try {
            log.info("uploadToS3 시작 - videoBytes 크기: {}", videoBytes.length);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", "pipe:0",            // stdin에서 webm
                    "-vn",                     // 비디오 제거
                    "-acodec", "libmp3lame",    // mp3 인코딩
                    "-ar", "44100",             // 표준 mp3 샘플링
                    "-ac", "2",                // 스테레오
                    "-f", "mp3",               // WAV 포맷
                    "pipe:1"                   // stdout으로 출력
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 비디오 데이터를 쓰는 스레드
            Thread inputThread = new Thread(() -> {
                try (OutputStream ffmpegStdin = process.getOutputStream()) {
                    ffmpegStdin.write(videoBytes);
                    ffmpegStdin.flush();
                } catch (IOException e) {
                    log.error("ffmpeg stdin 처리 중 오류", e);
                }
            });
            inputThread.start();

            // 오류 출력을 읽는 스레드
            StringBuilder errorOutput = new StringBuilder();
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("ffmpeg stderr 읽기 중 오류", e);
                }
            });
            errorThread.start();

            // 2. stdout에서 오디오 bytes 읽기
            byte[] audioBytes;
            try (InputStream ffmpegStdout = process.getInputStream()) {
                audioBytes = IOUtils.toByteArray(ffmpegStdout);
            }

            // 모든 스레드가 완료될 때까지 대기
            inputThread.join();
            errorThread.join();

            // 프로세스 완료 대기 (타임아웃 설정)
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.error("ffmpeg 처리 타임아웃");
                throw new RuntimeException("ffmpeg 처리 타임아웃");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0 || audioBytes.length == 0) {
                log.error("ffmpeg 음성 추출 실패. 종료 코드: {}, 추출된 바이트: {}, 오류: {}", exitCode, audioBytes.length, errorOutput);
                throw new RuntimeException("ffmpeg 음성 추출 실패. 종료 코드: " + exitCode);
            }

            log.info("ffmpeg 음성 추출 성공. 추출 크기: {} bytes", audioBytes.length);

            // 3. S3에 업로드
            S3Client s3Client = S3Client.builder()
                    .region(REGION)
                    .credentialsProvider(credentialsProvider)
                    .build();

            String audioFileName = "extracted-audio-" + UUID.randomUUID() + ".mp3";
            String s3Key = "audio/" + audioFileName;

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .contentType("audio/mpeg")
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(audioBytes));

            String fileUri = "s3://" + BUCKET_NAME + "/" + s3Key;
            log.info("오디오 파일 S3 업로드 완료: {}", fileUri);

            return fileUri;

        } catch (IOException | InterruptedException e) {
            log.error("uploadToS3 처리 실패", e);
            throw new RuntimeException("오디오 업로드 실패", e);
        }
    }

    // AWS Transcribe 작업 시작
    public static String startTranscriptionJob(AwsCredentialsProvider credentialsProvider, String s3Uri) {
        TranscribeClient transcribeClient = TranscribeClient.builder()
                .region(REGION)
                .credentialsProvider(credentialsProvider)
                .build();

        String jobName = "TranscriptionJob-" + System.currentTimeMillis(); // 고유한 작업 이름 생성

        StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .media(Media.builder().mediaFileUri(s3Uri).build())
                .mediaFormat(MediaFormat.MP3) // 오디오 형식 (MP3, WAV, FLAC 가능)
                .languageCode(LanguageCode.KO_KR) // 한국어 인식
                .build();

        transcribeClient.startTranscriptionJob(request);
        log.info("AWS Transcribe 변환 작업이 시작되었습니다: {}", jobName);
        return jobName;
    }

    // 변환된 텍스트 가져오기
    public static String getTranscriptionResult( AwsCredentialsProvider credentialsProvider, String jobName) {
        TranscribeClient transcribeClient = TranscribeClient.builder()
                .region(REGION)
                .credentialsProvider(credentialsProvider)
                .build();

        try {
            while (true) {
                GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder()
                        .transcriptionJobName(jobName)
                        .build();

                GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(request);
                TranscriptionJob job = response.transcriptionJob();

                switch (job.transcriptionJobStatus()) {
                    case FAILED:
                        throw new RuntimeException("변환 작업 실패: " + job.failureReason());
                    case COMPLETED:
                        String transcriptUri = job.transcript().transcriptFileUri();
                        log.info("변환 완료!");
                        return transcriptUri; // 실제 변환된 JSON 파일을 가져오는 로직 필요
                    default:
                        log.info("변환 중... 상태: {}", job.transcriptionJobStatus());
                        Thread.sleep(5000); // 5초 대기 후 재시도
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("변환 결과 가져오기 실패: " + e.getMessage());
        }
    }

    // Json 반환
    public static String parseTranscriptionJson(String transcriptUrl) {
        try (InputStream is = new URL(transcriptUrl).openStream()) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(is);
            return rootNode.path("results").path("transcripts").get(0).path("transcript").asText();
        } catch (Exception e) {
            throw new RuntimeException("변환된 텍스트 JSON 파싱 실패: " + e.getMessage());
        }
    }
}
