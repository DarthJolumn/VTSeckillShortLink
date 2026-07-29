package com.jolumn.vtslseckill.strategy;

import com.jolumn.vtslseckill.entity.SeckillActivity;
import com.jolumn.vtslseckill.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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

    @Override
    public int deductStock(Long activityId, Long userId) {
        return stockService.deduct(activityId, userId);
    }

    @Override
    public void createOrder(SeckillActivity activity, Long userId, String orderNo) {
        String msg = userId + ":" + activity.getId() + ":" + orderNo;
        kafkaTemplate.send("seckill-order", msg);
        log.info("RedisAsync 发送 Kafka 消息: {}", msg);
    }

    @Override
    public void refundStock(Long activityId, Long userId) {
        stockService.refund(activityId, userId);
    }
}
