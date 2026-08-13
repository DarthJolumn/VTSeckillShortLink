package com.jolumn.vtslseckill.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 补偿队列消费：回调失败的消息在此重发（比 pending 30s 扫描更快）。
 * RPOP 原子取出 → 重发 → 失败放回队列并累计重试次数，超过上限进死信队列。
 *
 * <p>重要：重发成功或进死信时都必须删除对应 pending 标记，否则
 * PendingOrderScanner 会把该消息重新补投（重复投递，且绕过死信隔离）。</p>
 */
@Component
public class CompensateQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(CompensateQueueConsumer.class);
    private static final String COMPENSATE_QUEUE = "compensate_queue";
    private static final String COMPENSATE_RETRY_KEY = "compensate_retry";   // Hash: msg -> 重试次数
    private static final String DEAD_LETTER_QUEUE = "dead_letter_queue";
    private static final String PENDING_KEY_PREFIX = "pending:";

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${seckill.async.compensate-max-retry:3}")
    private int maxRetry;

    public CompensateQueueConsumer(StringRedisTemplate redisTemplate,
                                   KafkaTemplate<String, String> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${seckill.async.compensate-interval-ms:5000}")
    public void consume() {
        String msg;
        try {
            msg = redisTemplate.opsForList().rightPop(COMPENSATE_QUEUE);
        } catch (Exception e) {
            log.warn("补偿队列 rightPop 失败: {}", e.getMessage());
            return;
        }
        if (msg == null) return;

        try {
            kafkaTemplate.send("seckill-order", msg);
            redisTemplate.opsForHash().delete(COMPENSATE_RETRY_KEY, msg);
            deletePending(msg);   // 重发成功：清 pending，避免 PendingOrderScanner 重复补投
            log.info("补偿队列重发成功: msg={}", msg);
        } catch (Exception e) {
            // 累计重试次数，超上限进死信队列（不再循环重发）
            try {
                long retried = redisTemplate.opsForHash().increment(COMPENSATE_RETRY_KEY, msg, 1);
                if (retried > maxRetry) {
                    redisTemplate.opsForList().leftPush(DEAD_LETTER_QUEUE, msg);
                    redisTemplate.opsForHash().delete(COMPENSATE_RETRY_KEY, msg);
                    deletePending(msg);   // 进死信：清 pending，否则 PendingOrderScanner 绕过死信补投
                    log.error("补偿重发超过 {} 次，进死信队列: msg={}", maxRetry, msg);
                } else {
                    redisTemplate.opsForList().leftPush(COMPENSATE_QUEUE, msg);  // 放回队列
                    log.error("补偿重发失败({}/{}): msg={}, err={}",
                            retried, maxRetry, msg, e.getMessage());
                }
            } catch (Exception ex) {
                log.error("补偿重试计数/死信处理失败: msg={}, err={}", msg, ex.getMessage());
            }
        }
    }

    /**
     * 删除消息对应的 pending 标记。消息格式 userId:activityId:orderNo，
     * pending key 为 pending:{orderNo}。
     */
    private void deletePending(String msg) {
        try {
            String[] parts = msg.split(":", 3);
            if (parts.length == 3) {
                redisTemplate.delete(PENDING_KEY_PREFIX + parts[2]);
            }
        } catch (Exception e) {
            log.warn("补偿路径 pending 删除失败（残留由 TTL 过期清理）: msg={}, err={}", msg, e.getMessage());
        }
    }
}
