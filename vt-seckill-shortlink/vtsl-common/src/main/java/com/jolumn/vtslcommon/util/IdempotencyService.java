package com.jolumn.vtslcommon.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public boolean tryAcquire(String key) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue()
                            .setIfAbsent(PREFIX + key, "1", 5, TimeUnit.MINUTES));
        } catch (Exception e) {
            // Redis 不可用时降级：放行请求，失去幂等保护但不阻断业务
            log.warn("幂等检查失败(Redis不可用), 降级放行: key={}", key, e);
            return true;
        }
    }

    public <T> T get(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(PREFIX + key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            log.warn("idempotency key {} 反序列化失败", key, e);
            return null;
        }
    }

    public void set(String key, Object value, long ttl, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(PREFIX + key, json, ttl, unit);
        } catch (JacksonException e) {
            log.error("idempotency key {} 序列化失败", key, e);
        } catch (Exception e) {
            // Redis 不可用时降级：缓存写入失败不影响主流程
            log.warn("幂等缓存写入失败(Redis不可用): key={}", key, e);
        }
    }
}
