package com.site.xidong.domain.user.controller;

import com.site.xidong.domain.user.dto.KakaoDTO;
import com.site.xidong.domain.user.dto.NaverDTO;
import com.site.xidong.domain.user.dto.Token;
import com.site.xidong.domain.user.service.AuthService;
import com.site.xidong.domain.user.service.SiteUserService;
import com.site.xidong.global.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final SiteUserService siteUserService;

    @Data
    public static class SocialLoginResponse {
        private String accessToken;
        private String refreshToken;
        private String key;
    }

    @Data
    public static class CallbackRequest {
        private String code;
        private String state;
    }

    @PostMapping("/auth/{provider}/callback")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> callback(
            @PathVariable String provider,
            @RequestBody CallbackRequest request) {
        log.info("callback request: {}", request);
        SocialLoginResponse response = new SocialLoginResponse();
        if (provider.equalsIgnoreCase("kakao")) {
            KakaoDTO.OAuthToken kakaoToken = authService.oAuthLogin(request.getCode());
            response.setAccessToken(kakaoToken.getAccess_token());
            response.setRefreshToken(kakaoToken.getRefresh_token());
            response.setKey(kakaoToken.getAccess_token());
        } else if (provider.equalsIgnoreCase("naver")) {
            NaverDTO.OAuthToken naverToken = authService.naverLogin(request.getCode(), request.getState());
            response.setAccessToken(naverToken.getAccess_token());
            response.setRefreshToken(naverToken.getRefresh_token());
            response.setKey(naverToken.getAccess_token());
        } else {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<Token>> refresh(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(ApiResponse.success(siteUserService.refresh(ud.getUsername())));
    }

    @GetMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails ud) {
        siteUserService.logout(ud.getUsername());
        return ResponseEntity.noContent().build();
    }
}
