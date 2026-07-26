package com.jolumn.livemallshortlink.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jolumn.livemallshortlink.entity.ShortLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class ShortLinkCache {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkCache.class);
    private static final String KEY_PREFIX = "s:code:";
    private static final String HASH_PREFIX = "s:hash:";
    private static final String NULL_MARKER = "NULL";

    private final Cache<String, String> localCache;
    private final StringRedisTemplate redisTemplate;

    public ShortLinkCache(@Value("${shortlink.cache.max-size:100000}") int maxSize,
                          @Value("${shortlink.cache.ttl-seconds:3}") int ttlSeconds,
                          StringRedisTemplate redisTemplate) {
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .build();
        this.redisTemplate = redisTemplate;
    }

    /**
     * 从 L1 缓存获取
     */
    public String getFromL1(String shortCode) {
        String url = localCache.getIfPresent(shortCode);
        if (url != null && !NULL_MARKER.equals(url)) {
            return url;
        }
        return null;
    }

    /**
     * 从 L2 缓存获取
     */
    public String getFromL2(String shortCode) {
        String url = redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
        if (url != null && !NULL_MARKER.equals(url)) {
            // 回填 L1
            localCache.put(shortCode, url);
            return url;
        }
        return null;
    }

    /**
     * 写入缓存（L1 + L2）
     */
    public void put(String shortCode, String originalUrl, Duration ttl) {
        // L1: 本地缓存
        localCache.put(shortCode, originalUrl);
        
        // L2: Redis
        redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, originalUrl, ttl);
        
        log.debug("缓存写入: shortCode={}, ttl={}", shortCode, ttl);
    }

    /**
     * 写入空值标记（防穿透）
     */
    public void putNullMarker(String shortCode) {
        localCache.put(shortCode, NULL_MARKER);
        redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, NULL_MARKER, Duration.ofSeconds(30));
    }

    /**
     * 检查是否已存在（用于去重）
     */
    public String getExistingCodeByHash(String urlHash) {
        return redisTemplate.opsForValue().get(HASH_PREFIX + urlHash);
    }

    /**
     * 写入反向索引（URL hash → shortCode）
     */
    public void putHashMapping(String urlHash, String shortCode) {
        redisTemplate.opsForValue().set(HASH_PREFIX + urlHash, shortCode);
    }
}
