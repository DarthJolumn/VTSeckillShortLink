package com.jolumn.vtslseckill.biz.service.strategy;

import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.biz.repository.SeckillActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DBQueueStrategy implements SeckillStrategy {

    private static final Logger log = LoggerFactory.getLogger(DBQueueStrategy.class);

    private final SeckillActivityRepository activityRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DBQueueStrategy(SeckillActivityRepository activityRepo,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.activityRepo = activityRepo;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    @Override
    public int deductStock(Long activityId, Long userId) {
        SeckillActivity activity = activityRepo.findById(activityId).orElse(null);
        if (activity == null) return -3;
        int updated = activityRepo.decrementStockIfAvailable(activityId, activity.getVersion());
        if (updated > 0) {
            return 200;
        }
        return -2;
    }

    @Override
    public void createOrder(SeckillActivity activity, Long userId, String orderNo) {
        String msg = userId + ":" + activity.getId() + ":" + orderNo;
        kafkaTemplate.send("seckill-order", activity.getId().toString(), msg);
        log.info("DBQueue 发送顺序 MQ 消息: key={}, msg={}", activity.getId(), msg);
    }

    @Transactional
    @Override
    public void refundStock(Long activityId, Long userId) {
        activityRepo.incrementStockIfAvailable(activityId);
        log.info("DBQueue 回补库存: activityId={}", activityId);
    }
}
