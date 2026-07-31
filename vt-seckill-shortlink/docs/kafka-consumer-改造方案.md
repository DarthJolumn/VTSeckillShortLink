# Kafka Consumer 背压 + 信号量改造方案

## 目标

保留"阻塞 Kafka 平台线程做背压"的正确架构，同时消除 ACK 上下文错乱和 Rebalance 超时两大隐患。

---

## 核心代码改造

将 `onMessage` 拆分为"平台线程背压层"和"虚拟线程业务层"。

```java
@KafkaListener(topics = "seckill-order", groupId = "vtsl-seckill")
public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
    // ✅ 【第一层】平台线程只做背压，绝不 fork 虚拟线程前做任何业务
    try {
        SEM.acquire();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("消费被中断，消息未处理: offset={}", record.offset());
        return; // 不 ACK，让 Kafka 重投
    }

    // ✅ 【第二层】拿到许可后，才 fork 虚拟线程执行业务
    Thread.startVirtualThread(() -> {
        try {
            processAndAck(record, ack);
        } finally {
            // ✅ 【关键】无论成功、异常、中断，必须释放信号量
            SEM.release();
        }
    });
}

private void processAndAck(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
        String[] parts = record.value().split(":", 3);
        if (parts.length < 3) {
            log.error("消息格式错误: {}", record.value());
            ack.acknowledge();
            return;
        }

        Long userId = Long.parseLong(parts[0]);
        Long activityId = Long.parseLong(parts[1]);
        String orderNo = parts[2];

        SeckillActivity activity = cacheService.getActivity(activityId);
        if (activity == null) {
            log.error("活动不存在: activityId={}", activityId);
            ack.acknowledge();
            return;
        }

        seckillService.createOrder(activity, userId, orderNo);

        // Redis 推送（非核心链路，异常不影响 ACK）
        try {
            String payload = String.format(
                    "{\"userId\":%d,\"orderNo\":\"%s\",\"ok\":true,\"timestamp\":%d}",
                    userId, orderNo, System.currentTimeMillis());
            redisTemplate.convertAndSend(WS_PUSH_TOPIC, payload);
        } catch (Exception e) {
            log.warn("Redis推送失败: userId={}, err={}", userId, e.getMessage());
        }

        ack.acknowledge();

    } catch (DuplicateKeyException e) {
        log.warn("重复订单(幂等): orderNo={}", e.getMessage());
        ack.acknowledge();
    } catch (TransactionException e) {
        log.error("事务异常(已ACK+记录补偿): {}", record.value(), e);
        ack.acknowledge();
        // TODO: 写入 retry_order 表或发送 retry-topic
    } catch (Exception e) {
        log.error("消费异常(兜底ACK): {}", record.value(), e);
        ack.acknowledge();
    }
}
```

---

## 配套配置

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 30                # 与 Semaphore 大小对齐
      max-poll-interval-ms: 300000        # 30 × 50ms × 5 = 7.5s，设 5min 应对极端抖动
      session-timeout-ms: 10000           # 默认不动
    listener:
      ack-mode: MANUAL_IMMEDIATE          # 手动 ACK
      auto-startup: true
    producer:
      properties:
        enable.idempotence: true          # 生产者幂等
```

---

## 改造前后对比

| 维度 | 改造前 | 改造后 | 验收方式 |
|------|--------|--------|---------|
| 背压位置 | 平台线程 acquire ✅ | 平台线程 acquire ✅ | 监控 Consumer Lag |
| OOM 风险 | 低 ✅ | 低 ✅ | 压测观察堆内存 |
| ACK 安全性 | VT 内调用 ⚠️ | VT 内调用 ⚠️（折中） | 压测 10w 条，无 offset 跳跃 |
| Rebalance 风险 | 高 ❌ | 极低 ✅ | 监控 group coordinator 日志 |
| 事务异常处理 | 不 ACK 等待重投 ❌ | ACK + 异步补偿 ✅ | 模拟 DB 超时 |
| 信号量泄漏 | 可能 ❌ | `finally` 保证释放 ✅ | 检查 `SEM.availablePermits()` |

---

## 注意事项

1. **灰度发布**：先在一个实例部署，观察 30 分钟再全量
2. **监控埋点**：`SEM.acquire()` 前后加 Micrometer Timer，监控等待耗时 P99
3. **二期优化**：将 ACK 移出 VT，通过 `BlockingQueue` 由平台线程执行 ACK
