package com.jolumn.vtslseckill.biz.service.strategy;

import com.jolumn.vtslseckill.biz.mq.KafkaOrderSender;
import com.jolumn.vtslseckill.biz.service.StockService;
import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.model.enums.SendOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Redis lua 原子扣库存 + Kafka 异步落库（REDIS_ASYNC，最终一致性）。
 * 可靠性由四层保障（发送逻辑在 {@link KafkaOrderSender#sendAsyncReliable}）：
 * ① 发送前写 Redis pending 标记（防进程崩溃丢失）
 * ② send() 立即返回，不阻塞用户
 * ③ 回调成功删 pending、失败进补偿队列
 * ④ PendingOrderScanner 定时扫描残留 pending 补投
 * ⑤ 消费端 uk_activity_user 唯一索引幂等 + ReconciliationScheduler 对账兜底
 */
@Component
public class RedisAsyncStrategy implements SeckillStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedisAsyncStrategy.class);

    private final StockService stockService;
    private final KafkaOrderSender sender;

    public RedisAsyncStrategy(StockService stockService,
                              KafkaOrderSender sender) {
        this.stockService = stockService;
        this.sender = sender;
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
     * 委托 {@link KafkaOrderSender#sendAsyncReliable} 走可靠异步通道。
     */
    @Override
    public SendOutcome createOrder(SeckillActivity activity, Long userId, String orderNo) {
        String msg = userId + ":" + activity.getId() + ":" + orderNo;
        return sender.sendAsyncReliable(msg, orderNo);
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
