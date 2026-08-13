package com.jolumn.vtslseckill.biz.service.strategy;

import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.biz.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Redis lua 原子扣库存 + Kafka 异步落库（fire-and-forget）。
 * 牺牲强一致性换取极致性能，可靠性由四层保障：
 * ① 发送前写 Redis pending 标记（防进程崩溃丢失）
 * ② send() 立即返回，不阻塞用户
 * ③ 回调成功删 pending、失败进补偿队列
 * ④ PendingOrderScanner 定时扫描残留 pending 补投
 * ⑤ 消费端 uk_activity_user 唯一索引幂等 + ReconciliationScheduler 对账兜底
 */
@Component
public class RedisAsyncStrategy implements SeckillStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedisAsyncStrategy.class);
    private static final String PENDING_KEY_PREFIX = "pending:";
    private static final String COMPENSATE_QUEUE = "compensate_queue";

    private final StockService stockService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${seckill.async.pending-ttl-seconds:300}")
    private long pendingTtlSeconds;

    public RedisAsyncStrategy(StockService stockService,
                              KafkaTemplate<String, String> kafkaTemplate,
                              StringRedisTemplate redisTemplate) {
        this.stockService = stockService;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 原子扣减库存
     * @param activityId
     * @param userId
     * @return
     */
    @Override
    public int deductStock(Long activityId, Long userId) {
        return stockService.deduct(activityId, userId);
    }

    /**
     * Kafka 异步不等待 落库创建订单（不阻塞用户请求线程）。
     *
     * <p>可靠性：先写 Redis pending 标记，send() 立即返回；
     * 回调成功删 pending、失败进补偿队列；进程崩溃时回调不执行，
     * pending 残留由 PendingOrderScanner 定时补投。</p>
     *
     * @param activity
     * @param userId
     * @param orderNo
     */
    @Override
    public void createOrder(SeckillActivity activity, Long userId, String orderNo) {
        String msg = userId + ":" + activity.getId() + ":" + orderNo;

        // ① 先落 pending 标记（防丢）；Redis 异常时降级放行（保可用性，可靠性交给对账）
        String pendingKey = PENDING_KEY_PREFIX + orderNo;
        try {
            redisTemplate.opsForValue().set(pendingKey, msg, Duration.ofSeconds(pendingTtlSeconds));
        } catch (Exception e) {
            log.warn("pending 标记写入失败（降级放行）: orderNo={}, err={}", orderNo, e.getMessage());
        }

        // ② 异步发送 + ③ 回调（不阻塞用户请求线程，回调由 Kafka I/O 线程触发）
        try {
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send("seckill-order", msg);
            future.whenComplete((result, failure) -> {
                if (failure == null) {
                    try {
                        redisTemplate.delete(pendingKey);
                    } catch (Exception e) {
                        log.warn("pending 删除失败（残留由定时补投扫描，幂等兜底）: orderNo={}", orderNo, e.getMessage());
                    }
                    log.info("RedisAsync 异步发送成功: orderNo={}", orderNo);
                } else {
                    // 保留 pending（TTL 内定时任务会补投），同时进补偿队列加速重发
                    try {
                        redisTemplate.opsForList().leftPush(COMPENSATE_QUEUE, msg);
                    } catch (Exception e) {
                        log.warn("补偿队列写入失败: orderNo={}, err={}", orderNo, e.getMessage());
                    }
                    log.error("RedisAsync 异步发送失败（已进补偿队列）: orderNo={}, err={}",
                            orderNo, failure.getMessage());
                }
            });
        } catch (Exception e) {
            // send() 本身抛异常（producer 关闭等）：pending 保留，由定时任务补投
            log.error("RedisAsync send() 调用失败（pending 保留待补投）: orderNo={}, err={}",
                    orderNo, e.getMessage());
        }
        // 用户立即返回（~1ms），不等待发送结果
    }

    /**
     * 库存回补
     * @param activityId
     * @param userId
     */
    @Override
    public void refundStock(Long activityId, Long userId) {
        stockService.refund(activityId, userId);
    }
}
