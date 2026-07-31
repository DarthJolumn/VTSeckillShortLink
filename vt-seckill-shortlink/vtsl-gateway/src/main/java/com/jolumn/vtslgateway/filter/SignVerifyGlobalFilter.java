package com.jolumn.vtslgateway.filter;

import com.jolumn.vtslcommon.util.HmacUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class SignVerifyGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(SignVerifyGlobalFilter.class);

    private static final long TIME_WINDOW = 5 * 60 * 1000L; // ±5min
    private static final String SIGN_PASSED_ATTR = "signPassed";

    private final ReactiveStringRedisTemplate redisTemplate;

    public SignVerifyGlobalFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${gateway.sign.app-secrets:}") String appSecretsConfig) {
        this.redisTemplate = redisTemplate;
        if (appSecretsConfig != null && !appSecretsConfig.isBlank()) {
            for (String pair : appSecretsConfig.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    HmacUtil.setSecret(kv[0].trim(), kv[1].trim());
                }
            }
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String timestamp = headers.getFirst("X-Timestamp");
        String nonce = headers.getFirst("X-Nonce");
        String sign = headers.getFirst("X-Sign");

        if (timestamp == null || nonce == null || sign == null) {
            return chain.filter(exchange);
        }

        try {
            long ts = Long.parseLong(timestamp);
            if (Math.abs(System.currentTimeMillis() - ts) > TIME_WINDOW) {
                return unauthorized(exchange, "Timestamp expired");
            }
        } catch (NumberFormatException e) {
            return unauthorized(exchange, "Invalid timestamp");
        }

        String nonceKey = "nonce:" + nonce;
        return redisTemplate.opsForValue()
                .setIfAbsent(nonceKey, "1", Duration.ofSeconds(60))
                .flatMap(success -> {
                    if (Boolean.FALSE.equals(success)) {
                        return unauthorized(exchange, "Nonce reused");
                    }

                    String appKey = headers.getFirst("X-AppKey");
                    if (appKey == null) {
                        return unauthorized(exchange, "Missing X-AppKey");
                    }

                    if (!HmacUtil.verify(timestamp + nonce, appKey, sign)) {
                        log.warn("签名校验失败: appKey={}", appKey);
                        return unauthorized(exchange, "Invalid signature");
                    }

                    exchange.getAttributes().put(SIGN_PASSED_ATTR, true);
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return -10;
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
