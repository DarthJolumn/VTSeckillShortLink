package com.jolumn.vtslseckill.strategy;

import com.jolumn.vtslseckill.entity.SeckillActivity;
import com.jolumn.vtslseckill.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * REDIS_SYNC：Redis Lua 原子扣减 + Kafka 同步发送（send().get() 等 broker 确认）。
 *
 * <p>与 REDIS_ASYNC 的唯一区别：发消息后同步等待 broker ack，消息不丢才返回成功；
 * 落库仍由 SeckillOrderConsumer 消费端完成（手动 ACK + 唯一索引幂等 + 对账兜底）。
 */
@Component
public class RedisSyncStrategy implements SeckillStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedisSyncStrategy.class);
    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final StockService stockService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public RedisSyncStrategy(StockService stockService,
                             KafkaTemplate<String, String> kafkaTemplate) {
        this.stockService = stockService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public int deductStock(Long activityId, Long userId) {
        return stockService.deduct(activityId, userId);
    }

    @Override
    public void createOrder(SeckillActivity activity, Long userId, String orderNo) {
        String msg = userId + ":" + activity.getId() + ":" + orderNo;
        try {
            // 同步等待 broker 确认：消息可靠送达才返回成功（区别于 REDIS_ASYNC 的 fire-and-forget）
            kafkaTemplate.send("seckill-order", msg).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("RedisSync 同步发送 MQ 消息并确认: orderNo={}", orderNo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RedisSync 发送 MQ 中断: " + orderNo, e);
        } catch (ExecutionException | TimeoutException e) {
            // 超时/失败 → placeOrder catch 后 refundStock 回补 Redis 库存
            throw new RuntimeException("RedisSync 发送 MQ 失败或超时: " + orderNo, e);
        }
    }

    @Override
    public void refundStock(Long activityId, Long userId) {
        stockService.refund(activityId, userId);
    }
}
