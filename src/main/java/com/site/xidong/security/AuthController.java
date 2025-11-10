package com.site.xidong.security;

import com.site.xidong.siteUser.SiteUser;
import com.site.xidong.siteUser.SiteUserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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
    public ResponseEntity<SocialLoginResponse> callback(@PathVariable String provider, @RequestBody CallbackRequest request) {
        log.info("callback request: {}", request);
        try {
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
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/auth/refresh")  // URL 경로 변경
    public ResponseEntity<Token> refresh() {
        Token jwtToken = siteUserService.refresh();
        return ResponseEntity.ok(jwtToken);
    }

    @GetMapping("/auth/logout")  // 이미 있는 경로라면 유지
    public ResponseEntity<?> logout() {
        siteUserService.logout();
        return ResponseEntity.ok().build();
    }


}
