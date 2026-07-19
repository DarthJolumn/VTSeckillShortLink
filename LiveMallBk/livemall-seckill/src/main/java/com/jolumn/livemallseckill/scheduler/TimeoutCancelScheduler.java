package com.jolumn.livemallseckill.scheduler;

import com.jolumn.livemallseckill.entity.SeckillOrder;
import com.jolumn.livemallseckill.repository.SeckillOrderRepository;
import com.jolumn.livemallseckill.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TimeoutCancelScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutCancelScheduler.class);

    private final SeckillOrderRepository orderRepo;
    private final StockService stockService;

    @Value("${seckill.order-timeout-minutes:15}")
    private int timeoutMinutes;

    public TimeoutCancelScheduler(SeckillOrderRepository orderRepo, StockService stockService) {
        this.orderRepo = orderRepo;
        this.stockService = stockService;
    }

    /** 每15秒扫描超时未支付订单 */
    @Scheduled(fixedDelayString = "${seckill.timeout-scan-ms:15000}")
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<SeckillOrder> timeoutOrders = orderRepo.findByStatusAndCreatedAtBefore(0, deadline);

        for (SeckillOrder order : timeoutOrders) {
            Thread.startVirtualThread(() -> {
                try {
                    int currentVersion = order.getVersion();
                    order.setStatus(2);
                    order.setCancelledAt(LocalDateTime.now());
                    // 乐观锁 CAS：version 匹配才更新
                    SeckillOrder saved = orderRepo.save(order);
                    if (saved.getVersion() == currentVersion + 1) {
                        int shard = (int) (order.getUserId() % 4L);
                        stockService.refund(order.getActivityId(), order.getUserId(), shard);
                        log.info("超时取消订单成功: orderNo={}", order.getOrderNo());
                    }
                } catch (Exception e) {
                    log.warn("超时取消失败（可能已被用户取消）: orderNo={}", order.getOrderNo());
                }
            });
        }
    }
}
