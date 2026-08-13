package com.jolumn.vtslseckill.biz.service.strategy;

import com.jolumn.vtslseckill.model.entity.SeckillActivity;

public interface SeckillStrategy {

    int deductStock(Long activityId, Long userId);

    void createOrder(SeckillActivity activity, Long userId, String orderNo);

    void refundStock(Long activityId, Long userId);
}
