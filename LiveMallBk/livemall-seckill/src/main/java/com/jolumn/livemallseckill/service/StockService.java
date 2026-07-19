package com.jolumn.livemallseckill.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> refundScript;
    private final int shardCount;

    public int getShardCount() { return shardCount; }

    public StockService(StringRedisTemplate redisTemplate,
                        @Value("${seckill.shard-count:4}") int shardCount) {
        this.redisTemplate = redisTemplate;
        this.shardCount = shardCount;

        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setLocation(new ClassPathResource("lua/deduct_stock.lua"));
        this.deductScript.setResultType(Long.class);

        this.refundScript = new DefaultRedisScript<>();
        this.refundScript.setLocation(new ClassPathResource("lua/refund_stock.lua"));
        this.refundScript.setResultType(Long.class);
    }

    /**
     * 原子扣库存。返回值：
     *  200 = 扣减成功
     *  -1 = 重复下单
     *  -2 = 库存不足
     */
    public int deduct(Long activityId, Long userId) {
        String stockKey = "stock:total:" + activityId;
        String orderedKey = "ordered:" + activityId + ":" + userId;
        List<String> keys = List.of(stockKey, orderedKey);

        Long result = redisTemplate.execute(deductScript, keys,
                userId.toString(), String.valueOf(shardCount), String.valueOf(activityId));

        int code = result != null ? result.intValue() : -2;
        if (code == 200) {
            log.info("库存扣减成功: activityId={}, userId={}", activityId, userId);
        }
        return code;
    }

    /** 回补库存（自动计算分片） */
    public void refund(Long activityId, Long userId) {
        int shard = (int) (userId % shardCount);
        refund(activityId, userId, shard);
    }

    /**
     * 回补库存（超时取消/退款时调用，指定分片）
     */
    public void refund(Long activityId, Long userId, int shard) {
        String stockKey = "stock:shard:" + activityId + ":" + shard;
        String orderedKey = "ordered:" + activityId + ":" + userId;
        redisTemplate.execute(refundScript, List.of(stockKey, orderedKey));
        log.info("库存回补: activityId={}, userId={}, shard={}", activityId, userId, shard);
    }
}
