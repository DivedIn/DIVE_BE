package com.site.xidong.domain.feedback.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LocalWhisperService {

    private static final int TIMEOUT_SECONDS = 300;

    @Value("${whisper.python.path}")
    private String pythonPath;

    @Value("${whisper.script.path}")
    private String scriptPath;

    @Value("${whisper.model.size}")
    private String modelSize;

    @Value("${whisper.mock.enabled}")
    private boolean mockEnabled;

    @Value("${whisper.mock.delay-ms:20000}")
    private long mockDelayMs;

    public String transcribeFromUrl(String presignedUrl) {
        if (mockEnabled) {
            return runMock();
        }

        long start = System.currentTimeMillis();
        log.info("Whisper STT 시작");

        Process process;
        try {
            process = new ProcessBuilder(pythonPath, scriptPath, presignedUrl, modelSize)
                    .redirectErrorStream(false)
                    .start();
        } catch (IOException e) {
            log.error("Whisper 프로세스 실행 실패", e);
            return "";
        }

        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        Thread outputThread = streamReader(process.getInputStream(), output, false);
        Thread errorThread  = streamReader(process.getErrorStream(), errors, true);
        outputThread.start();
        errorThread.start();

        try {
            outputThread.join();
            errorThread.join();
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.error("Whisper STT 타임아웃 ({}초 초과)", TIMEOUT_SECONDS);
                return "";
            }
            if (process.exitValue() != 0) {
                log.error("Whisper STT 실패. 종료코드: {}", process.exitValue());
                return "";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Whisper STT 인터럽트됨", e);
            return "";
        }

        String transcript = output.toString().trim();
        log.info("Whisper STT 완료: 길이={}, 소요={}ms", transcript.length(), System.currentTimeMillis() - start);
        return transcript;
    }

    private String runMock() {
        log.info("[MOCK-STT] {}ms 대기 후 고정 텍스트 반환", mockDelayMs);
        try {
            Thread.sleep(mockDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "도커는 컨테이너 기반의 오픈소스 가상화 플랫폼입니다. 애플리케이션과 그 실행에 필요한 " +
               "라이브러리, 설정, 의존성 등을 컨테이너라는 단위로 패키징해서 어떤 환경에서든 동일하게 " +
               "실행될 수 있도록 해주는 도구입니다.";
    }

    private Thread streamReader(java.io.InputStream stream, StringBuilder buffer, boolean warnOnError) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append("\n");
                    if (warnOnError && (line.contains("ERROR") || line.contains("error"))) {
                        log.warn("Whisper: {}", line);
                    }
                }
            } catch (IOException e) {
                log.error("스트림 읽기 실패", e);
            }
        });
    }
}
