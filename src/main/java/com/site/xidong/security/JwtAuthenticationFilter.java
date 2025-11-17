package com.site.xidong.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
        String requestURI = servletRequest.getRequestURI();
        log.info("requestURI: {}", requestURI);

        List<String> permitAllUrls = Arrays.asList("/siteUser/signup", "/siteUser/login", "/auth/login/kakao/**","/login/oauth2/code/**", "**/error**", "**/oauth2/**", "/api/**", "/auth/kakao/callback", "/auth/naver/callback", "/feedback/**", "/actuator/**");
        List<String> authUrls = Arrays.asList("/questionSet/**", "/siteUser/myInfo", "/auth/refresh", "/video/**", "/auth/logout", "/comment/**", "/notification/**", "/question/**");

        boolean isPermitAll = permitAllUrls.stream().anyMatch(pattern ->
                new AntPathMatcher().match(pattern, requestURI));
        boolean isAuth = authUrls.stream().anyMatch(pattern ->
                new AntPathMatcher().match(pattern, requestURI));
        if(isPermitAll) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        } else if(isAuth) {
            String token = jwtTokenProvider.resolveToken(servletRequest);
                log.info("access token: {}", token);
            if (token != null && jwtTokenProvider.validateAccessToken(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                servletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "사용자 인증에 실패했습니다.");
                throw new ServletException("AccessToken is not valid.");
            }
        }
        filterChain.doFilter(servletRequest,servletResponse);
    }

}
