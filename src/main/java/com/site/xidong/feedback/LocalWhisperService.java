package com.site.xidong.feedback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LocalWhisperService {

    @Value("${whisper.python.path}")
    private String pythonPath;

    @Value("${whisper.script.path}")
    private String scriptPath;

    @Value("${whisper.model.size}")
    private String modelSize;

    /**
     * S3 presigned URL에서 직접 음성 인식
     * Python 스크립트가 FFmpeg + Whisper 처리
     */
    public String transcribeFromUrl(String presignedUrl) {
        try {
            long start = System.currentTimeMillis();
            log.info("Whisper STT 시작: {}", presignedUrl);

            // Python 스크립트 실행
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath,
                    scriptPath,
                    presignedUrl,
                    modelSize
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // stdout 읽기 (변환된 텍스트)
            StringBuilder output = new StringBuilder();
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("Whisper stdout 읽기 실패", e);
                }
            });
            outputThread.start();

            // stderr 읽기 (에러/진행상황)
            StringBuilder errorOutput = new StringBuilder();
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                        if (line.contains("ERROR") || line.contains("error")) {
                            log.warn("Whisper: {}", line);
                        }
                    }
                } catch (IOException e) {
                    log.error("Whisper stderr 읽기 실패", e);
                }
            });
            errorThread.start();

            // 스레드 완료 대기
            outputThread.join();
            errorThread.join();

            // 프로세스 완료 대기 (최대 5분)
            boolean completed = process.waitFor(300, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.error("Whisper STT 타임아웃 (5분 초과)");
                throw new RuntimeException("Whisper STT 타임아웃");
            }

            // 종료 코드 확인
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Whisper STT 실패. 종료코드: {}, 에러:\n{}", exitCode, errorOutput);
                throw new RuntimeException("Whisper STT 실패: exit code " + exitCode);
            }

            // 결과 확인
            String transcript = output.toString().trim();
            if (transcript.isEmpty()) {
                log.warn("Whisper 결과가 비어있음");
            }

            long duration = System.currentTimeMillis() - start;
            log.info("Whisper STT 완료: 길이 {}, 소요시간 {}ms", transcript.length(), duration);

            return transcript;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Whisper STT 인터럽트됨", e);
            return "";
        } catch (Exception e) {
            log.error("Whisper STT 실패", e);
            return "";
        }
    }
}