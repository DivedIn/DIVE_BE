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

    private final VideoRepository videoRepository;
    private final FeedbackRepository feedbackRepository;

    public FeedbackReturnDTO getFeedback(AnswerDTO answerDTO) throws Exception {
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

    public Feedback findFeedback(Long feedbackId) throws Exception {
        Feedback feedback = feedbackRepository.findById(feedbackId).orElse(null);
        return feedback;
    }
}
