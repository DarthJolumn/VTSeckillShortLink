package com.jolumn.vtslgateway.filter;

import com.jolumn.vtslcommon.util.HmacUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignVerifyGlobalFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private GatewayFilterChain chain;

    private SignVerifyGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SignVerifyGlobalFilter(redisTemplate, "testapp:testsecret");
    }

    @Test
    void filter_noSignHeaders_skipsVerification() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(redisTemplate, never()).opsForValue();
        verify(chain).filter(exchange);
    }

    @Test
    void filter_expiredTimestamp_returns401() {
        long expiredTimestamp = System.currentTimeMillis() - 10 * 60 * 1000; // 10 分钟前
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .header("X-Timestamp", String.valueOf(expiredTimestamp))
                .header("X-Nonce", "test-nonce")
                .header("X-Sign", "test-sign")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_nonceReused_returns401() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long timestamp = System.currentTimeMillis();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", "reused-nonce")
                .header("X-Sign", "test-sign")
                .header("X-AppKey", "testapp")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(valueOperations.setIfAbsent(eq("nonce:reused-nonce"), eq("1"), any(java.time.Duration.class)))
                .thenReturn(Mono.just(false));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_invalidSignature_returns401() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long timestamp = System.currentTimeMillis();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", "test-nonce")
                .header("X-Sign", "wrong-signature")
                .header("X-AppKey", "testapp")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(valueOperations.setIfAbsent(eq("nonce:test-nonce"), eq("1"), any(java.time.Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_validSignature_passesAndMarksExchange() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long timestamp = System.currentTimeMillis();
        String nonce = "test-nonce";
        String secret = "testsecret";
        String expectedSign = HmacUtil.sha256(timestamp + nonce, secret);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", nonce)
                .header("X-Sign", expectedSign)
                .header("X-AppKey", "testapp")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(valueOperations.setIfAbsent(eq("nonce:" + nonce), eq("1"), any(java.time.Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat((Boolean) exchange.getAttribute("signPassed")).isTrue();
        verify(chain).filter(exchange);
    }

    @Test
    void filter_unknownAppKey_returns401() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long timestamp = System.currentTimeMillis();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", "test-nonce")
                .header("X-Sign", "test-sign")
                .header("X-AppKey", "unknown-app")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(valueOperations.setIfAbsent(eq("nonce:test-nonce"), eq("1"), any(java.time.Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getOrder_returnsNegative10() {
        assertThat(filter.getOrder()).isEqualTo(-10);
    }
}
