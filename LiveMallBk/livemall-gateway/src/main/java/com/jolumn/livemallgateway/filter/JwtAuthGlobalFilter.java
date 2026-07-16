package com.jolumn.livemallgateway.filter;

import com.jolumn.livemallcommon.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtUtil jwtUtil;
    private final List<String> publicPaths;
    private final List<String> publicGetPaths;

    public JwtAuthGlobalFilter(
            JwtUtil jwtUtil,
            @org.springframework.beans.factory.annotation.Value("${gateway.auth.public-paths:}") String publicPathsCfg,
            @org.springframework.beans.factory.annotation.Value("${gateway.auth.public-get-paths:}") String publicGetPathsCfg) {
        this.jwtUtil = jwtUtil;
        this.publicPaths = parsePatterns(publicPathsCfg);
        this.publicGetPaths = parsePatterns(publicGetPathsCfg);
    }

    private static List<String> parsePatterns(String cfg) {
        if (cfg == null || cfg.isBlank()) return List.of();
        return Arrays.stream(cfg.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 完全公开路径 → 放行（不验 JWT，不注入 X-User-Id）
        if (matchAny(this.publicPaths, path)) {
            return chain.filter(exchange);
        }

        // 2. GET 公开路径 → 放行
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.GET && matchAny(this.publicGetPaths, path)) {
            return chain.filter(exchange);
        }

        // 3. 签名验签通过 → 跳过 JWT
        if (Boolean.TRUE.equals(exchange.getAttribute("signPassed"))) {
            return chain.filter(exchange);
        }

        // 4. 其余路径需 JWT
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return unauthorized(exchange, "请先登录");
        }

        try {
            Claims claims = jwtUtil.parse(token);
            Long userId = Long.parseLong(claims.getSubject());
            Integer role = claims.get("role", Integer.class);

            exchange.getAttributes().put("userId", userId);
            exchange.getAttributes().put("role", role);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId.toString())
                    .header("X-User-Role", role.toString())
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            return unauthorized(exchange, "Token 无效或已过期");
        }
    }

    @Override
    public int getOrder() {
        return -5;
    }

    private boolean matchAny(List<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty()) return false;
        return patterns.stream().anyMatch(p -> MATCHER.match(p, path));
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}