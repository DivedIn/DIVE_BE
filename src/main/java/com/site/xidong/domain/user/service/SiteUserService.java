package com.site.xidong.domain.user.service;

import com.site.xidong.domain.user.dto.SiteUserDTO;
import com.site.xidong.domain.user.dto.SiteUserJoinDTO;
import com.site.xidong.domain.user.dto.SiteUserLoginDTO;
import com.site.xidong.domain.user.entity.LoginMethod;
import com.site.xidong.domain.user.entity.Role;
import com.site.xidong.domain.user.entity.SiteUser;
import com.site.xidong.domain.user.repository.SiteUserRepository;
import com.site.xidong.global.exception.CustomException;
import com.site.xidong.global.exception.ErrorCode;
import com.site.xidong.global.jwt.JwtTokenProvider;
import com.site.xidong.domain.user.dto.Token;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteUserService {
    private static final long DEFAULT_TOKEN_VALID_MS = 14 * 24 * 60 * 60 * 1000L;

    private final SiteUserRepository siteUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public Token join(SiteUserJoinDTO dto) {
        if (siteUserRepository.findSiteUserByUsername(dto.getUsername()).isPresent()) {
            throw new CustomException(ErrorCode.DUPLICATE_RESOURCE);
        }
        Optional<SiteUser> exist = siteUserRepository.findSiteUserByEmail(dto.getEmail());
        if (exist.isPresent()) {
            LoginMethod method = exist.get().getLoginMethod();
            if (method == LoginMethod.GENERAL) throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
            throw new CustomException(ErrorCode.USER_ALREADY_EXIST);
        }

        SiteUser siteUser = SiteUser.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname())
                .imageUrl("https://dive-s3-ver2.s3.ap-northeast-2.amazonaws.com/default_profile.png")
                .loginMethod(LoginMethod.GENERAL)
                .build();
        siteUser.addRole(Role.USER.getRole());

        Token jwtToken = jwtTokenProvider.createToken(siteUser.getUsername(), siteUser.getRoles());
        siteUser.updateToken(jwtToken.getRefreshToken(), DEFAULT_TOKEN_VALID_MS);
        siteUserRepository.save(siteUser);

        return jwtToken;
    }

    @Transactional
    public Token login(SiteUserLoginDTO dto) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(dto.getUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(dto.getPassword(), siteUser.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }
        Token jwtToken = jwtTokenProvider.createToken(siteUser.getUsername(), siteUser.getRoles());
        siteUser.updateToken(jwtToken.getRefreshToken(), DEFAULT_TOKEN_VALID_MS);
        siteUserRepository.save(siteUser);
        return jwtToken;
    }

    @Transactional
    public void logout(String username) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        siteUser.clearToken();
        siteUserRepository.save(siteUser);
    }

    @Transactional
    public Token refresh(String username) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Token jwtToken = jwtTokenProvider.createToken(siteUser.getUsername(), siteUser.getRoles());
        siteUser.updateToken(jwtToken.getRefreshToken(), DEFAULT_TOKEN_VALID_MS);
        siteUserRepository.save(siteUser);
        return jwtToken;
    }

    @Transactional
    public SiteUser updateToken(String username, Token token) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        siteUser.updateToken(token.getRefreshToken(), siteUser.getTokenValidTime());
        return siteUserRepository.save(siteUser);
    }

    public SiteUserDTO getMyInfo(String username) {
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return SiteUserDTO.builder()
                .username(siteUser.getUsername())
                .email(siteUser.getEmail())
                .nickname(siteUser.getNickname())
                .imageUrl(siteUser.getImageUrl())
                .build();
    }
}
