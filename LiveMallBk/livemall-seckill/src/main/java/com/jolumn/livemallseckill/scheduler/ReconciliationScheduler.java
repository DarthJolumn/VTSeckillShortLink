package com.jolumn.livemallseckill.scheduler;

import com.jolumn.livemallseckill.entity.SeckillOrder;
import com.jolumn.livemallseckill.repository.SeckillOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);
    private static final int CANCELLED_STATUS = 2;

    private final SeckillOrderRepository orderRepo;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> refundScript;

    @Value("${seckill.reconciliation-enabled:true}")
    private boolean reconciliationEnabled;

    @Value("${seckill.reconciliation-window-minutes:60}")
    private int reconciliationWindowMinutes;

    public ReconciliationScheduler(SeckillOrderRepository orderRepo, StringRedisTemplate redisTemplate) {
        this.orderRepo = orderRepo;
        this.redisTemplate = redisTemplate;
        this.refundScript = new DefaultRedisScript<>();
        this.refundScript.setLocation(new ClassPathResource("lua/refund_stock.lua"));
        this.refundScript.setResultType(Long.class);
    }

    @Scheduled(fixedDelayString = "${seckill.reconciliation-scan-ms:300000}")
    public void reconcileCancelledOrders() {
        if (!reconciliationEnabled) return;
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(reconciliationWindowMinutes);
        List<SeckillOrder> cancelledOrders = orderRepo.findByStatusAndCancelledAtAfter(CANCELLED_STATUS, windowStart);
        int fixed = 0;

        for (SeckillOrder order : cancelledOrders) {
            String orderedKey = "ordered:{" + order.getActivityId() + "}:" + order.getUserId();
            Boolean exists = redisTemplate.hasKey(orderedKey);
            if (Boolean.TRUE.equals(exists)) {
                try {
                    String stockKey = "stock:{" + order.getActivityId() + "}";
                    redisTemplate.execute(refundScript, List.of(stockKey, orderedKey));
                    log.info("对账补偿回补库存: orderNo={}, activityId={}, userId={}",
                            order.getOrderNo(), order.getActivityId(), order.getUserId());
                    fixed++;
                } catch (Exception e) {
                    log.error("对账补偿失败: orderNo={}", order.getOrderNo(), e);
                }
            }
        }
        if (fixed > 0) {
            log.info("对账补偿完成: 检查 {} 笔, 补偿 {} 笔", cancelledOrders.size(), fixed);
        }
    }
}
