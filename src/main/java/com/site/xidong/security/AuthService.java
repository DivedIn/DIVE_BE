package com.site.xidong.security;

import com.site.xidong.siteUser.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final KakaoUtil kakaoUtil;
    private final NaverUtil naverUtil;
    private final SiteUserRepository siteUserRepository;
    private final SiteUserService siteUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public KakaoDTO.OAuthToken oAuthLogin(String accessCode) throws Exception {
        KakaoDTO.OAuthToken oAuthToken = kakaoUtil.requestToken(accessCode);
        KakaoDTO.KakaoProfile kakaoProfile = kakaoUtil.requestProfile(oAuthToken);
        String kakaoId = String.valueOf(kakaoProfile.getId()); // 카카오 고유 ID 사용
        String email = kakaoProfile.getKakao_account().getEmail();

        // 카카오 ID로 사용자 찾기
        Optional<SiteUser> siteUser = siteUserRepository.findByUsernameAndLoginMethod(
                "KAKAO_" + kakaoId,
                LoginMethod.KAKAO);

        if (siteUser.isPresent()) {
            // 이미 카카오로 가입한 경우, 로그인 처리
            Token jwtToken = jwtTokenProvider.createToken(siteUser.get().getUsername(), siteUser.get().getRoles());
            String accessToken = jwtToken.getAccessToken();
            String refreshToken = jwtToken.getRefreshToken();
            siteUserService.updateToken(siteUser.get().getUsername(), jwtToken);

            KakaoDTO.OAuthToken newToken = new KakaoDTO.OAuthToken();
            newToken.setAccess_token(accessToken);
            newToken.setToken_type("bearer");
            newToken.setRefresh_token(refreshToken);
            newToken.setExpires_in(2 * 60 * 60);
            newToken.setScope("account_email profile_image profile_nickname");
            newToken.setRefresh_token_expires_in(14 * 24 * 60 * 60);
            return newToken;
        }

        // 신규 가입 - 카카오 ID를 username으로 사용
        SiteUser user = SiteUser.builder()
                .username("KAKAO_" + kakaoId) // 카카오 ID를 prefix와 함께 사용
                .email(email)
                .nickname(kakaoProfile.getKakao_account().getProfile().getNickname())
                .password(passwordEncoder.encode("null"))
                .imageUrl(kakaoProfile.getKakao_account().getProfile().getProfile_image_url())
                .loginMethod(LoginMethod.KAKAO)
                .build();
        user.addRole(Role.USER.getRole());
        user.setCreatedAt(LocalDateTime.now());
        user.setRefreshToken(oAuthToken.getRefresh_token());
        user.setTokenIssueAt(LocalDateTime.now());
        user.setTokenValidTime(oAuthToken.getExpires_in());
        siteUserRepository.save(user);

        return oAuthToken;
    }

        public NaverDTO.OAuthToken naverLogin(String accessCode, String state) throws Exception {
            NaverDTO.OAuthToken oAuthToken = naverUtil.requestToken(accessCode, state);
            NaverDTO.NaverProfile naverProfile = naverUtil.requestProfile(oAuthToken);
            String naverId = naverProfile.getResponse().getId(); // 네이버 고유 ID 사용
            String email = naverProfile.getResponse().getEmail();

            // 네이버 ID로 사용자 찾기
            Optional<SiteUser> siteUser = siteUserRepository.findByUsernameAndLoginMethod(
                    "NAVER_" + naverId,
                    LoginMethod.NAVER
            );

            if (siteUser.isPresent()) {
                // 이미 네이버로 가입한 경우, 로그인 처리
                Token jwtToken = jwtTokenProvider.createToken(siteUser.get().getUsername(), siteUser.get().getRoles());
                String accessToken = jwtToken.getAccessToken();
                String refreshToken = jwtToken.getRefreshToken();
                siteUserService.updateToken(siteUser.get().getUsername(), jwtToken);

                NaverDTO.OAuthToken newToken = new NaverDTO.OAuthToken();
                newToken.setAccess_token(accessToken);
                newToken.setRefresh_token(refreshToken);
                newToken.setToken_type("bearer");
                newToken.setExpires_in(2 * 60 * 60);
                return newToken;
            }

            // 신규 가입 - 네이버 ID를 username으로 사용
            SiteUser user = SiteUser.builder()
                    .username("NAVER_" + naverId) // 네이버 ID를 prefix와 함께 사용
                    .email(email)
                    .nickname(naverProfile.getResponse().getNickname())
                    .password(passwordEncoder.encode("null"))
                    .imageUrl(naverProfile.getResponse().getProfile_image())
                    .loginMethod(LoginMethod.NAVER)
                    .build();
            user.addRole(Role.USER.getRole());
            user.setCreatedAt(LocalDateTime.now());
            user.setRefreshToken(oAuthToken.getRefresh_token());
            user.setTokenIssueAt(LocalDateTime.now());
            user.setTokenValidTime(oAuthToken.getExpires_in());
            siteUserRepository.save(user);

            return oAuthToken;
        }
}
