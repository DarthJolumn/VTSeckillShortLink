package com.jolumn.vtslcommon.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Snowflake 分布式 ID: 1符号+41时间戳+10 workerId+12序列号
 * JDK 25 JEP 491 已修复 synchronized pinning，ReentrantLock 保留为最佳实践
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1735689600000L; // 2025-01-01
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    private final long workerId;
    private final ReentrantLock lock = new ReentrantLock();
    private long lastTimestamp = -1L; // 上次生成时间戳: 1标记上次 2并发sequence++
    private long sequence = 0L;

    public SnowflakeIdGenerator(@Value("${worker.id:1}") long workerId) {
        this.workerId = workerId;
    }

    public long nextId() {
        lock.lock();
        try {
            long timestamp = System.currentTimeMillis();
            // 时间回退,也就是服务器时间变动
            // 一般不会出现
            if (timestamp < lastTimestamp) {
                long offset = lastTimestamp - timestamp;
                if (offset <= 30) {
                    Thread.sleep(offset + 1);
                    timestamp = System.currentTimeMillis();
                } else {
                    throw new RuntimeException("时钟回拨超过 30ms: " + offset + "ms");
                }
            }
            // 高并发处理
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) timestamp = waitNextMillis(lastTimestamp);
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Snowflake 被中断", e);
        } finally {
            lock.unlock();
        }
    }

    public String nextOrderNo() { return String.valueOf(nextId()); }

    private long waitNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) ts = System.currentTimeMillis();
        return ts;
    }
}
