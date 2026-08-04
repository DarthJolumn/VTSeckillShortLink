package com.jolumn.vtslshortlinkapi.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RedisFixedWindowLimiter limiter;

    public RateLimitInterceptor(RedisFixedWindowLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod hm)) return true;

        RateLimit rl = hm.getMethodAnnotation(RateLimit.class);
        if (rl == null) rl = hm.getBeanType().getAnnotation(RateLimit.class);
        if (rl == null) return true;

        String ip = getClientIp(request);
        String key = "rate_limit:" + ip + ":" + request.getRequestURI();

        if (!limiter.tryAcquire(key, rl.limit(), rl.windowSeconds())) {
            log.warn("Rate limit exceeded: ip={}, path={}", ip, request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Too Many Requests\"}");
            return false;
        }
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
