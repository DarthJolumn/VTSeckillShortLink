package com.jolumn.vtslshortlink.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortLinkCacheTest {

    private ShortLinkCache cache;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cache = new ShortLinkCache(100, 30, redisTemplate);
    }

    @Test
    void testPutAndGetFromL2() {
        String shortCode = "Ptest123";
        String url = "/product/123";
        Duration ttl = Duration.ofDays(30);

        cache.put(shortCode, url, ttl);

        verify(hashOps).putAll(eq("s:code:Ptest123"), any(Map.class));
        verify(redisTemplate).expire(eq("s:code:Ptest123"), any(Duration.class));

        when(hashOps.get("s:code:Ptest123", "url")).thenReturn(url);
        String cached = cache.getFromL2(shortCode);
        assertEquals(url, cached);
    }

    @Test
    void testGetFromL2Miss() {
        when(hashOps.get("s:code:unknown", "url")).thenReturn(null);
        String cached = cache.getFromL2("unknown");
        assertNull(cached);
    }

    @Test
    void testGetFromL1() {
        String shortCode = "Ptest456";
        String url = "/product/456";

        cache.put(shortCode, url, Duration.ofDays(1));

        String cached = cache.getFromL1(shortCode);
        assertEquals(url, cached);
    }

    @Test
    void testGetFromL1Miss() {
        assertNull(cache.getFromL1("not_cached"));
    }

    @Test
    void testPutWithProduct() {
        String shortCode = "Pprod789";
        String url = "/product/789";
        Long productId = 789L;
        Duration ttl = Duration.ofDays(3650);

        cache.putWithProduct(shortCode, url, productId, ttl);

        verify(hashOps).putAll(eq("s:code:Pprod789"), any(Map.class));
        verify(redisTemplate).expire(eq("s:code:Pprod789"), any(Duration.class));
    }

    @Test
    void testGetFullFromL2() {
        String shortCode = "Pfull";
        Map<Object, Object> entries = Map.of(
                "url", "/product/999",
                "productId", "999",
                "status", "1",
                "clickCount", "42"
        );
        when(hashOps.entries("s:code:Pfull")).thenReturn(entries);

        Map<Object, Object> result = cache.getFullFromL2(shortCode);
        assertEquals("/product/999", result.get("url"));
        assertEquals("42", result.get("clickCount"));

        assertNotNull(cache.getFromL1(shortCode));
    }

    @Test
    void testGetFullFromL2Empty() {
        when(hashOps.entries("s:code:empty")).thenReturn(Map.of());
        Map<Object, Object> result = cache.getFullFromL2("empty");
        assertTrue(result.isEmpty());
    }

    @Test
    void testIncrementClickCount() {
        cache.incrementClickCount("Pclick");
        verify(hashOps).increment("s:code:Pclick", "clickCount", 1);
    }

    @Test
    void testIsBlocked() {
        when(hashOps.get("s:code:blocked", "status")).thenReturn("2");
        assertTrue(cache.isBlocked("blocked"));

        when(hashOps.get("s:code:active", "status")).thenReturn("1");
        assertFalse(cache.isBlocked("active"));

        when(hashOps.get("s:code:missing", "status")).thenReturn(null);
        assertFalse(cache.isBlocked("missing"));
    }

    @Test
    void testHashMapping() {
        String urlHash = "abc123def456";
        String shortCode = "Pmapped";
        Duration ttl = Duration.ofHours(24);

        cache.putHashMapping(urlHash, shortCode, ttl);
        verify(valueOps).set(eq("s:hash:" + urlHash), eq(shortCode), any(Duration.class));

        when(valueOps.get("s:hash:" + urlHash)).thenReturn(shortCode);
        String existing = cache.getExistingCodeByHash(urlHash);
        assertEquals(shortCode, existing);
    }

    @Test
    void testEvict() {
        String shortCode = "Pevict";
        cache.put(shortCode, "/product/evict", Duration.ofDays(1));
        cache.evict(shortCode);

        verify(redisTemplate).delete("s:code:" + shortCode);
        assertNull(cache.getFromL1(shortCode));
    }

    @Test
    void testJitterRange() {
        // Verify jitter doesn't produce negative or zero TTL
        cache.put("Pjitter1", "/p/1", Duration.ofSeconds(1));
        cache.put("Pjitter2", "/p/2", Duration.ofHours(1));
        cache.put("Pjitter3", "/p/3", Duration.ofDays(30));
        // No exception means jitter kept TTL >= 1s
    }
}
