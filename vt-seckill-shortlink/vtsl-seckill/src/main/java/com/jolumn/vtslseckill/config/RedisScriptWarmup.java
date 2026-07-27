package com.jolumn.vtslseckill.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Redis Lua 脚本预加载（防首抖）
 * 启动时执行 SCRIPT LOAD，避免运行时编译开销
 */
@Component
public class RedisScriptWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisScriptWarmup.class);

    private final StringRedisTemplate redisTemplate;

    @Value("classpath:lua/deduct_stock.lua")
    private Resource deductScriptResource;

    @Value("classpath:lua/refund_stock.lua")
    private Resource refundScriptResource;

    public RedisScriptWarmup(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        // SCRIPT LOAD 预热，传入脚本内容（非 SHA）
        // 使用 RedisCallback 避免裸 getConnection() 泄漏
        String deductScript = new String(deductScriptResource.getInputStream().readAllBytes());
        String refundScript = new String(refundScriptResource.getInputStream().readAllBytes());

        String deductSha = redisTemplate.execute((RedisCallback<String>) conn ->
            conn.scriptLoad(deductScript.getBytes()));
        String refundSha = redisTemplate.execute((RedisCallback<String>) conn ->
            conn.scriptLoad(refundScript.getBytes()));

        log.info("Lua 脚本预热完成: deduct={}, refund={}", deductSha, refundSha);
    }
}
