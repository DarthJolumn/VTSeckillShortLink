package com.jolumn.vtslseckill.biz.mq;

import com.jolumn.vtslseckill.biz.util.SlidingWindowStats;
import com.jolumn.vtslseckill.model.enums.SeckillMode;
import com.jolumn.vtslseckill.model.enums.SendOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REDIS_SYNC 自适应降级决策器（与发送动作解耦，独立可测）。
 *
 * <p>职责：
 * <ul>
 *   <li>滑动窗口统计最近 window-size 次发送耗时/超时率（{@link SlidingWindowStats}）</li>
 *   <li>窗口填满且平均耗时 &gt; max-avg-ms 或超时率 &gt; max-timeout-rate → 切 REDIS_ASYNC</li>
 *   <li>降级后按 sample-rate 探针（同步采样），连续 recover-threshold 次成功 → 恢复 REDIS_SYNC</li>
 *   <li>min-recover-delay-ms 防抖（避免 Kafka 抖动反复横跳）</li>
 * </ul>
 *
 * <p>注意：状态为实例本地（多实例各自独立）。前提是"多实例连同一 Kafka 会同时观测到
 * 故障并同时降级"，该前提成立时实例间天然一致，无需共享状态。</p>
 */
@Component
public class SyncDegradeController {

    private static final Logger log = LoggerFactory.getLogger(SyncDegradeController.class);

    /** 当前实际执行模式：正常 REDIS_SYNC（同步等待）/ 降级 REDIS_ASYNC（异步受理） */
    private final AtomicReference<SeckillMode> mode = new AtomicReference<>(SeckillMode.REDIS_SYNC);
    private final AtomicInteger recoverStreak = new AtomicInteger();   // 连续探针成功次数
    private volatile long degradedAt;                                 // 降级时间戳（防抖起点）

    private final SlidingWindowStats window;
    private final long maxAvgMs;
    private final double maxTimeoutRate;
    private final double sampleRate;
    private final int recoverThreshold;
    private final long minRecoverDelayMs;
    private final long degradeTtlMs;

    public SyncDegradeController(@Value("${seckill.sync-degrade.window-size:100}") int windowSize,
                                 @Value("${seckill.sync-degrade.max-avg-ms:500}") long maxAvgMs,
                                 @Value("${seckill.sync-degrade.max-timeout-rate:0.05}") double maxTimeoutRate,
                                 @Value("${seckill.sync-degrade.sample-rate:0.05}") double sampleRate,
                                 @Value("${seckill.sync-degrade.recover-threshold:5}") int recoverThreshold,
                                 @Value("${seckill.sync-degrade.min-recover-delay-ms:10000}") long minRecoverDelayMs,
                                 @Value("${seckill.sync-degrade.degrade-ttl-ms:60000}") long degradeTtlMs) {
        this.window = new SlidingWindowStats(windowSize);
        this.maxAvgMs = maxAvgMs;
        this.maxTimeoutRate = maxTimeoutRate;
        this.sampleRate = sampleRate;
        this.recoverThreshold = recoverThreshold;
        this.minRecoverDelayMs = minRecoverDelayMs;
        this.degradeTtlMs = degradeTtlMs;
    }

    /** 是否处于同步模式（正常态） */
    public boolean isSyncMode() {
        return mode.get() == SeckillMode.REDIS_SYNC;
    }

    /** 当前执行模式 */
    public SeckillMode currentMode() {
        return mode.get();
    }

    /**
     * 同步模式下记录一次发送结果（窗口统计 + 降级判定）。
     * 仅同步模式调用；降级模式下探针结果走 onSampleSuccess/onSampleFailure。
     *
     * @param outcome   发送结果（CONFIRMED=成功，其余=失败）
     * @param elapsedMs 本次发送实际耗时(ms)——降级判定的核心输入，成功也要记真实耗时
     */
    public void record(SendOutcome outcome, long elapsedMs) {
        boolean success = outcome == SendOutcome.CONFIRMED;
        window.record(elapsedMs, !success);
        maybeDegrade();
    }

    /** 降级模式下：按 sample-rate 决定本次是否走同步探针 */
    public boolean shouldSample() {
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    /** 探针成功：连续 recover-threshold 次 → 恢复 REDIS_SYNC（受防抖约束） */
    public void onSampleSuccess() {
        if (System.currentTimeMillis() - degradedAt < minRecoverDelayMs) return;
        if (recoverStreak.incrementAndGet() >= recoverThreshold) {
            mode.set(SeckillMode.REDIS_SYNC);
            window.reset();
            recoverStreak.set(0);
            log.info("【恢复】REDIS_ASYNC 探针连续 {} 次成功，恢复 REDIS_SYNC", recoverThreshold);
        }
    }

    /** 探针失败：清空恢复计数（防止 Kafka 抖动导致伪恢复） */
    public void onSampleFailure() {
        recoverStreak.set(0);
    }

    /**
     * 诊断轮询：每 2s 打印滑动窗口内部状态（压测观察用）。
     * 输出 mode / 窗口样本数 / 平均耗时 / 超时率 / 窗口内最大耗时。
     */
    @Scheduled(fixedDelayString = "${seckill.sync-degrade.print-interval-ms:2000}")
    public void printWindowStats() {
        if (window.size() == 0) return;   // 无样本不刷屏
        log.info("【窗口】mode={}, filled={}/{}, avg={}ms, rate={}%, max={}ms",
                mode.get(), window.size(), window.capacity(),
                window.averageMs(), String.format("%.1f", window.timeoutRate() * 100),
                window.maxMs());
    }

    /**
     * 降级 TTL 定时恢复（关键设计，组合方案 ①+②）：
     * 有流量时靠 5% 采样快速恢复（秒级）；无流量时 TTL 到期强制尝试恢复（兜底），
     * 不依赖用户请求、不产生探针消息。
     * 恢复后若 Kafka 仍未恢复，下一个请求 sendSync 失败 → 窗口重新积累 → 再次降级，形成闭环。
     */
    @Scheduled(fixedDelayString = "${seckill.sync-degrade.ttl-check-ms:5000}")
    public void ttlRecover() {
        if (isSyncMode()) return;
        if (System.currentTimeMillis() - degradedAt < degradeTtlMs) return;  // TTL 未到期
        mode.set(SeckillMode.REDIS_SYNC);
        window.reset();
        recoverStreak.set(0);
        log.warn("【恢复】降级 TTL {}ms 到期，尝试恢复 REDIS_SYNC（若 Kafka 未恢复，下个请求失败将重新降级）",
                degradeTtlMs);
    }

    private void maybeDegrade() {
        if (!isSyncMode()) return;
        if (!window.isFull()) return;                       // 窗口未满不判定
        long avg = window.averageMs();
        double rate = window.timeoutRate();
        if (avg > maxAvgMs || rate > maxTimeoutRate) {
            mode.set(SeckillMode.REDIS_ASYNC);
            degradedAt = System.currentTimeMillis();
            recoverStreak.set(0);
            log.warn("【降级】REDIS_SYNC 降级为 REDIS_ASYNC: avg={}ms, timeoutRate={}%, window={}",
                    avg, String.format("%.2f", rate * 100), window.size());
        }
    }
}
