package com.jolumn.vtslseckill.biz.util;

/**
 * 环形数组滑动窗口统计：记录最近 N 次采样的耗时与超时标记，
 * 支持查询窗口平均耗时、超时率。线程安全（synchronized 内部状态）。
 *
 * <p>用途：{@code RedisSyncStrategy} 降级判定——只统计最近 N 次 Kafka 发送结果，
 * 避免累计统计对突发故障不敏感（系统健康跑 10000 次后突发 100 次超时，
 * 累计 rate≈1% 检测不到降级；而滑动窗口内全是超时 → 即时触发）。</p>
 *
 * <p>设计：环形数组（head 指向最旧样本），窗口填满后新样本覆盖最旧样本
 * 并 O(1) 扣减其统计，无需搬移元素。</p>
 */
public class SlidingWindowStats {

    private final int capacity;
    private final long[] elapsedMs;
    private final boolean[] timeout;
    private int head;          // 环形写指针（指向最旧样本，覆盖时替换）
    private int filled;        // 已填样本数（0..capacity）
    private long sum;          // 窗口内总耗时(ms)
    private int timeoutCount;  // 窗口内超时/失败次数

    public SlidingWindowStats(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0: " + capacity);
        }
        this.capacity = capacity;
        this.elapsedMs = new long[capacity];
        this.timeout = new boolean[capacity];
    }

    /** 记录一个样本：耗时(ms) + 是否超时/失败 */
    public synchronized void record(long elapsedMs, boolean timeout) {
        if (filled == capacity) {
            // 覆盖最旧样本：先扣减其统计
            sum -= this.elapsedMs[head];
            if (this.timeout[head]) timeoutCount--;
        } else {
            filled++;
        }
        this.elapsedMs[head] = elapsedMs;
        this.timeout[head] = timeout;
        head = (head + 1) % capacity;
        sum += elapsedMs;
        if (timeout) timeoutCount++;
    }

    /** 窗口是否已填满（填满后平均耗时/超时率才有意义） */
    public synchronized boolean isFull() {
        return filled == capacity;
    }

    /** 窗口平均耗时(ms)；窗口为空返回 0 */
    public synchronized long averageMs() {
        return filled == 0 ? 0 : sum / filled;
    }

    /** 窗口超时率(0.0~1.0)；窗口为空返回 0 */
    public synchronized double timeoutRate() {
        return filled == 0 ? 0.0 : (double) timeoutCount / filled;
    }

    /** 窗口内最大耗时(ms)；窗口为空返回 0（诊断用） */
    public synchronized long maxMs() {
        long max = 0;
        for (int i = 0; i < filled; i++) {
            if (elapsedMs[i] > max) max = elapsedMs[i];
        }
        return max;
    }

    /** 已填样本数 */
    public synchronized int size() {
        return filled;
    }

    /** 窗口容量 */
    public int capacity() {
        return capacity;
    }

    /** 清空窗口（恢复后重置统计） */
    public synchronized void reset() {
        head = 0;
        filled = 0;
        sum = 0;
        timeoutCount = 0;
    }
}
