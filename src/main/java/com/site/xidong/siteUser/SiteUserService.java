package com.site.xidong.siteUser;

import com.site.xidong.security.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class SiteUserService {
    private final SiteUserRepository siteUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public Token join(SiteUserJoinDTO siteUserJoinDTO) throws Exception {
        String username = siteUserJoinDTO.getUsername();
        Optional<SiteUser> target = siteUserRepository.findSiteUserByUsername(username);
        if(!target.isEmpty()) {
            throw new Exception("이미 사용 중인 아이디입니다.");
        }
        String email = siteUserJoinDTO.getEmail();
        Optional<SiteUser> exist = siteUserRepository.findSiteUserByEmail(email);
        if(!exist.isEmpty() && exist.get().getLoginMethod().equals(LoginMethod.GENERAL)) {
            throw new Exception("이미 해당 이메일로 가입된 계정이 있습니다.");
        } else if(!exist.isEmpty() && exist.get().getLoginMethod().equals(LoginMethod.KAKAO)) {
            throw new Exception("이미 카카오로 가입하셨습니다.");
        }  else if(!exist.isEmpty() && exist.get().getLoginMethod().equals(LoginMethod.NAVER)) {
            throw new Exception("이미 네이버로 가입하셨습니다.");
        }
        SiteUser siteUser = SiteUser.builder()
                .username(siteUserJoinDTO.getUsername())
                .email(siteUserJoinDTO.getEmail())
                .password(passwordEncoder.encode(siteUserJoinDTO.getPassword()))
                .nickname(siteUserJoinDTO.getNickname())
                .imageUrl("https://dive-s3-ver2.s3.ap-northeast-2.amazonaws.com/default_profile.png")
                .loginMethod(LoginMethod.GENERAL)
                .build();
        siteUser.addRole(Role.USER.getRole());
        siteUser.setCreatedAt(LocalDateTime.now());


        Token jwtToken = jwtTokenProvider.createToken(siteUser.getUsername(), siteUser.getRoles());
        String accessToken = jwtToken.getAccessToken();
        String refreshToken = jwtToken.getRefreshToken();

        siteUser.setRefreshToken(refreshToken);
        siteUser.setTokenIssueAt(LocalDateTime.now());
        siteUser.setTokenValidTime(14 * 24 * 60 * 60 * 1000L);
        siteUserRepository.save(siteUser);

        return jwtToken;
    }

    public Token login(SiteUserLoginDTO siteUserLoginDTO) throws UsernameNotFoundException, Exception {
        String username = siteUserLoginDTO.getUsername();
        Optional<SiteUser> exist = siteUserRepository.findSiteUserByUsername(username);
        if(exist.isEmpty()) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }
        if(!passwordEncoder.matches(siteUserLoginDTO.getPassword(), exist.get().getPassword())) {
            throw new Exception("비밀번호가 틀렸습니다.");
        }
        Token jwtToken = jwtTokenProvider.createToken(exist.get().getUsername(), exist.get().getRoles());
        String accessToken = jwtToken.getAccessToken();
        String refreshToken = jwtToken.getRefreshToken();
        updateToken(exist.get().getUsername(), jwtToken);
        return jwtToken;
    }

    public void logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        siteUser.setRefreshToken(null);
        siteUser.setTokenIssueAt(null);
        siteUser.setTokenValidTime(0L);
        siteUserRepository.save(siteUser);
    }

    public Token refresh() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        Token jwtToken = jwtTokenProvider.createToken(siteUser.getUsername(), siteUser.getRoles());
        String accessToken = jwtToken.getAccessToken();
        String refreshToken = jwtToken.getRefreshToken();

        siteUser.setRefreshToken(refreshToken);
        siteUser.setTokenIssueAt(LocalDateTime.now());
        siteUser.setTokenValidTime(14 * 24 * 60 * 60 * 1000L);
        siteUserRepository.save(siteUser);

        return jwtToken;
    }

    public SiteUser updateToken(String username, Token token) throws UsernameNotFoundException {
        Optional<SiteUser> selectedUser = siteUserRepository.findSiteUserByUsername(username);
        SiteUser updatedUser;
        if(selectedUser.isPresent()) {
            SiteUser siteUser = selectedUser.get();
            siteUser.setRefreshToken(token.getRefreshToken());
            siteUser.setTokenIssueAt(LocalDateTime.now());
            updatedUser = siteUserRepository.save(siteUser);
        } else {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }
        return updatedUser;
    }

    public SiteUserDTO getMyInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SiteUserSecurityDTO siteUserSecurityDTO = (SiteUserSecurityDTO) auth.getPrincipal();
        SiteUser siteUser = siteUserRepository.findSiteUserByUsername(siteUserSecurityDTO.getUsername()).get();
        SiteUserDTO siteUserDTO = SiteUserDTO.builder()
                .username(siteUser.getUsername())
                .email(siteUser.getEmail())
                .nickname(siteUser.getNickname())
                .imageUrl(siteUser.getImageUrl())
                .build();
        return siteUserDTO;
    }
}
