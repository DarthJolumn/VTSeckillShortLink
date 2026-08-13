package com.jolumn.vtslseckill.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 异步发送兜底：扫描残留 pending 标记（回调未执行/发送失败/JVM 崩溃遗留），
 * 补投 Kafka。补投可能产生重复消息，由消费端 uk_activity_user 唯一索引幂等吞掉。
 */
@Component
public class PendingOrderScanner {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderScanner.class);
    private static final String PENDING_KEY_PREFIX = "pending:";
    private static final String PENDING_PATTERN = PENDING_KEY_PREFIX + "*";

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 补投任务开关（压测时置 false） */
    @Value("${seckill.async.resend-enabled:true}")
    private boolean resendEnabled;

    /** 每次补投批量上限 */
    @Value("${seckill.async.resend-batch:200}")
    private int resendBatch;

    /** pending 标记 TTL（秒），用于判断"已写入多久" */
    @Value("${seckill.async.pending-ttl-seconds:300}")
    private long pendingTtlSeconds;

    public PendingOrderScanner(StringRedisTemplate redisTemplate,
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 每 30s 扫描 pending:* 残留并补投。
     * 注意：pending TTL 5min，正常回调会删除；残留说明回调未执行（崩溃/失败）。
     * 为避免刚写入的 pending 被误扫，只处理存在超过 30s 的 key。
     *
     * <p>TTL 语义说明：getExpire 返回的是「剩余 TTL」。总 TTL 300s，
     * 剩余 &lt; 270s ⇔ 已写入超过 30s。</p>
     */
    @Scheduled(fixedDelayString = "${seckill.async.resend-interval-ms:30000}")
    public void resendPending() {
        if (!resendEnabled) return;

        // 收集 (key, msg) 对：补投成功后要删除对应 pending，否则每 30s 重复补投
        List<String[]> batch = new ArrayList<>();   // [key, msg]
        // 补投只针对"已写入超过 30s"的 pending。TTL 是剩余时间，剩余 < 总TTL-30 ⇔ 已写入 > 30s。
        // ⚠️ 边界保护：若 pendingTtlSeconds ≤ 30（如测试配 10s），总TTL-30 ≤ 0 会使条件永不成立
        // → 永不补投。此时退化为"剩余 TTL < max(1, ttl-30)"即快过期前补投一次。
        long minAgeSeconds = Math.min(30, Math.max(1, pendingTtlSeconds - 1));
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(PENDING_PATTERN).count(200).build())) {
            while (cursor.hasNext() && batch.size() < resendBatch) {
                String key = cursor.next();
                // 只补投"已存在较久"的 pending，避免刚写入的（正常处理中）被重复投递
                Long ttl = redisTemplate.getExpire(key);
                if (ttl != null && ttl > 0 && ttl < pendingTtlSeconds - minAgeSeconds) {
                    String msg = redisTemplate.opsForValue().get(key);
                    if (msg != null) {
                        batch.add(new String[]{key, msg});
                    }
                }
            }
        } catch (Exception e) {
            log.warn("pending 扫描失败: {}", e.getMessage());
            return;
        }

        for (String[] pair : batch) {
            String key = pair[0];
            String msg = pair[1];
            try {
                kafkaTemplate.send("seckill-order", msg);
                // 补投成功 → 删除 pending，避免重复补投（消费端唯一索引幂等仍是最终兜底）
                redisTemplate.delete(key);
                log.info("pending 补投成功并清理: msg={}", msg);
            } catch (Exception e) {
                log.error("pending 补投失败（保留待下次扫描）: msg={}, err={}", msg, e.getMessage());
            }
        }
    }
}
