package com.jolumn.vtslseckill.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 可靠性监控：统计 pending / 补偿队列 / 死信队列积压量并打日志。
 * 积压超过阈值即输出 ERROR（接日志平台告警）；SCAN 游标避免阻塞 Redis。
 */
@Component
public class AsyncReliabilityMonitor {

    private static final Logger log = LoggerFactory.getLogger(AsyncReliabilityMonitor.class);
    private static final String PENDING_PATTERN = "pending:*";
    private static final String COMPENSATE_QUEUE = "compensate_queue";
    private static final String DEAD_LETTER_QUEUE = "dead_letter_queue";

    private final StringRedisTemplate redisTemplate;

    @Value("${seckill.async.monitor-warn-pending:100}")
    private int warnPending;
    @Value("${seckill.async.monitor-warn-dead-letter:10}")
    private int warnDeadLetter;

    public AsyncReliabilityMonitor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 每分钟统计一次并打日志；超阈值打 ERROR（触发告警） */
    @Scheduled(fixedDelayString = "${seckill.async.monitor-interval-ms:60000}")
    public void monitor() {
        long pendingCount = 0;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(PENDING_PATTERN).count(500).build())) {
            while (cursor.hasNext()) {
                cursor.next();
                pendingCount++;
            }
        } catch (Exception e) {
            log.warn("pending 计数失败: {}", e.getMessage());
        }

        Long compensateCount = redisTemplate.opsForList().size(COMPENSATE_QUEUE);
        Long deadLetterCount = redisTemplate.opsForList().size(DEAD_LETTER_QUEUE);

        if (pendingCount > warnPending || (deadLetterCount != null && deadLetterCount > warnDeadLetter)) {
            log.error("【异步可靠性告警】pending={}, compensate={}, deadLetter={} (阈值 pending>{}, deadLetter>{})",
                    pendingCount, compensateCount, deadLetterCount, warnPending, warnDeadLetter);
        } else {
            log.info("异步可靠性状态: pending={}, compensate={}, deadLetter={}",
                    pendingCount, compensateCount, deadLetterCount);
        }
    }
}
