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
    private final boolean dedupEnabled;

    public StockService(StringRedisTemplate redisTemplate,
                        @Value("${seckill.dedup-enabled:true}") boolean dedupEnabled) {
        this.redisTemplate = redisTemplate;
        this.dedupEnabled = dedupEnabled;

        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setLocation(new ClassPathResource("lua/deduct_stock.lua"));
        this.deductScript.setResultType(Long.class);

        this.refundScript = new DefaultRedisScript<>();
        this.refundScript.setLocation(new ClassPathResource("lua/refund_stock.lua"));
        this.refundScript.setResultType(Long.class);
    }

    /** 初始化库存（活动上架时调用） */
    public void initStock(Long activityId, int totalStock) {
        redisTemplate.opsForValue().set("stock:{" + activityId + "}", String.valueOf(totalStock));
        log.info("库存初始化: activityId={}, total={}", activityId, totalStock);
    }

    /**
     * 原子扣库存。返回值：
     *  200 = 扣减成功
     *  -1 = 重复下单
     *  -2 = 库存不足
     *  -3 = 活动未初始化
     */
    public int deduct(Long activityId, Long userId) {
        String stockKey = "stock:{" + activityId + "}";
        String orderedKey = "ordered:{" + activityId + "}:" + userId;
        String dedupFlag = dedupEnabled ? "1" : "0";
        Long result = redisTemplate.execute(deductScript, List.of(stockKey, orderedKey), dedupFlag);

        int code = result != null ? result.intValue() : -2;
        if (code == 200) {
            log.info("库存扣减成功: activityId={}, userId={}", activityId, userId);
        }
        return code;
    }

    /** 回补库存 */
    public void refund(Long activityId, Long userId) {
        String stockKey = "stock:{" + activityId + "}";
        String orderedKey = "ordered:{" + activityId + "}:" + userId;
        redisTemplate.execute(refundScript, List.of(stockKey, orderedKey));
        log.info("库存回补: activityId={}, userId={}", activityId, userId);
    }
}
