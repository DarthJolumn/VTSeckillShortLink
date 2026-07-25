package com.jolumn.livemallseckill.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> refundScript;
    private final int shardCount;

    public StockService(StringRedisTemplate redisTemplate,
                        @Value("${seckill.stock-shard-count:10}") int shardCount) {
        this.redisTemplate = redisTemplate;
        this.shardCount = shardCount;

        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setLocation(new ClassPathResource("lua/deduct_stock_single.lua"));
        this.deductScript.setResultType(Long.class);

        this.refundScript = new DefaultRedisScript<>();
        this.refundScript.setLocation(new ClassPathResource("lua/refund_stock.lua"));
        this.refundScript.setResultType(Long.class);
    }

    /** 初始化库存（活动上架时调用），按分片均匀分配 */
    public void initStock(Long activityId, int totalStock) {
        int base = totalStock / shardCount;
        int remainder = totalStock % shardCount;
        for (int i = 0; i < shardCount; i++) {
            int shardStock = base + (i < remainder ? 1 : 0);
            redisTemplate.opsForValue().set("stock:shard:{" + activityId + "}:" + i, String.valueOf(shardStock));
        }
        log.info("库存初始化: activityId={}, total={}, shards={}, base={}, remainder={}",
                activityId, totalStock, shardCount, base, remainder);
    }

    /**
     * 原子扣库存。返回值：
     *  200 = 扣减成功
     *  -1 = 重复下单
     *  -2 = 库存不足
     */
    public int deduct(Long activityId, Long userId) {
        String orderedKey = "ordered:{" + activityId + "}:" + userId;
        Long result = redisTemplate.execute(deductScript, List.of(orderedKey),
                userId.toString(), String.valueOf(shardCount), String.valueOf(activityId));

        int code = result != null ? result.intValue() : -2;
        if (code == 200) {
            log.info("库存扣减成功: activityId={}, userId={}, shard={}",
                    activityId, userId, userId % shardCount);
        }
        return code;
    }

    /** 回补库存 */
    public void refund(Long activityId, Long userId) {
        int shard = (int) (userId % shardCount);
        String stockKey = "stock:shard:{" + activityId + "}:" + shard;
        String orderedKey = "ordered:{" + activityId + "}:" + userId;
        redisTemplate.execute(refundScript, List.of(stockKey, orderedKey));
        log.info("库存回补: activityId={}, userId={}, shard={}", activityId, userId, shard);
    }
}
