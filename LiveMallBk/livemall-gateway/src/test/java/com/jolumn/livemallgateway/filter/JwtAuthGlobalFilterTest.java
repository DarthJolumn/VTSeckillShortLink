package com.jolumn.livemallgateway.filter;

import com.jolumn.livemallcommon.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthGlobalFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthGlobalFilter(jwtUtil, "/auth/login,/auth/register,/auth/refresh", "");
    }

    @Test
    void filter_whiteList_skipsJwt() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(jwtUtil, never()).parse(anyString());
        verify(chain).filter(exchange);
    }

    @Test
    void filter_missingToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_invalidToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.parse("invalid.token.here")).thenThrow(new RuntimeException("Invalid signature"));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_validToken_injectsHeaders() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token.here")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("sub", "123");
        claimsMap.put("role", 1);
        claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
        Claims claims = new DefaultClaims(claimsMap);

        when(jwtUtil.parse("valid.token.here")).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getAttributes().get("userId")).isEqualTo(123L);
        assertThat(exchange.getAttributes().get("role")).isEqualTo(1);
    }

    @Test
    void filter_expiredToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired.token.here")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.parse("expired.token.here")).thenThrow(new RuntimeException("Token expired"));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getOrder_returnsNegative5() {
        assertThat(filter.getOrder()).isEqualTo(-5);
    }
}
