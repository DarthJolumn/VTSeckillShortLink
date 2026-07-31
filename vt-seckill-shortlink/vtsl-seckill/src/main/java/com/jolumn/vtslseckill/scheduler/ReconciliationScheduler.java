package com.jolumn.vtslseckill.scheduler;

import com.jolumn.vtslseckill.entity.SeckillActivity;
import com.jolumn.vtslseckill.entity.SeckillOrder;
import com.jolumn.vtslseckill.entity.enums.SeckillMode;
import com.jolumn.vtslseckill.repository.SeckillActivityRepository;
import com.jolumn.vtslseckill.repository.SeckillOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);
    private static final int CANCELLED_STATUS = 2;
    private static final String LOCK_KEY = "lock:reconciliation:cancelled";
    private static final long LOCK_TTL_SECONDS = 300;

    private final SeckillOrderRepository orderRepo;
    private final SeckillActivityRepository activityRepo;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> refundScript;

    @Value("${seckill.reconciliation-enabled:true}")
    private boolean reconciliationEnabled;

    @Value("${seckill.reconciliation-window-minutes:60}")
    private int reconciliationWindowMinutes;

    @Value("${seckill.reconciliation-scan-ms:300000}")
    private long scanIntervalMs;

    @Value("${seckill.reconciliation-batch-size:500}")
    private int batchSize;

    public ReconciliationScheduler(SeckillOrderRepository orderRepo,
                                   SeckillActivityRepository activityRepo,
                                   StringRedisTemplate redisTemplate) {
        this.orderRepo = orderRepo;
        this.activityRepo = activityRepo;
        this.redisTemplate = redisTemplate;
        this.refundScript = new DefaultRedisScript<>();
        this.refundScript.setLocation(new ClassPathResource("lua/refund_stock.lua"));
        this.refundScript.setResultType(Long.class);
    }

    @Scheduled(fixedDelayString = "${seckill.reconciliation-scan-ms:300000}")
    public void reconcileCancelledOrders() {
        if (!reconciliationEnabled) return;

        if (!acquireLock()) {
            log.debug("其他实例正在执行对账，本次跳过");
            return;
        }

        try {
            doReconcile();
        } finally {
            releaseLock();
        }
    }

    private void doReconcile() {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(reconciliationWindowMinutes);
        Pageable pageable = PageRequest.of(0, batchSize);
        List<SeckillOrder> cancelledOrders = orderRepo.findByStatusAndCancelledAtAfter(CANCELLED_STATUS, windowStart, pageable);
        if (cancelledOrders.isEmpty()) return;

        Set<Long> activityIds = cancelledOrders.stream()
                .map(SeckillOrder::getActivityId)
                .collect(Collectors.toSet());
        Map<Long, SeckillActivity> activityMap = activityRepo.findAllById(activityIds).stream()
                .collect(Collectors.toMap(SeckillActivity::getId, a -> a));

        int fixed = 0;
        int skipped = 0;

        for (SeckillOrder order : cancelledOrders) {
            SeckillActivity activity = activityMap.get(order.getActivityId());
            if (activity == null || activity.getMode() == SeckillMode.DB_QUEUE) {
                skipped++;
                continue;
            }

            String stockKey = "stock:{" + order.getActivityId() + "}";
            String orderedKey = "ordered:{" + order.getActivityId() + "}:" + order.getUserId();

            Long result = redisTemplate.execute(refundScript, List.of(stockKey, orderedKey));
            if (Long.valueOf(1L).equals(result)) {
                log.info("对账补偿回补库存: orderNo={}, activityId={}, userId={}",
                        order.getOrderNo(), order.getActivityId(), order.getUserId());
                fixed++;
            } else {
                skipped++;
            }
        }

        log.info("对账补偿完成: 检查 {} 笔, 补偿 {} 笔, 跳过 {} 笔",
                cancelledOrders.size(), fixed, skipped);
    }

    private boolean acquireLock() {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("获取对账分布式锁失败: {}", e.getMessage());
            return false;
        }
    }

    private void releaseLock() {
        try {
            redisTemplate.delete(LOCK_KEY);
        } catch (Exception e) {
            log.warn("释放对账分布式锁失败: {}", e.getMessage());
        }
    }
}