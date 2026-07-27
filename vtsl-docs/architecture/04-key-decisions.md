# 关键技术决策

## 一、GC 选型：分代 ZGC vs G1

**决策**：分代 ZGC

**理由**：秒杀对 P99 敏感。同口径压测（450 并发 / 45s / 10w 订单），G1 因 Evacuation Pause（STW）产生 **856ms GC 总耗时**；ZGC 全部并发，GC 总耗时仅 **3ms**，应用线程零阻塞。

**数据支撑**：详见 `../performance/02-g1-vs-zgc.md`

---

## 二、线程模型：虚拟线程

**决策**：Spring Boot 启用虚拟线程（`spring.threads.virtual.enabled=true`）

**理由**：IO 密集型场景（Redis、Kafka、MySQL），VT 优势：
- 无线程池队头阻塞：平台线程池满时请求排队，VT 按需创建无排队
- 同等资源更高吞吐：600 并发下 VT 相比平台线程 TPS +7.6%、P99 -23%
- 无 GC STW 长尾：配合 ZGC，彻底消除 GC 对延迟的干扰

**例外**：Gateway（WebFlux）仍用 EventLoop，不做 VT。

**数据支撑**：详见 `../performance/01-vt-vs-platform.md`

---

## 三、响应式：仅 Gateway

**决策**：Gateway 用 WebFlux（EventLoop），下游业务服务用 Spring MVC（VT）

**理由**：
- Gateway 是唯一高吞吐转发节点（数千连接），EventLoop 非阻塞模型优于 VT
- 下游服务逻辑复杂（DB 事务、Kafka 发送），VT 的同步编程模型更简单

---

## 四、缓存设计

### 秒杀活动缓存

| 层 | 技术 | TTL | 策略 |
|---|------|-----|------|
| L1 | Caffeine LoadingCache | refreshAfterWrite 5s | 后台异步刷新，永不阻塞请求 |

> 活动数据量级小（≤1000），单层 Caffeine 即可满足，无需 L2 Redis。

### 短链缓存

| 层 | 技术 | TTL | 策略 |
|---|------|-----|------|
| L1 | Caffeine | 3s | 拦截热点，消除网络 IO |
| L2 | Redis Hash | 24h ± 20% 抖动 | 全局共享，TTL 随机偏移防雪崩 |
| 防击穿 | SETNX 锁 + 自旋 20×50ms | 5s | DCL，高并发下仅一个线程回源 |

**一致性**：Cache Aside 模式
- 写 DB 后调用 `evict()` 删除缓存（幂等，无并发写脏数据风险）
- L1 依赖 3s 自然过期同步，兼顾性能与最终一致性

### 排行榜缓存

Redis Sorted Set：`ZINCRBY leaderboard:{roomId}` 实时计分，`ZREVRANGE` 取 TopN。

---

## 五、数据一致性防线

| # | 防线 | 技术 | 位置 |
|---|------|------|------|
| 1 | 扣减原子性 | Redis Lua（查重-扣减-标记） | `StockService.deduct()` |
| 2 | Cluster 同槽 | Hash Tag `{activityId}` | Lua KEYS 参数 |
| 3 | 禁读写分离 | `read-from: MASTER` | Redis 配置 |
| 4 | 超时回补 | DB 先（@Version）→ Redis 后（Lua EXISTS） | `SeckillService.cancelOrder()` |
| 5 | 对账补偿 | 每 5min 扫描 status=2 订单，检查 Redis 库存 | `ReconciliationScheduler` |
| 6 | DB 幂等 | `uk_activity_user` 唯一索引 | `SeckillOrderRepository` |

---

## 六、Kafka 消费可靠性

| 机制 | 说明 | 位置 |
|------|------|------|
| 手动 ACK | `ack-mode: manual`，INSERT 成功才 ack | `application.yml:78` |
| Semaphore 背压 | 上限 30 = HikariCP 池大小，阻塞 poll 迫使 Broker 降速 | `SeckillOrderConsumer.java:48` |
| 唯一索引幂等 | `DuplicateKeyException` → ack，不重复入库 | `SeckillOrderConsumer.java:91` |
| 内存重试 | `TransactionException` 重试 3 次，超限后 ack 放弃 | `SeckillOrderConsumer.java:95-100` |
| 异步发送 | `kafkaTemplate.send()` 纯异步，不阻塞请求线程 | `SeckillController.java:87` |
| Fail Fast | Kafka 不可用时回补库存 + 返回 503 | `SeckillController.java:88-93` |

---

## 七、短链算法即契约

**设计**：雪花 ID 全 64 位 Base58 编码，ProductId ↔ ShortCode 双向确定性推导。

| 维度 | 传统方案（DB 映射） | 本方案（算法派生） |
|------|-------------------|-------------------|
| 一致性 | 需分布式事务 | 数学保证，天然一致 |
| 存储成本 | 全量落库 | 商品短链 0 存储 |
| 发布性能 | 额外 DB 写入 | +0ms 纯内存计算 |
| 容灾能力 | 依赖 DB | DB 宕机仍可算法推导 |

**格式**：`P48zVYK5Fg8Kp2mN9`（前缀 + Base58 全 64 位，≤11 字符）

**实现**：`vtsl-common/.../codec/ShortCodeCodec.java:42-55`

---

## 八、ScopedValue 用户上下文

**决策**：JDK 25 ScopedValue 替代 ThreadLocal。

**理由**：
- 零拷贝：ScopedValue 不复制数据，仅绑定引用
- 不可变：绑定后不可修改，天然线程安全
- 无泄漏：不继承子线程（VT 不会意外继承父线程上下文）
- 零开销：`isBound()` + `get()` 均为 O(1)

**局限**：Gateway（WebFlux）无 Servlet API，不做 ScopedValue 绑定，仅做 header 注入。

---

## 九、锁：ReentrantLock

**决策**：使用 `ReentrantLock` 而非 `synchronized`。

**理由**：JDK 25 JEP 491 虽已修复 `synchronized` 对虚拟线程的 pinning 问题，`ReentrantLock` 保留为最佳实践（显式、可中断、支持超时）。

---

## 十、RPC 与消息

| 通信方式 | 场景 | 技术 |
|---------|------|------|
| 同步 RPC | 服务间接口调用（WS 推送、Leaderboard 计分） | Dubbo (Nacos 注册) |
| 异步消息 | 秒杀订单解耦、短链点击统计 | Kafka (KRaft) |
| 实时推送 | 秒杀结果推送、直播间广播 | Redis Pub/Sub |
