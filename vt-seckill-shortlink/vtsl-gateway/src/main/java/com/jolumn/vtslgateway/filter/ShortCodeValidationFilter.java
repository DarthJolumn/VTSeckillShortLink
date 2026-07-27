package com.jolumn.vtslgateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * 短码格式校验过滤器
 * <p>
 * 校验策略：
 * - 算法码（P/A/L 前缀）：前缀 + Base58（数字+大写字母+小写字母，不含 0 O I l）
 * - DB 码：纯字母数字，2-20 位
 * <p>
 * 直接拒绝非法请求，减少后端无效负载
 */
@Component
public class ShortCodeValidationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ShortCodeValidationFilter.class);

    /** Base58 字符集（与 ShortCodeCodec 一致）：数字 + 大写字母(无 O I) + 小写字母(无 l) */
    private static final String BASE58 = "[1-9A-HJ-NP-Za-km-z]+";

    /** 算法码：P/A/L 前缀 + Base58(1-11位) */
    private static final Pattern ALGORITHM_CODE = Pattern.compile(
            "^[PAL]" + BASE58 + "{1,11}$");

    /** DB 码：纯字母数字 2-20 位 */
    private static final Pattern DB_CODE = Pattern.compile(
            "^[a-zA-Z0-9]{2,20}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 只校验 GET /s/{shortCode}
        if (!exchange.getRequest().getMethod().name().equals("GET")
                || !path.matches("^/s/[^/]+$")) {
            return chain.filter(exchange);
        }

        String shortCode = path.substring("/s/".length());

        if (!isValid(shortCode)) {
            log.warn("短码格式非法，拒绝请求: shortCode={}", shortCode);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 在认证过滤器之前执行，减少无效认证开销
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isValid(String shortCode) {
        if (shortCode == null || shortCode.length() < 2) return false;

        char first = shortCode.charAt(0);
        if (first == 'P' || first == 'A' || first == 'L') {
            return ALGORITHM_CODE.matcher(shortCode).matches();
        }
        return DB_CODE.matcher(shortCode).matches();
    }
}
