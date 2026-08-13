package com.jolumn.vtslseckill.biz.service.strategy;

import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.biz.service.StockService;
import com.jolumn.vtslseckill.biz.util.SlidingWindowStats;
import com.jolumn.vtslseckill.model.enums.SeckillMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis lua 原子扣库存 + Kafka get等待结果落库创建订单。
 * 强一致性，但在超高流量时 get 等待会成为瓶颈。
 *
 * <p>自适应降级：复用 {@link SeckillMode} 表达当前实际执行模式——
 * 正常为 REDIS_SYNC（同步等待 broker ack），滑动窗口统计最近 window-size 次
 * 发送的平均耗时与超时率（窗口逻辑在 {@link SlidingWindowStats}），超过阈值
 * 自动降级为 REDIS_ASYNC（异步不等待，用户立即成功）；降级后保留 sample-rate
 * 比例的请求仍走同步采样，连续 recover-threshold 次成功即恢复 REDIS_SYNC
 * （快于固定冷却），采样失败不影响用户（异步兜底重发）。</p>
 *
 * <p>降级期间消息可能丢失/重复：丢失由本地消息表补投 + 对账兜底，
 * 重复由消费端 uk_activity_user 唯一索引幂等兜底（SeckillOrderConsumer）。</p>
 */
@Component
public class RedisSyncStrategy implements SeckillStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedisSyncStrategy.class);
    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final StockService stockService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // ====== 降级配置（yml: seckill.sync-degrade.*，构造注入与 StockService/SeckillOrderConsumer 风格一致） ======
    private final long maxAvgMs;
    private final double maxTimeoutRate;
    private final double sampleRate;            // 降级后作为探针的概率
    private final int recoverThreshold;         // 连续采样成功恢复阈值（默认 5 次）
    private final long minRecoverDelayMs;       // 降级后最短恢复延迟，防抖（默认 10s）

    /** 当前实际执行模式：正常 REDIS_SYNC（同步等待）/ 降级 REDIS_ASYNC（异步不等待） */
    private final AtomicReference<SeckillMode> effectiveMode = new AtomicReference<>(SeckillMode.REDIS_SYNC);
    private final AtomicInteger recoverStreak = new AtomicInteger();   // 连续采样成功次数
    private volatile long degradedAt;                                 // 降级时间戳（最短恢复延迟起点）

    /** 滑动窗口统计：最近 window-size 次发送耗时/超时（线程安全，见 SlidingWindowStats） */
    private final SlidingWindowStats window;

    public RedisSyncStrategy(StockService stockService,
                             KafkaTemplate<String, String> kafkaTemplate,
                             @Value("${seckill.sync-degrade.window-size:100}") int windowSize,
                             @Value("${seckill.sync-degrade.max-avg-ms:500}") long maxAvgMs,
                             @Value("${seckill.sync-degrade.max-timeout-rate:0.05}") double maxTimeoutRate,
                             @Value("${seckill.sync-degrade.sample-rate:0.05}") double sampleRate,
                             @Value("${seckill.sync-degrade.recover-threshold:5}") int recoverThreshold,
                             @Value("${seckill.sync-degrade.min-recover-delay-ms:10000}") long minRecoverDelayMs) {
        this.stockService = stockService;
        this.kafkaTemplate = kafkaTemplate;
        this.maxAvgMs = maxAvgMs;
        this.maxTimeoutRate = maxTimeoutRate;
        this.sampleRate = sampleRate;
        this.recoverThreshold = recoverThreshold;
        this.minRecoverDelayMs = minRecoverDelayMs;
        this.window = new SlidingWindowStats(windowSize);
    }

    @Override
    public int deductStock(Long activityId, Long userId) {
        return stockService.deduct(activityId, userId);
    }

    @Override
    public void createOrder(SeckillActivity activity, Long userId, String orderNo) {
        String msg = userId + ":" + activity.getId() + ":" + orderNo;
        if (effectiveMode.get() == SeckillMode.REDIS_SYNC) {
            if (trySendSync(msg, orderNo)) return;
            // 超时/失败：先判定是否整体降级，再异步兜底重发（用户成功）
            maybeDegrade();
            sendAsync(msg, orderNo);
        } else {
            // 已降级（REDIS_ASYNC）：5% 采样仍走同步以感知恢复，其余异步不等待
            if (shouldSample()) { // 探针
                if (trySendSync(msg, orderNo)) {
                    maybeRecover();
                } else {
                    recoverStreak.set(0);
                    sendAsync(msg, orderNo);   // 采样失败兜底，用户仍成功
                }
            } else {
                sendAsync(msg, orderNo);
            }
        }
    }

    /**
     * 同步发送：等待 broker 确认。成功返回 true；
     * 超时/失败返回 false，并记录到滑动窗口。
     */
    private boolean trySendSync(String msg, String orderNo) {
        long start = System.nanoTime();
        try {
            kafkaTemplate.send("seckill-order", msg).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            window.record((System.nanoTime() - start) / 1_000_000, false);
            log.info("RedisSync 同步发送 MQ 消息并确认: orderNo={}", orderNo);
            return true;
        } catch (TimeoutException e) {
            window.record(SEND_TIMEOUT_SECONDS * 1000, true);
            log.warn("RedisSync 同步发送超时: orderNo={}", orderNo);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            window.record(SEND_TIMEOUT_SECONDS * 1000, true);
            return false;
        } catch (ExecutionException e) {
            window.record(SEND_TIMEOUT_SECONDS * 1000, true);
            log.warn("RedisSync 同步发送失败: orderNo={}, cause={}",
                    orderNo, e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            return false;
        }
    }

    /**
     * 异步发送：fire-and-forget，不等待 broker ack。
     * 仅当 send() 本身失败（如 producer 已关闭，Kafka 彻底不可用）才抛异常，
     * 由 placeOrder catch 后 refund + 503（Fail Fast）。
     */
    private void sendAsync(String msg, String orderNo) {
        try {
            kafkaTemplate.send("seckill-order", msg);
            log.info("RedisSync(降级) 异步发送 MQ 消息: orderNo={}", orderNo);
        } catch (Exception e) {
            throw new RuntimeException("RedisSync 异步发送 MQ 失败: " + orderNo, e);
        }
    }

    /**
     * 降级判定：滑动窗口填满后，平均耗时或超时率超阈值 → 切 REDIS_ASYNC。
     * 窗口只反映最近 N 次，突发故障能即时触发（对比累计统计的迟钝）。
     */
    private void maybeDegrade() {
        if (effectiveMode.get() != SeckillMode.REDIS_SYNC) return;
        if (!window.isFull()) return;                        // 窗口未满不判定
        long avg = window.averageMs();
        double rate = window.timeoutRate();
        if (avg > maxAvgMs || rate > maxTimeoutRate) {
            effectiveMode.set(SeckillMode.REDIS_ASYNC);
            degradedAt = System.currentTimeMillis();
            recoverStreak.set(0);
            log.warn("REDIS_SYNC 降级为 REDIS_ASYNC: avg={}ms, timeoutRate={}%, window={}",
                    avg, String.format("%.2f", rate * 100), window.size());
        }
    }

    /**
     * 采样恢复：降级后连续 recover-threshold 次同步采样成功 → 恢复 REDIS_SYNC。
     * 受 min-recover-delay-ms 防抖约束（避免 Kafka 瞬时抖动导致刚降级又恢复）。
     */
    private void maybeRecover() {
        // 兜底冷却时间
        if (System.currentTimeMillis() - degradedAt < minRecoverDelayMs) return;
        // 探针成功次数,作为恢复为同步等待的依据          阈值↓
        if (recoverStreak.incrementAndGet() >= recoverThreshold) {
            effectiveMode.set(SeckillMode.REDIS_SYNC);
            window.reset();
            recoverStreak.set(0);
            log.info("REDIS_ASYNC 采样连续 {} 次成功，恢复 REDIS_SYNC", recoverThreshold);
        }
    }

    /** 降级期间：按 sample-rate 概率决定本次请求是否走同步采样 */
    private boolean shouldSample() {
        // 5% 的概率作为探针去使用 Kakfa get 同步等待结果
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    @Override
    public void refundStock(Long activityId, Long userId) {
        stockService.refund(activityId, userId);
    }

    /** 供监控/管理接口查看当前执行模式 */
    public SeckillMode currentMode() {
        return effectiveMode.get();
    }
}
