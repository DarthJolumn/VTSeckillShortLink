# VTSeckillShortLink — 高性能秒杀与短链接平台

基于 **JDK 25 (分代 ZGC + Virtual Threads) + Spring Boot 4.1** 构建的高并发秒杀与短链接平台。针对 IO 密集型特征（Redis、Kafka、MySQL），经四阶段压测验证虚拟线程 + 分代 ZGC 组合：虚拟线程消除对头阻塞、提升系统吞吐量；分代 ZGC 将 STW 控制在 3ms，降低 P99 延迟，极大改善长尾延迟。

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-blue" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen" />
  <img src="https://img.shields.io/badge/ZGC-Concurrent-brightgreen" />
  <img src="https://img.shields.io/badge/Virtual%20Threads-Enabled-blueviolet" />
</p>

---

## 性能基准

### 线程模型对比：虚拟线程 vs 平台线程

> 环境: 8vCPU/16GB VM | 秒杀扣减接口 `POST /seckill/order` | 10 万样本/次

| 并发 | 指标 | 平台线程 | 虚拟线程 | 提升 |
|------|------|---------|---------|------|
| **300** | TPS | 1,780 | 1,703 | - |
| | P99 | 112ms | 129ms | - |
| **600** | **TPS** | **1,924** | **2,071** | **+7.6%** |
| | **平均** | **155ms** | **133ms** | **↓14%** |
| | **P99** | **340ms** | **261ms** | **↓23%** |
| | **Max** | **471ms** | **337ms** | **↓28%** |

虚拟线程在 **600 并发高负载**下优势显著；300 并发低负载时与平台线程相当（瓶颈不在线程模型，而在业务逻辑与网络 IO）。

### GC 对比：分代 ZGC vs G1

> 环境: 8vCPU/16GB VM | 450 并发 / 45s 同口径压测 | 10 万秒杀订单

| 指标 | G1 (Evacuation Pause STW) | 分代 ZGC (Concurrent) | 倍数 |
|------|--------------------------|----------------------|------|
| Young GC 总耗时 | **652ms (STW)** | **1ms (并发)** | **652x** |
| 并发 GC 总耗时 | **205ms (STW)** | **1ms (并发)** | **205x** |
| **GC 总耗时** | **856ms (全部 STW)** | **3ms (零 STW)** | **285x** |
| Young GC 次数 | 170 次 | 9 次 | **19x** |

**应用层效果（300 并发 / 30s / 10w 订单）**：

| 指标 | G1 | ZGC | 提升 |
|------|-----|-----|------|
| **TPS** | 1,191 | **1,312** | **+10.2%** |
| **P99** | 269ms | **183ms** | **↓32%** |
| **Max** | 385ms | **286ms** | **↓26%** |

ZGC 彻底消除 GC STW 抖动对 P99 的影响，是秒杀场景 GC 选型的决定性优势。

---

## 秒杀核心设计

### 高并发扣减

- **Redis Lua 原子扣减**：查重-扣减-标记一条龙，Hash Tag 保证 Cluster 同槽无 CROSSSLOT，强制走 Master 避免跨槽与主从延迟
- **Kafka Semaphore 背压**：上限对齐 HikariCP 池大小（30），阻塞 poll 迫使 Broker 降速，从根源杜绝 VT 堆积引发的连接耗尽与 heap OOM
- **异步削峰**：`kafkaTemplate.send()` 纯异步发送，异常时 catch 后回补库存 Fail Fast 返回 503

### 数据一致性

- **超时退单**：DB 先更新（@Version CAS 防并发）→ Redis 后原子幂等回补（Lua EXISTS），`ReconciliationScheduler` 每 5min 对账补偿
- **消费可靠性**：Kafka Consumer 手动 ACK + 唯一索引幂等兜底（`DuplicateKeyException` → ack）+ 内存重试 3 次
- **Fail Fast**：Kafka 不可用 → 回补库存 + 503，不丢消息、不超卖

### 瓶颈定位与优化

基于 SkyWalking 链路追踪与四阶段控制变量测试，定位秒杀接口尾部延迟根因为"Kafka 同步阻塞 + Redis 跨 VM 网络"双重叠加放大（P99=264ms, Max=3342ms）：

| 阶段 | 变更 | P99 | Max | 结论 |
|------|------|:---:|:---:|------|
| 基线 | 同步 Kafka + 远端 Redis | 264ms | 3,342ms | 原始状态 |
| 仅 Redis 本地化 | 同步 Kafka + 本地 Redis | 132ms | 305ms | 网络 IO 为主要瓶颈 |
| 仅 Kafka 异步 | 异步 Kafka + 远端 Redis | 87ms | 334ms | Kafka 同步阻塞为次要瓶颈 |
| **组合优化** | **异步 Kafka + 本地 Redis** | **92ms** | **139ms** | **彻底解耦** |

---

## 短链核心设计

### 算法即契约

- **短码 = 版本前缀 + Base58(雪花ID全64位)**，实现 ProductId ↔ ShortCode 双向确定性推导
- 商品短链 **0 存储**，发布性能 +0ms 纯内存计算，DB 宕机仍可算法推导

### 多级缓存与纵深防御

- **L1 Caffeine (3s TTL)**：拦截热点消除网络 IO
- **L2 Redis (24h±10% TTL)**：全局共享承载非热点读，TTL 随机偏移规避雪崩
- **DCL 防击穿**：双检锁防缓存击穿
- **Cache Aside**：先更新 DB 再删除 Redis，利用删除操作幂等性杜绝并发写脏数据

### 高可用保障

- **Gateway 短码格式校验**：纯内存正则匹配，微秒级，非法直接 400
- **双限流**：短码 1000次/分钟 + IP 100次/分钟，Redis Lua 滑动窗口
- **点击统计**：Redis Hash 实时计数 + Kafka 异步 → MySQL 按天持久化

---

## 基础设施

- **Gateway 统一入口**：Nacos 服务注册与发现，Sentinel 令牌桶限流 + 熔断
- **安全**：HMAC-SHA256 签名 + Nonce 60s 防重放，JWT 鉴权 + 白名单路径放行
- **用户上下文**：非核心业务通过 ScopedValue 替代 ThreadLocal，虚拟线程间天然隔离无泄漏；秒杀链路显式传递规避切换开销

---

## 项目结构

| 模块 | 端口 | 职责 |
|------|------|------|
| `vtsl-gateway` | 8080 | 网关 (WebFlux)；路由转发、JWT 鉴权、签名验签、Sentinel 限流 |
| `vtsl-seckill` | 8090 | 秒杀 + 商品；活动管理、Redis Lua 扣减、Kafka 异步下单 |
| `vtsl-shortlink` | 8084 | 短链系统；算法码派生、三级缓存、点击统计 |
| `vtsl-common` | - | 公共库；ScopedValue、编解码器、注解、DTO |

---

## 运行要求

- JDK 25
- Docker Compose (中间件 VM)

---

## 详细文档

- [架构设计](vtsl-docs/architecture/)
- [性能压测](vtsl-docs/performance/)
