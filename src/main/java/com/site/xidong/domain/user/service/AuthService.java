package com.site.xidong.domain.user.service;

import com.site.xidong.domain.user.dto.KakaoDTO;
import com.site.xidong.domain.user.dto.NaverDTO;
import com.site.xidong.domain.user.dto.Token;
import com.site.xidong.domain.user.entity.LoginMethod;
import com.site.xidong.domain.user.entity.Role;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.user.repository.SiteUserRepository;
import com.site.xidong.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final KakaoUtil kakaoUtil;
    private final NaverUtil naverUtil;
    private final SiteUserRepository siteUserRepository;
    private final SiteUserService siteUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public KakaoDTO.OAuthToken oAuthLogin(String accessCode) {
        KakaoDTO.OAuthToken oAuthToken = kakaoUtil.requestToken(accessCode);
        KakaoDTO.KakaoProfile kakaoProfile = kakaoUtil.requestProfile(oAuthToken);
        String kakaoId = String.valueOf(kakaoProfile.getId());
        String email = kakaoProfile.getKakao_account().getEmail();

        Optional<SiteUser> existing = siteUserRepository.findByUsernameAndLoginMethod("KAKAO_" + kakaoId, LoginMethod.KAKAO);
        if (existing.isPresent()) {
            Token jwtToken = jwtTokenProvider.createToken(existing.get().getUsername(), existing.get().getRoles());
            siteUserService.updateToken(existing.get().getUsername(), jwtToken);

            KakaoDTO.OAuthToken newToken = new KakaoDTO.OAuthToken();
            newToken.setAccess_token(jwtToken.getAccessToken());
            newToken.setToken_type("bearer");
            newToken.setRefresh_token(jwtToken.getRefreshToken());
            newToken.setExpires_in(2 * 60 * 60);
            newToken.setScope("account_email profile_image profile_nickname");
            newToken.setRefresh_token_expires_in(14 * 24 * 60 * 60);
            return newToken;
        }

        SiteUser user = SiteUser.builder()
                .username("KAKAO_" + kakaoId)
                .email(email)
                .nickname(kakaoProfile.getKakao_account().getProfile().getNickname())
                .password(passwordEncoder.encode("null"))
                .imageUrl(kakaoProfile.getKakao_account().getProfile().getProfile_image_url())
                .loginMethod(LoginMethod.KAKAO)
                .build();
        user.addRole(Role.USER.getRole());
        user.updateToken(oAuthToken.getRefresh_token(), oAuthToken.getExpires_in());
        siteUserRepository.save(user);

        return oAuthToken;
    }

    @Transactional
    public NaverDTO.OAuthToken naverLogin(String accessCode, String state) {
        NaverDTO.OAuthToken oAuthToken = naverUtil.requestToken(accessCode, state);
        NaverDTO.NaverProfile naverProfile = naverUtil.requestProfile(oAuthToken);
        String naverId = naverProfile.getResponse().getId();
        String email = naverProfile.getResponse().getEmail();

        Optional<SiteUser> existing = siteUserRepository.findByUsernameAndLoginMethod("NAVER_" + naverId, LoginMethod.NAVER);
        if (existing.isPresent()) {
            Token jwtToken = jwtTokenProvider.createToken(existing.get().getUsername(), existing.get().getRoles());
            siteUserService.updateToken(existing.get().getUsername(), jwtToken);

            NaverDTO.OAuthToken newToken = new NaverDTO.OAuthToken();
            newToken.setAccess_token(jwtToken.getAccessToken());
            newToken.setRefresh_token(jwtToken.getRefreshToken());
            newToken.setToken_type("bearer");
            newToken.setExpires_in(2 * 60 * 60);
            return newToken;
        }

        SiteUser user = SiteUser.builder()
                .username("NAVER_" + naverId)
                .email(email)
                .nickname(naverProfile.getResponse().getNickname())
                .password(passwordEncoder.encode("null"))
                .imageUrl(naverProfile.getResponse().getProfile_image())
                .loginMethod(LoginMethod.NAVER)
                .build();
        user.addRole(Role.USER.getRole());
        user.updateToken(oAuthToken.getRefresh_token(), oAuthToken.getExpires_in());
        siteUserRepository.save(user);

        return oAuthToken;
    }
}
