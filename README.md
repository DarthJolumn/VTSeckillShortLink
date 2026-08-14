# VTSeckillShortLink — 高性能秒杀与短链接平台

基于 **JDK 25 (分代 ZGC + Virtual Threads) + Spring Boot 4.1** 构建的高并发秒杀与短链接平台。针对 IO 密集型特征（Redis、Kafka、MySQL），经压测验证虚拟线程 + 分代 ZGC 组合：虚拟线程消除对头阻塞、提升吞吐；分代 ZGC 将 STW 控制在 3ms，消除 GC 抖动对 P99 的影响。

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-blue" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen" />
  <img src="https://img.shields.io/badge/ZGC-Concurrent-brightgreen" />
  <img src="https://img.shields.io/badge/Virtual%20Threads-Enabled-blueviolet" />
  <img src="https://img.shields.io/badge/Kafka-3%20Broker%20Cluster-orange" />
</p>

---

## 核心亮点

- **策略模式扣库存引擎**：`SeckillStrategy` 接口 + 工厂路由，三种可动态调整的扣减策略（Redis+Kafka 异步 / Redis+Kafka 同步 / DB 乐观锁直扣），覆盖从最终一致到强一致的不同场景
- **滑动窗口自适应降级**：REDIS_SYNC 同步等待成为瓶颈时，环形数组滑动窗口检测平均耗时/超时率超阈值，自动降级为异步受理，冷却后采样/TTL 双路径恢复——**单次失败 Fail Fast 回补库存，整体降级保吞吐**
- **异步发送四层可靠性**：pending 标记 → 回调补偿 → 定时补投 → 对账兜底，at-least-once + 消费端唯一索引幂等 = exactly-once
- **Kafka 3-broker 集群**：KRaft 模式 + RF=3 + 双 Listener（容器互联/宿主机访问），单 broker 宕机消息不丢

---

## 性能基准

### 线程模型对比：虚拟线程 vs 平台线程

> 环境: 8vCPU/16GB VM | 秒杀扣减接口 `POST /seckill/order` | 10 万样本/次

| 并发 | 指标 | 平台线程 | 虚拟线程 | 提升 |
|------|------|---------|---------|------|
| **600** | **TPS** | **1,924** | **2,071** | **+7.6%** |
| | **P99** | **340ms** | **261ms** | **↓23%** |
| | **Max** | **471ms** | **337ms** | **↓28%** |

### GC 对比：分代 ZGC vs G1

> 环境: 8vCPU/16GB VM | 450 并发 / 45s 同口径压测 | 10 万秒杀订单

| 指标 | G1 (Evacuation Pause STW) | 分代 ZGC (Concurrent) | 倍数 |
|------|--------------------------|----------------------|------|
| **GC 总耗时** | **856ms (全部 STW)** | **3ms (零 STW)** | **285x** |
| 应用层 P99 | 269ms | **183ms** | **↓32%** |

### 🔥 REDIS_SYNC 降级链路压测（滑动窗口降级验证）

> 1000 并发 | Kafka 故障注入（tc 网络延迟）| 10 万订单全落库

| 指标 | 值 |
|------|-----|
| 平均 / 中位数 | 84ms / 62ms |
| P90 / P95 / P99 | 196ms / 273ms / 451ms |
| **错误率** | **0.000%**（降级期间请求全部受理成功） |
| **最终一致性** | **100,000 单全部落库，0 丢单 0 超卖** |
| 吞吐 | 764.8 req/s |

**验证闭环**：注入故障 → 滑动窗口检测 avg>500ms 自动降级 REDIS_ASYNC → 降级期间异步受理保吞吐 → 故障移除后采样/TTL 恢复 → 最终 ordered 标记数 = DB 订单数 = 10w，库存归零。降级瞬间超时重发的重复消息全部被唯一索引幂等拦截。

---

## 秒杀核心设计

### 策略引擎：三种可动态调整的扣减策略（策略模式）

```
SeckillStrategy（接口）
  ├── RedisAsyncStrategy → Lua 原子扣减 + Kafka 异步（send() 不等待）→ 最终一致性
  ├── RedisSyncStrategy  → Lua 原子扣减 + Kafka 同步（send().get(3s)）→ 强一致性
  └── DBQueueStrategy    → DB 乐观锁直扣（@Version CAS）+ MQ 顺序消费 → 强一致低并发
```

