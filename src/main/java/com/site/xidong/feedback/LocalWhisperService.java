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

    @Value("${whisper.mock.enabled}")
    private boolean mockEnabled;

    /**
     * S3 presigned URL에서 직접 음성 인식
     * Python 스크립트가 FFmpeg + Whisper 처리
     */
    public String transcribeFromUrl(String presignedUrl) {
        if (mockEnabled) {
            log.info("[MOCK-STT] 시작");
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("[MOCK-STT] 완료");
            return "도커는 컨테이너 기반의 오픈소스 가상화 플랫폼입니다. 애플리케이션과 그 실행에 필요한 라이브러리, 설정, 의존성 등을 컨테이너라는 단위로 패키징해서, 어떤 환경에서든 동일하게 실행될 수 있도록 해주는 도구입니다.\n" +
                    "\n" +
                    "기존 가상머신과 비교하면 차이가 명확한데요, 가상머신은 하이퍼바이저 위에 Guest OS 전체를 올리기 때문에 무겁고 부팅도 느립니다. 반면 도커 컨테이너는 Host OS의 커널을 공유하기 때문에 훨씬 가볍고 빠르게 실행된다는 장점이 있습니다.\n" +
                    "\n" +
                    "도커의 핵심 구성요소로는 이미지, 컨테이너, Dockerfile, 그리고 Docker Hub가 있습니다. 이미지는 컨테이너를 만들기 위한 읽기 전용 템플릿이고, 컨테이너는 그 이미지를 실제로 실행한 인스턴스입니다. Dockerfile은 이미지를 어떻게 빌드할지 정의한 명세서이고, Docker Hub는 이미지를 저장하고 공유하는 레지스트리입니다.\n" +
                    "\n" +
                    "도커를 사용하는 가장 큰 이유는 \"내 컴퓨터에서는 되는데요?\" 라는 환경 불일치 문제를 해결할 수 있기 때문입니다. 개발, 테스트, 운영 환경을 동일하게 유지할 수 있고, 마이크로서비스 아키텍처에서 각 서비스를 독립적으로 배포하고 확장하는 데도 매우 유리합니다. 한 마디로 요약하면, 환경에 종속되지 않는 일관된 애플리케이션 실행 환경을 제공하는 플랫폼이라고 할 수 있습니다.";
        }
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