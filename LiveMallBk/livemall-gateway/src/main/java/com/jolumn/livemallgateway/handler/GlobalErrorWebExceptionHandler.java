package com.jolumn.livemallgateway.handler;

import com.jolumn.livemallcommon.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Order(-1)
@Component
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        int status;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            status = rse.getStatusCode().value();
            message = rse.getReason() != null ? rse.getReason() : rse.getMessage();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            message = "系统繁忙，请稍后重试";
            log.error("Gateway 异常", ex);
        }

        response.setStatusCode(HttpStatus.valueOf(status));

        Result<?> result = Result.error(status, message);
        String body = toJson(result);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String toJson(Result<?> result) {
        return "{\"code\":" + result.getCode()
                + ",\"message\":\"" + escapeJson(result.getMessage()) + "\""
                + ",\"data\":null"
                + ",\"timestamp\":" + result.getTimestamp() + "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
