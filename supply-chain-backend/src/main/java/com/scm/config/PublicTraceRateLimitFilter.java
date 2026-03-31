package com.scm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.common.Result;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PDF 要求：公开溯源接口防刷（简化为按 IP 滑动窗口限流）。
 */
@Component
@Order(1)
public class PublicTraceRateLimitFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/public/trace";
    private static final int MAX_REQUESTS = 80;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!request.getMethod().equalsIgnoreCase("GET") || uri == null || !uri.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        long now = System.currentTimeMillis();
        Window w = windows.compute(ip, (k, v) -> {
            if (v == null || now - v.startMs > WINDOW_MS) {
                return new Window(now);
            }
            return v;
        });

        int n = w.counter.incrementAndGet();
        if (n > MAX_REQUESTS) {
            // HTTP 200 + 业务码 429，便于前端 Axios 统一按 Result 解析并提示
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            byte[] body = objectMapper.writeValueAsBytes(Result.fail(429, "访问过于频繁，请稍后再试"));
            response.getOutputStream().write(body);
            return;
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private static final class Window {
        final long startMs;
        final AtomicInteger counter = new AtomicInteger(0);

        Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
