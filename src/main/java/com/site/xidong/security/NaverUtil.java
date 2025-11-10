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
public class NaverUtil {
    @Value("${spring.security.oauth2.client.registration.naver.client-id}")
    private String client;
    @Value("${spring.security.oauth2.client.registration.naver.client-secret}")
    private String client_secret;

    public NaverDTO.OAuthToken requestToken(String accessCode, String state) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", client);
        params.add("client_secret", client_secret);
        params.add("code", accessCode);
        params.add("state", state);

        HttpEntity<MultiValueMap<String, String>> naverTokenRequest = new HttpEntity<>(params, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "https://nid.naver.com/oauth2.0/token",
                HttpMethod.POST,
                naverTokenRequest,
                String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NaverDTO.OAuthToken oAuthToken = null;
        try {
            oAuthToken  = objectMapper.readValue(response.getBody(), NaverDTO.OAuthToken.class);
            log.info("oAuthToken : " + oAuthToken.getAccess_token());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return oAuthToken;
    }

    public NaverDTO.NaverProfile requestProfile(NaverDTO.OAuthToken oAuthToken) {
        RestTemplate restTemplate2 = new RestTemplate();
        HttpHeaders headers2 = new HttpHeaders();

        headers2.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        headers2.add("Authorization","Bearer "+ oAuthToken.getAccess_token());

        HttpEntity<MultiValueMap<String,String>> naverProfileRequest = new HttpEntity <>(headers2);

        ResponseEntity<String> response2 = restTemplate2.exchange(
                "https://openapi.naver.com/v1/nid/me",
                HttpMethod.GET,
                naverProfileRequest,
                String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NaverDTO.NaverProfile naverProfile = null;
        try {
            naverProfile = objectMapper.readValue(response2.getBody(), NaverDTO.NaverProfile.class);
            log.info("nickname : " + naverProfile.getResponse().getNickname());
            log.info("profile_image_url : " + naverProfile.getResponse().getProfile_image());
            log.info("email : " + naverProfile.getResponse().getEmail());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return naverProfile;
    }
}
