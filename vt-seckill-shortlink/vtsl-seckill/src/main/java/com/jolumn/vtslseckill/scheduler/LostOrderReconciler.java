package com.jolumn.vtslseckill.scheduler;

import com.jolumn.vtslseckill.biz.repository.SeckillOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 丢单对账：扫描 Redis ordered:{activityId}:{userId} 标记存在但 DB 无对应订单的记录，
 * 超时后幂等回补库存（refund_stock.lua），闭合"Redis 已扣、消息未达/回补失败"的库存泄漏洞。
 *
 * <p>场景：Redis 扣减成功 → Kafka 发送失败 → placeOrder 回补，但：
 * <ol>
 *   <li>placeOrder 在 refund 之前 JVM 崩溃 → ordered 标记在、DB 无订单</li>
 *   <li>refund 时 Redis 也挂了 → 回补没执行</li>
 * </ol>
 * 本任务周期性扫描兜底。refund Lua 幂等，重复扫描安全。</p>
 */
@Component
public class LostOrderReconciler {

    private static final Logger log = LoggerFactory.getLogger(LostOrderReconciler.class);
    private static final String ORDERED_PATTERN = "ordered:*";
    private static final String LOCK_KEY = "lock:reconciliation:lost-order";
    private static final long LOCK_TTL_SECONDS = 300;

    private final StringRedisTemplate redisTemplate;
    private final SeckillOrderRepository orderRepo;
    private final DefaultRedisScript<Long> refundScript;

    @Value("${seckill.lost-order-enabled:true}")
    private boolean lostOrderEnabled;

    @Value("${seckill.lost-order-scan-ms:600000}")
    private long scanIntervalMs;

    public LostOrderReconciler(StringRedisTemplate redisTemplate,
                               SeckillOrderRepository orderRepo) {
        this.redisTemplate = redisTemplate;
        this.orderRepo = orderRepo;
        this.refundScript = new DefaultRedisScript<>();
        this.refundScript.setLocation(new ClassPathResource("lua/refund_stock.lua"));
        this.refundScript.setResultType(Long.class);
    }

    @Scheduled(fixedDelayString = "${seckill.lost-order-scan-ms:600000}")
    public void reconcileLostOrders() {
        if (!lostOrderEnabled) return;
        if (!acquireLock()) {
            log.debug("其他实例正在执行丢单对账，本次跳过");
            return;
        }
        try {
            doReconcile();
        } finally {
            releaseLock();
        }
    }

    private void doReconcile() {
        int fixed = 0;
        int skipped = 0;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(ORDERED_PATTERN).count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // key 格式: ordered:{activityId}:{userId}
                String[] parts = key.split(":");
                if (parts.length != 3) {
                    skipped++;
                    continue;
                }
                long activityId;
                long userId;
                try {
                    activityId = Long.parseLong(parts[1]);
                    userId = Long.parseLong(parts[2]);
                } catch (NumberFormatException e) {
                    skipped++;
                    continue;
                }

                // DB 是否已有该用户在该活动的订单（幂等：有订单则不是丢单）
                boolean hasOrder = orderRepo.existsByActivityIdAndUserId(activityId, userId);
                if (hasOrder) {
                    skipped++;
                    continue;
                }

                // 无订单 → ordered 标记是"扣了但没落库" → 幂等回补
                String stockKey = "stock:{" + activityId + "}";
                String orderedKey = "ordered:{" + activityId + "}:" + userId;
                Long result = redisTemplate.execute(refundScript, List.of(stockKey, orderedKey));
                if (Long.valueOf(1L).equals(result)) {
                    log.warn("丢单对账回补库存: activityId={}, userId={}", activityId, userId);
                    fixed++;
                } else {
                    skipped++;
                }
            }
        } catch (Exception e) {
            log.warn("丢单对账扫描失败: {}", e.getMessage());
        }
        log.info("丢单对账完成: 补偿 {} 笔, 跳过 {} 笔", fixed, skipped);
    }

    private boolean acquireLock() {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("获取丢单对账分布式锁失败: {}", e.getMessage());
            return false;
        }
    }

    private void releaseLock() {
        try {
            redisTemplate.delete(LOCK_KEY);
        } catch (Exception e) {
            log.warn("释放丢单对账分布式锁失败: {}", e.getMessage());
        }
    }
}
