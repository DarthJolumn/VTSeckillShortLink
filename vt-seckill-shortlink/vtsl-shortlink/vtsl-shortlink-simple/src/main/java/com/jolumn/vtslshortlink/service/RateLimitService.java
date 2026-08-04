package com.jolumn.vtslshortlink.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RateLimitService {

    private static final String RATE_LIMIT_LUA =
            "local key = KEYS[1] " +
            "local limit = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local current = tonumber(redis.call('get', key) or '0') " +
            "if current >= limit then return 0 end " +
            "redis.call('incr', key) " +
            "if current == 0 then redis.call('expire', key, window) end " +
            "return 1";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class);
    }

    /**
     * 滑动窗口限流
     * @param key 限流 key
     * @param limit 窗口内最大请求数
     * @param windowSeconds 窗口大小（秒）
     * @return true=允许，false=被限流
     */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long result = redisTemplate.execute(rateLimitScript, List.of(key),
                String.valueOf(limit), String.valueOf(windowSeconds));
        return result != null && result == 1L;
    }
}
