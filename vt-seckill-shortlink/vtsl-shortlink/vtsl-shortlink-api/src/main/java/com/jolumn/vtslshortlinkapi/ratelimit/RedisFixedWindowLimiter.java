package com.jolumn.vtslshortlinkapi.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisFixedWindowLimiter {

    private static final String LUA =
            "local key = KEYS[1] " +
            "local limit = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local current = tonumber(redis.call('get', key) or '0') " +
            "if current >= limit then return 0 end " +
            "redis.call('incr', key) " +
            "if current == 0 then redis.call('expire', key, window) end " +
            "return 1";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisFixedWindowLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long result = redisTemplate.execute(script, List.of(key),
                String.valueOf(limit), String.valueOf(windowSeconds));
        return result != null && result == 1L;
    }
}
