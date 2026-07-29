package com.jolumn.vtslseckill.strategy;

import com.jolumn.vtslseckill.entity.SeckillActivity;
import com.jolumn.vtslseckill.entity.SeckillOrder;
import com.jolumn.vtslseckill.repository.SeckillOrderRepository;
import com.jolumn.vtslseckill.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RedisSyncStrategy implements SeckillStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedisSyncStrategy.class);

    private final StockService stockService;
    private final SeckillOrderRepository orderRepo;

    public RedisSyncStrategy(StockService stockService,
                             SeckillOrderRepository orderRepo) {
        this.stockService = stockService;
        this.orderRepo = orderRepo;
    }

    @Override
    public int deductStock(Long activityId, Long userId) {
        return stockService.deduct(activityId, userId);
    }

    @Transactional
    @Override
    public void createOrder(SeckillActivity activity, Long userId, String orderNo) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(orderNo);
        order.setActivityId(activity.getId());
        order.setUserId(userId);
        order.setProductId(activity.getProductId());
        order.setSeckillPrice(activity.getSeckillPrice());
        order.setStatus(0);
        orderRepo.save(order);
        log.info("RedisSync 同步落库: orderNo={}", orderNo);
    }

    @Override
    public void refundStock(Long activityId, Long userId) {
        stockService.refund(activityId, userId);
    }
}
