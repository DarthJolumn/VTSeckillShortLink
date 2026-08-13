package com.jolumn.vtslseckill.biz.service.strategy;

import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.biz.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis lua 原子扣库存 + Kafka 不等结果异步落库。
 * 牺牲强一致性换取极致性能
 */
@Component
public class RedisAsyncStrategy implements SeckillStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedisAsyncStrategy.class);

    private final StockService stockService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public RedisAsyncStrategy(StockService stockService,
                              KafkaTemplate<String, String> kafkaTemplate) {
        this.stockService = stockService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 原子扣减库存
     * @param activityId
     * @param userId
     * @return
     */
    public int deductStock(Long activityId, Long userId) {
        return stockService.deduct(activityId, userId);
    }

    /**
     * Kafka 异步不等待 落库创建订单
     * @param activity
     * @param userId
     * @param orderNo
     */
    public void createOrder(SeckillActivity activity, Long userId, String orderNo) {
        String msg = userId + ":" + activity.getId() + ":" + orderNo;
        kafkaTemplate.send("seckill-order", msg);
        log.info("RedisAsync 发送 Kafka 消息: {}", msg);
    }

    /**
     * 库存回补
     * @param activityId
     * @param userId
     */
    public void refundStock(Long activityId, Long userId) {
        stockService.refund(activityId, userId);
    }
}
