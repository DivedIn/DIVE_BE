package com.site.xidong.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class KakaoUtil {
    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String client;
    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String redirect;

    public KakaoDTO.OAuthToken requestToken(String accessCode) {
        try {
            log.info("access code로 카카오 토큰 요청 시도: {}", accessCode.substring(0, 10) + "...");
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", client);
            params.add("redirect_uri", redirect);
            params.add("code", accessCode);

            log.debug("client_id: {}, redirect_uri: {}", client, redirect);
            HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://kauth.kakao.com/oauth/token",
                    HttpMethod.POST,
                    kakaoTokenRequest,
                    String.class);

            log.debug("토큰 응답 상태: {}", response.getStatusCode());
            log.debug("토큰 응답 body: {}", response.getBody());

            ObjectMapper objectMapper = new ObjectMapper();
            KakaoDTO.OAuthToken oAuthToken = null;
            try {
                oAuthToken = objectMapper.readValue(response.getBody(), KakaoDTO.OAuthToken.class);
                if (oAuthToken != null && oAuthToken.getAccess_token() != null) {
                    log.info("카카오 엑세스 토큰 반환 성공");
                    return oAuthToken;
                } else {
                    log.error("null or invalid Kakao token");
                    throw new RuntimeException("null or invalid Kakao token");
                }
            } catch (JsonProcessingException e) {
                log.error("카카오 토큰 parsing 실패: {}", e.getMessage(), e);
                throw new RuntimeException("카카오 토큰 parsing 실패", e);
            }
        } catch (Exception e) {
            log.error("카카오 토큰 반환 실패: {}", e.getMessage(), e);
            throw new RuntimeException("카카오 토큰 반환 실패", e);
        }
    }

    public KakaoDTO.KakaoProfile requestProfile(KakaoDTO.OAuthToken oAuthToken) {
        try {
            if (oAuthToken == null || oAuthToken.getAccess_token() == null) {
                log.error("유효하지 않은 OAuth 토큰");
                throw new RuntimeException("유효하지 않은 OAuth 토큰");
            }

            log.info("카카오 프로필 요청");
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
            headers.add("Authorization","Bearer "+ oAuthToken.getAccess_token());

            HttpEntity<MultiValueMap<String,String>> kakaoProfileRequest = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://kapi.kakao.com/v2/user/me",
                    HttpMethod.GET,
                    kakaoProfileRequest,
                    String.class);

            log.debug("프로필 응답 상태: {}", response.getStatusCode());
            log.debug("프로필 응답 body: {}", response.getBody());

            ObjectMapper objectMapper = new ObjectMapper();
            KakaoDTO.KakaoProfile kakaoProfile = null;
            try {
                kakaoProfile = objectMapper.readValue(response.getBody(), KakaoDTO.KakaoProfile.class);
                if (kakaoProfile != null && kakaoProfile.getId() != 0) {
                    log.info("카카오 프로필 조회 성공. user ID: {}", kakaoProfile.getId());
                    return kakaoProfile;
                } else {
                    log.error("null or invalid Kakao profile");
                    throw new RuntimeException("Null or invalid Kakao profile received");
                }
            } catch (JsonProcessingException e) {
                log.error("카카오 프로필 parsing 실패: {}", e.getMessage(), e);
                throw new RuntimeException("카카오 프로필 parsing 실패", e);
            }
        } catch (Exception e) {
            log.error("카카오 프로필 조회 실패: {}", e.getMessage(), e);
            throw new RuntimeException("카카오 프로필 조회 실패", e);
        }
    }

}
