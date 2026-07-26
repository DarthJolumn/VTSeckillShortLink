package com.jolumn.livemallshortlink.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    void testTryAcquire_Allowed() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L);

        boolean allowed = rateLimitService.tryAcquire("test-key", 10, 60);
        assertTrue(allowed);
    }

    @Test
    void testTryAcquire_Blocked() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L);

        boolean allowed = rateLimitService.tryAcquire("test-key", 10, 60);
        assertFalse(allowed);
    }

    @Test
    void testTryAcquire_NullResult() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(null);

        boolean allowed = rateLimitService.tryAcquire("test-key", 10, 60);
        assertFalse(allowed);
    }

    @Test
    void testTryAcquire_PassesCorrectArgs() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L);

        rateLimitService.tryAcquire("mykey", 100, 30);

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("mykey")),
                eq("100"),
                eq("30")
        );
    }
}
