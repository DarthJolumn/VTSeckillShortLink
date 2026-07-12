package com.jolumn.livemallcommon.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue()
                        .setIfAbsent(PREFIX + key, "1", 5, TimeUnit.MINUTES));
    }

    public <T> T get(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(PREFIX + key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("idempotency key {} 反序列化失败", key, e);
            return null;
        }
    }

    public void set(String key, Object value, long ttl, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(PREFIX + key, json, ttl, unit);
        } catch (JsonProcessingException e) {
            log.error("idempotency key {} 序列化失败", key, e);
        }
    }
}
