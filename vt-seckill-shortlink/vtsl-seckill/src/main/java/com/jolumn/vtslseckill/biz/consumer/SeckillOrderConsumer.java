package com.jolumn.vtslseckill.biz.consumer;

import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.biz.service.ActivityCacheService;
import com.jolumn.vtslseckill.biz.service.SeckillService;
import com.jolumn.vtslseckill.biz.service.StockService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);
    private static final int MAX_RETRIES = 3;
    private static final String WS_PUSH_TOPIC = "ws:push:seckill-result";
    private final ConcurrentHashMap<String, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    private final Semaphore SEM;

    private final SeckillService seckillService;
    private final ActivityCacheService cacheService;
    private final StockService stockService;
    private final StringRedisTemplate redisTemplate;

    public SeckillOrderConsumer(SeckillService seckillService,
                                ActivityCacheService cacheService,
                                StockService stockService,
                                StringRedisTemplate redisTemplate,
                                @Value("${seckill.consumer-max-concurrency:30}") int maxConcurrency) {
        this.seckillService = seckillService;
        this.cacheService = cacheService;
        this.stockService = stockService;
        this.redisTemplate = redisTemplate;
        this.SEM = new Semaphore(maxConcurrency);
    }

    @KafkaListener(topics = "seckill-order", groupId = "vtsl-seckill")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            SEM.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                String[] parts = record.value().split(":", 3);
                if (parts.length < 3) {
                    log.error("Kafka 消息格式错误: {}", record.value());
                    ack.acknowledge();
                    return;
                }
                Long userId = Long.parseLong(parts[0]);
                Long activityId = Long.parseLong(parts[1]);
                String orderNo = parts[2];

                SeckillActivity activity = cacheService.getActivity(activityId);
                if (activity == null) {
                    // 活动缺失（被删/缓存失效）：Redis 库存已扣但订单无法落库 → 幂等回补库存
                    // refund Lua 仅在 ordered:{activityId}:{userId} 存在时才 INCR，恶意/重复消息不会污染库存
                    try {
                        stockService.refund(activityId, userId);
                        log.error("活动不存在，已幂等回补库存: activityId={}, userId={}", activityId, userId);
                    } catch (Exception refundEx) {
                        log.error("活动不存在且回补库存失败: activityId={}, userId={}", activityId, userId, refundEx);
                    }
                    ack.acknowledge();
                    return;
                }

                seckillService.createOrder(activity, userId, orderNo);

                try {
                    String payload = String.format(
                            "{\"userId\":%d,\"orderNo\":\"%s\",\"ok\":true,\"message\":\"%s\",\"timestamp\":%d}",
                            userId, orderNo, "抢购成功", System.currentTimeMillis());
                    redisTemplate.convertAndSend(WS_PUSH_TOPIC, payload);
                } catch (Exception e) {
                    log.warn("Redis Pub/Sub 推送异常: userId={}, error={}", userId, e.getMessage());
                }

                ack.acknowledge();
            } catch (DuplicateKeyException e) {
                log.warn("重复订单（幂等兜底）: orderNo={}", e.getMessage());
                ack.acknowledge();
            } catch (TransactionException e) {
                String key = record.value();
                AtomicInteger count = retryCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
                int retried = count.incrementAndGet();
                if (retried >= MAX_RETRIES) {
                    log.error("DB/事务异常已达最大重试({}), 放弃并 ack: {}", MAX_RETRIES, key);
                    ack.acknowledge();
                    retryCounts.remove(key);
                } else {
                    log.error("DB/事务异常({}/{}), 不 ack 等待重投: {}", retried, MAX_RETRIES, key);
                }
            } catch (Exception e) {
                log.error("订单消费异常", e);
                ack.acknowledge();
            } finally {
                SEM.release();
            }
        });
    }
}
