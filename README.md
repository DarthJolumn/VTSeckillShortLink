# LiveMall — 高并发直播电商秒杀系统

基于 **JDK 25 (ZGC + Virtual Threads) + Spring Boot 4.1** 构建的直播秒杀平台，覆盖秒杀抢购、直播间互动、排行榜等核心场景。所有中间件部署于单台 VM 实例。

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-blue" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen" />
  <img src="https://img.shields.io/badge/ZGC-Concurrent-brightgreen" />
  <img src="https://img.shields.io/badge/Virtual%20Threads-Enabled-blueviolet" />
</p>

---

## 架构总览

```
                          ┌───────────────────────────────────────────────┐
                          │         Spring Cloud Gateway (WebFlux)        │
                          │     JWT · HMAC-SHA256 签名 · Sentinel 限流     │
                          │     ScopedValue 用户上下文 → 注入请求头         │
                          └───┬────────────┬────────────┬─────────────────┘
                              │            │            │
            ┌─────────────────┘            │            └──────────────────┐
            ▼                               ▼                              ▼
   ┌──────────────┐               ┌──────────────────┐          ┌──────────────────┐
   │  User          │◄────Dubbo────│  WebSocket        │◄──Dubbo──│  Leaderboard     │
   │  :8081         │               │  :8083             │          │  :8084            │
   │  VT · MySQL    │               │  VT · Kafka · WS   │          │  VT · Redis ZSET  │
   └──────────────┘               └────────┬─────────┘          └──────────────────┘
                                           │
                                     ┌─────┴──────┐
                                     │            │
                                     ▼            ▼
                            ┌──────────────┐  ┌─────────────────┐
                            │  Seckill(集群) │  │  Seckill(单实例) │
                            │  :8090~8092   │  │  :8082           │
                            │  Lua · Kafka  │  │  Lua · Kafka     │
                            │  Redis Cluster │  │  Redis Cluster   │
                            └──────────────┘  └─────────────────┘

     ┌──────────────────────────────────────────────────────────────────────┐
     │     VM: 192.168.147.132 (8vCPU/16GB)                                 │
     │     ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌───────┐ ┌───────┐       │
     │     │Redis │ │MySQL │ │Nacos │ │Kafka │ │Sentinel│ │(其他)  │       │
     │     └──────┘ └──────┘ └──────┘ └──────┘ └───────┘ └───────┘       │
     └──────────────────────────────────────────────────────────────────────┘
```

---

## 性能基准

### 线程模型对比：虚拟线程 vs 平台线程

> 环境: 8vCPU/16GB VM | 秒杀扣减接口 | 10 万样本/次

| 并发 | 指标 | 平台线程 | 虚拟线程 | 提升 |
|------|------|---------|---------|------|
| **300** | TPS | 1,780 | 1,703 | - |
| | P99 | 112ms | 129ms | - |
| **600** | **TPS** | **1,924** | **2,071** | **+7.6%** |
| | **平均** | **155ms** | **133ms** | **↓14%** |
| | **P99** | **340ms** | **261ms** | **↓23%** |
| | **Max** | **471ms** | **337ms** | **↓28%** |

虚拟线程在 **600 并发高负载**下优势显著，300 并发低负载时与平台线程相当。

### GC 对比：分代 ZGC vs G1

> 环境: 8vCPU/16GB VM | 450 并发 / 45s 同口径压测 | 10 万秒杀订单

| 指标 | G1 (G1 Evacuation Pause) | 分代 ZGC (Concurrent) | 倍数 |
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

## 核心设计亮点

- **Redis Lua 原子扣减**：查重-扣减-标记一条龙，Hash Tag 保证 Cluster 同槽，强制走 Master
- **Kafka Semaphore 背压**：上限对齐 HikariCP 池大小，阻塞 poll 迫使 Broker 降速
- **异步削峰**：Producer 异步发送 + Consumer 手动 ACK + 唯一索引幂等 + 内存重试 3 次
- **多级缓存**：L1 Caffeine 3s + L2 Redis 24h±10% TTL，DCL 防击穿，Cache Aside 保一致
- **ScopedValue 用户上下文**：Gateway JWT 鉴权后注入请求头，下游绑定 ScopedValue，零泄漏
- **超时退单一致性**：DB 先（@Version CAS）→ Redis 后（Lua EXISTS 幂等）+ 5min 对账补偿

---

## 运行要求

- JDK 25
- Docker Compose (中间件 VM)

---

## 项目结构

| 模块 | 端口 | 职责 |
|------|------|------|
| `livemall-gateway` | 8080 | 网关 (WebFlux) |
| `livemall-user` | 8081 | 用户服务 |
| `livemall-seckill` | 8090~8092 | 秒杀集群 |
| `livemall-seckill-single` | 8082 | 秒杀单实例 |
| `livemall-websocket` | 8083 | 直播间/WebSocket |
| `livemall-leaderboard` | 8084 | 排行榜 |
| `livemall-common` | - | 公共模块 |
