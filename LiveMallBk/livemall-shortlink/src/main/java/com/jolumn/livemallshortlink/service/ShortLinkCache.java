package com.jolumn.livemallshortlink.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class ShortLinkCache {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkCache.class);
    private static final String KEY_PREFIX = "s:code:";
    private static final String HASH_PREFIX = "s:hash:";

    private static final String FIELD_URL = "url";
    private static final String FIELD_PRODUCT_ID = "productId";
    private static final String FIELD_EXPIRE_AT = "expireAt";
    private static final String FIELD_CLICK_COUNT = "clickCount";
    private static final String FIELD_STATUS = "status";

    /** TTL 随机抖动比例 ±20%，防缓存雪崩 */
    private static final double TTL_JITTER = 0.1;

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

    public String getFromL1(String shortCode) {
        return localCache.getIfPresent(shortCode);
    }

    public String getFromL2(String shortCode) {
        try {
            Object val = redisTemplate.opsForHash().get(KEY_PREFIX + shortCode, FIELD_URL);
            String url = val != null ? val.toString() : null;
            if (url != null) {
                localCache.put(shortCode, url);
            }
            return url;
        } catch (RedisSystemException e) {
            if (isWrongType(e)) {
                redisTemplate.delete(KEY_PREFIX + shortCode);
            }
            return null;
        }
    }

    public Map<Object, Object> getFullFromL2(String shortCode) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(KEY_PREFIX + shortCode);
            if (!entries.isEmpty()) {
                Object url = entries.get(FIELD_URL);
                if (url != null) {
                    localCache.put(shortCode, url.toString());
                }
            }
            return entries;
        } catch (RedisSystemException e) {
            if (isWrongType(e)) {
                redisTemplate.delete(KEY_PREFIX + shortCode);
            }
            return Map.of();
        }
    }

    /** 读路径回填缓存（带 TTL 抖动防雪崩） */
    public void put(String shortCode, String originalUrl, Duration ttl) {
        localCache.put(shortCode, originalUrl);

        Map<String, String> hash = new HashMap<>();
        hash.put(FIELD_URL, originalUrl);
        hash.put(FIELD_STATUS, "1");
        hash.put(FIELD_CLICK_COUNT, "0");
        redisTemplate.opsForHash().putAll(KEY_PREFIX + shortCode, hash);
        redisTemplate.expire(KEY_PREFIX + shortCode, jitter(ttl));

        log.debug("缓存写入(Hash): shortCode={}, ttl={}", shortCode, ttl);
    }

    public void putWithProduct(String shortCode, String originalUrl, Long productId, Duration ttl) {
        localCache.put(shortCode, originalUrl);

        Map<String, String> hash = new HashMap<>();
        hash.put(FIELD_URL, originalUrl);
        hash.put(FIELD_PRODUCT_ID, String.valueOf(productId));
        hash.put(FIELD_STATUS, "1");
        hash.put(FIELD_CLICK_COUNT, "0");
        redisTemplate.opsForHash().putAll(KEY_PREFIX + shortCode, hash);
        redisTemplate.expire(KEY_PREFIX + shortCode, jitter(ttl));

        log.debug("缓存写入(Hash+productId): shortCode={}, productId={}, ttl={}", shortCode, productId, ttl);
    }

    /** Cache Aside：写 DB 后删除 Redis + L1 缓存 */
    public void evict(String shortCode) {
        localCache.invalidate(shortCode);
        redisTemplate.delete(KEY_PREFIX + shortCode);
        log.debug("缓存删除: shortCode={}", shortCode);
    }

    public void incrementClickCount(String shortCode) {
        redisTemplate.opsForHash().increment(KEY_PREFIX + shortCode, FIELD_CLICK_COUNT, 1);
    }

    public boolean isBlocked(String shortCode) {
        try {
            Object status = redisTemplate.opsForHash().get(KEY_PREFIX + shortCode, FIELD_STATUS);
            return "2".equals(String.valueOf(status));
        } catch (RedisSystemException e) {
            if (isWrongType(e)) {
                redisTemplate.delete(KEY_PREFIX + shortCode);
            }
            return false;
        }
    }

    /** 判断异常是否为 WRONGTYPE（key 类型不匹配），兼容不同 Lettuce 版本的消息格式 */
    private boolean isWrongType(RedisSystemException e) {
        String msg = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage()
                : e.getMessage();
        return msg != null && msg.toUpperCase().contains("WRONGTYPE");
    }

    public String getExistingCodeByHash(String urlHash) {
        return redisTemplate.opsForValue().get(HASH_PREFIX + urlHash);
    }

    public void putHashMapping(String urlHash, String shortCode, Duration ttl) {
        redisTemplate.opsForValue().set(HASH_PREFIX + urlHash, shortCode, jitter(ttl));
    }

    /** 在 base TTL 上叠加随机抖动 ±20%，防缓存雪崩 */
    private Duration jitter(Duration base) {
        long baseSeconds = base.getSeconds();
        if (baseSeconds <= 0) return base;
        long jitter = (long) (baseSeconds * TTL_JITTER);
        long offset = ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Duration.ofSeconds(Math.max(baseSeconds + offset, 1));
    }
}