- **工厂路由**：`SeckillStrategyFactory` 按 `SeckillMode` 路由，新增策略实现接口即可（开闭原则）
- **运行时动态切换**：`PUT /seckill/activity/{id}/mode`，Caffeine 刷新即时生效；Redis 系共用 Lua 库存可互切，DB_QUEUE 库存模型不同禁止互切

### 滑动窗口自适应降级（REDIS_SYNC）

```
SYNC（同步等待）──窗口 avg>500ms 或 rate>5%──▶ ASYNC（异步受理）
      ▲                                          │
      └──── 采样连续 5 次成功 / TTL 60s 到期 ──────┘
```

- **环形数组滑动窗口**（`SlidingWindowStats`）：只统计最近 100 次，突发故障即时触发（对比累计统计的迟钝）
- **失败语义**：单次失败 ≠ 降级——同步模式单次 Kafka 发送失败立即回补库存 + 503（Fail Fast），降级是窗口持续超阈值后的整体决策
- **恢复双路径**：有流量时 5% 采样秒级恢复；无流量时 TTL 60s 兜底恢复，不依赖请求、不产生探针消息

### 异步发送四层可靠性

```
① SET pending:{orderNo} → ② send() 立即返回
   → ③ 回调: 成功删 pending / 失败进补偿队列
   → ④ PendingOrderScanner 30s 补投 + CompensateQueueConsumer 5s 重试（>3 次进死信）
   → ⑤ LostOrderReconciler 对账: ordered 标记存在但 DB 无订单 → 幂等回补
```

### 数据一致性

- **取消订单**：DB 先（`@Version` CAS 防并发）→ Redis 后（`refund_stock.lua` EXISTS 幂等回补），ReconciliationScheduler 每 5min 对账兜底
- **消费可靠性**：Kafka 消费端 Semaphore 背压（对齐 HikariCP）+ 手动 ACK + 唯一索引幂等 + 内存重试 3 次

### 瓶颈定位与优化

SkyWalking 链路追踪 + 四阶段控制变量法定位尾部延迟根因为"Kafka 同步阻塞 + Redis 跨 VM 网络"叠加放大（P99=264ms, Max=3342ms），异步化 + Redis 本地化后 P99 降至 92ms（↓67%）。

---

## 短链核心设计

- **算法即契约**：短码 = 版本前缀 + Base58(雪花ID全64位)，ProductId ↔ ShortCode 双向确定性推导，商品短链 **0 存储**、+0ms 纯内存计算
- **多级缓存**：L1 Caffeine (3s TTL) + L2 Redis (24h±10% TTL 随机偏移)，DCL 防击穿 + Cache Aside
- **高可用**：Gateway 短码格式校验（纯内存正则微秒级）、双限流（短码 1000次/min + IP 100次/min）、点击统计 Redis 计数 + Kafka 异步落库

---

## 基础设施

- **Kafka 集群**：3 broker KRaft（RF=3 + minISR=2 + 幂等生产者），双 Listener（容器互联 INTERNAL / 宿主机 EXTERNAL），topic 由 KafkaAdmin 启动幂等创建
- **Gateway 统一入口**：Nacos 服务注册与发现，Sentinel 令牌桶限流 + 熔断
- **安全**：HMAC-SHA256 签名 + Nonce 60s 防重放，JWT 鉴权 + 白名单路径放行
- **用户上下文**：ScopedValue 替代 ThreadLocal，虚拟线程间天然隔离无泄漏

---

## 项目结构

| 模块 | 端口 | 职责 |
|------|------|------|
| `vtsl-gateway` | 8080 | 网关 (WebFlux)；路由转发、JWT 鉴权、签名验签、Sentinel 限流 |
| `vtsl-seckill` | 8090 | 秒杀 + 商品；策略引擎、Lua 扣减、滑动窗口降级、Kafka 异步下单 |
| `vtsl-shortlink` | 8084 | 短链系统；算法码派生、三级缓存、点击统计 |
| `vtsl-common` | - | 公共库；ScopedValue、编解码器、注解、DTO |

---

## 运行要求

- JDK 25
- Docker Compose（中间件 VM：MySQL/Redis/Nacos/Kafka 集群）

---

## 详细文档

- [架构设计](vtsl-docs/architecture/)
- [性能压测](vtsl-docs/performance/)（含降级链路压测 `04-sync-degrade.md`）
- [秒杀链路全解（代码级学习文档）](vtsl-docs/resourceiambic/秒杀链路全解-代码级学习文档.md)
