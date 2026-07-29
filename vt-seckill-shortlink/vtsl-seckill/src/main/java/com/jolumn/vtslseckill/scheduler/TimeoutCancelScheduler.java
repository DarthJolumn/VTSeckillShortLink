package com.jolumn.vtslseckill.scheduler;

import com.jolumn.vtslseckill.entity.SeckillActivity;
import com.jolumn.vtslseckill.entity.SeckillOrder;
import com.jolumn.vtslseckill.repository.SeckillActivityRepository;
import com.jolumn.vtslseckill.repository.SeckillOrderRepository;
import com.jolumn.vtslseckill.strategy.SeckillStrategy;
import com.jolumn.vtslseckill.strategy.SeckillStrategyFactory;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TimeoutCancelScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutCancelScheduler.class);

    private final SeckillOrderRepository orderRepo;
    private final SeckillActivityRepository activityRepo;
    private final SeckillStrategyFactory strategyFactory;

    @Value("${seckill.order-timeout-minutes:15}")
    private int timeoutMinutes;

    @Value("${seckill.timeout-cancel-enabled:true}")
    private boolean timeoutCancelEnabled;

    @Value("${seckill.timeout-scan-batch:500}")
    private int batchSize;

    public TimeoutCancelScheduler(SeckillOrderRepository orderRepo,
                                  SeckillActivityRepository activityRepo,
                                  SeckillStrategyFactory strategyFactory) {
        this.orderRepo = orderRepo;
        this.activityRepo = activityRepo;
        this.strategyFactory = strategyFactory;
    }

    @Scheduled(fixedDelayString = "${seckill.timeout-scan-ms:15000}")
    public void cancelTimeoutOrders() {
        if (!timeoutCancelEnabled) return;
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<SeckillOrder> timeoutOrders = orderRepo.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                0, deadline, PageRequest.of(0, batchSize));

        for (SeckillOrder order : timeoutOrders) {
            Thread.startVirtualThread(() -> {
                try {
                    order.setStatus(2);
                    order.setCancelledAt(LocalDateTime.now());
                    orderRepo.save(order);

                    SeckillActivity activity = activityRepo.findById(order.getActivityId()).orElse(null);
                    if (activity != null) {
                        SeckillStrategy strategy = strategyFactory.getStrategy(activity.getMode());
                        strategy.refundStock(order.getActivityId(), order.getUserId());
                    }
                    log.info("超时取消订单成功: orderNo={}", order.getOrderNo());
                } catch (OptimisticLockException e) {
                    log.info("订单已被用户主动取消: orderNo={}", order.getOrderNo());
                } catch (Exception e) {
                    log.error("超时取消失败: orderNo={}", order.getOrderNo(), e);
                }
            });
        }
    }
}
