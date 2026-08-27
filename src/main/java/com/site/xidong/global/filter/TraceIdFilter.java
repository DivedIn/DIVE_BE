package com.site.xidong.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * [관측 가능성] 요청 진입점에서 traceId를 만들어 MDC에 심는다. 요청을 처리하는 동안 나오는
 * 모든 로그 라인(동기 처리는 물론, TaskDecorator로 전파되는 @Async 경계 너머까지)이 이
 * traceId로 묶인다. 응답 헤더에도 실어서 클라이언트가 원하면 이 값으로 문의할 수 있게 한다.
 *
 * Spring Security의 FilterChainProxy보다 먼저 돌아야(HIGHEST_PRECEDENCE) permitAll인
 * 요청을 포함해 전 요청에 traceId가 붙는다 — JwtAuthenticationFilter의 로그 라인도 예외 없이.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
