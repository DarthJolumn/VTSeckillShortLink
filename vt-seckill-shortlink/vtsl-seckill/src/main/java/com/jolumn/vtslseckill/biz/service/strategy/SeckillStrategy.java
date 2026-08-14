package com.jolumn.vtslseckill.biz.service.strategy;

import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.model.enums.SendOutcome;

public interface SeckillStrategy {

    int deductStock(Long activityId, Long userId);

    /**
     * 扣减成功后的订单消息发送。
     *
     * @return 发送结果语义（CONFIRMED=同步确认送达 / ACCEPTED=异步受理 /
     *         TRANSIENT/FATAL=失败——上层据此统一回补库存）
     */
    SendOutcome createOrder(SeckillActivity activity, Long userId, String orderNo);

    void refundStock(Long activityId, Long userId);
}
