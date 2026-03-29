package com.site.xidong.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.site.xidong.video.Video;
import com.site.xidong.video.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;


@Log4j2
@Service
@RequiredArgsConstructor
public class FeedbackService {
    @Value("${claude.api.key}")
    private String API_KEY;

    @Value("${claude.mock.enabled}")
    private boolean mockEnabled;

    private final VideoRepository videoRepository;
    private final FeedbackRepository feedbackRepository;

    public FeedbackReturnDTO getFeedback(AnswerDTO answerDTO) throws Exception { //TODO: WebClient으로 변경하기

        if (mockEnabled) {
            log.info("부하테스트 모드: Claude API 호출 생략");
            return getMockFeedback(answerDTO);
        }
        Video video = videoRepository.findById(answerDTO.getVideoId()).orElse(null);
        String question = video.getQuestion().getContents();
        String answer = answerDTO.getAnswer();
        String cmd = answer + "은 CS 면접 질문 [" + question + "]에 대한 답변 영상을 음성으로 변환한 후 STT 변환한거야. 그러니 오타라고 생각하지 말고 융통성 있게 받아들여줘. 이 답변을 실제 개발자 채용 면접 답변이라고 생각하고 내용 측면과 전달력 측면에서 피드백해줘. 결과는 한국어로 전달해줘.";

        // Jackson 객체 매퍼 생성
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();

        // JSON 구조 설정
        rootNode.put("model", "claude-3-7-sonnet-20250219");
        rootNode.put("max_tokens", 1024);

        ArrayNode messagesNode = mapper.createArrayNode();
        ObjectNode messageNode = mapper.createObjectNode();
        messageNode.put("role", "user");
        messageNode.put("content", cmd);
        messagesNode.add(messageNode);

        rootNode.set("messages", messagesNode);

        // JSON 문자열로 변환
        String jsonInputString = mapper.writeValueAsString(rootNode);

        // Claude API 요청 URL
        URL url = new URL("https://api.anthropic.com/v1/messages");

        // HTTP 연결 설정
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("x-api-key", API_KEY); // 실제 API 키로 교체
        log.info("API KEY: " + API_KEY);
        connection.setRequestProperty("anthropic-version", "2023-06-01");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // 요청 본문 전송
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // 응답 처리
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            // 응답을 FeedbackDTO로 변환하는 부분
            String responseString = response.toString();

            // Jackson을 사용하여 JSON 응답 파싱
            ObjectNode responseNode = mapper.readValue(responseString, ObjectNode.class);
            String content = responseNode.path("content").path(0).path("text").asText();

            Feedback feedback = Feedback.builder()
                    .contents(content)
                    .createdAt(LocalDateTime.now())
                    .video(video)
                    .build();
            feedbackRepository.save(feedback);

            FeedbackReturnDTO feedbackDTO = FeedbackReturnDTO.builder()
                    .feedbackId(feedback.getId())
                    .videoId(feedback.getVideo().getId())
                    .contents(feedback.getContents())
                    .createdAt(feedback.getCreatedAt())
                    .build();

            return feedbackDTO;
        } catch (IOException e) {
            // 에러 응답 처리
            if (connection.getResponseCode() >= 400) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                    log.error("API Error ({}): {}", connection.getResponseCode(), errorResponse.toString());
                }
            }
            throw e;
        }
    }

    private FeedbackReturnDTO getMockFeedback(AnswerDTO answerDTO) throws Exception {
        Video video = videoRepository.findById(answerDTO.getVideoId())
                .orElseThrow(() -> new Exception("비디오를 찾을 수 없습니다: " + answerDTO.getVideoId()));

        String question = video.getQuestion().getContents();
        long acceptedAt = System.currentTimeMillis();

        // 실제 Claude 응답과 유사한 Mock 피드백
        String mockContent = String.format("""
                ### 내용 측면 피드백
                
                **강점:**
                - 질문 "%s"에 대한 핵심 개념을 정확히 이해하고 있습니다.
                - 실무 경험을 바탕으로 한 구체적인 예시가 답변의 신뢰도를 높였습니다.
                - 기술적 깊이와 실용성의 균형이 좋습니다.
                
                **개선점:**
                - 좀 더 구조화된 답변 (예: 정의 → 장단점 → 사용 사례)으로 전개하면 더 명확할 것입니다.
                - 최신 트렌드나 대안 기술에 대한 언급이 추가되면 좋겠습니다.
                
                ### 전달력 측면 피드백
                
                **강점:**
                - 논리적인 흐름으로 청자의 이해를 돕는 구조입니다.
                - 적절한 발화 속도와 명확한 발음으로 전달력이 우수합니다.
                
                **개선점:**
                - 중요한 포인트에서 강조나 휴지(pause)를 활용하면 더 효과적일 것입니다.
                - 약간의 필러워드("음", "아")가 있으나 전반적으로 자연스럽습니다.
                
                **종합 평가:** 면접 답변으로서 매우 우수한 수준입니다. 실무 투입이 가능한 역량을 보여주고 있습니다.
                
                ---
                """, question);

        // 실제 처리 시뮬레이션 (약간의 지연)
        Thread.sleep(20000);  // 실제 Claude API 호출 시간 시뮬레이션

        // DB 저장 (실제와 동일)
        Feedback feedback = Feedback.builder()
                .contents(mockContent)
                .createdAt(LocalDateTime.now())
                .video(video)
                .build();
        feedbackRepository.save(feedback);

        return FeedbackReturnDTO.builder()
                .feedbackId(feedback.getId())
                .videoId(video.getId())
                .contents(feedback.getContents())
                .createdAt(feedback.getCreatedAt())
                .build();
    }

    public Feedback findFeedback(Long feedbackId) throws Exception {
        Feedback feedback = feedbackRepository.findById(feedbackId).orElse(null);
        return feedback;
    }
}
