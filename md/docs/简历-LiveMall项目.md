## LiveMall · 高并发直播电商秒杀系统

*2026.03 — 至今 | Java 25 LTS / Spring Boot 4.1 / Dubbo 3.3 / gRPC / Redis / Kafka*

**项目概述：** 面向 10w QPS 设计的直播电商秒杀系统，覆盖弹幕互动、秒杀抢购、实时排行榜核心场景。后端 6 个模块（Gateway / User / Seckill / WebSocket / Leaderboard / Common），Java 25 虚拟线程全链路调度，Dubbo + gRPC 双 RPC 通道，前端 Vue 3 SPA。

---

### 核心实现

**秒杀链路**
- Redis Lua 4 分片原子库存扣减（`userId % shardCount` 哈希路由，单次 DECR 判定，32w QPS 理论吞吐），DB 唯一索引 `uk_activity_user` 兜底一人一单
- Kafka 异步削峰创建订单 + `@Scheduled` 15s 扫描超时取消 + 乐观锁 `version` CAS 防并发冲突
- Caffeine L1 本地缓存 + Redis L2 + MySQL L3 多级缓存，无库存请求本地直接拒绝

**gRPC + Dubbo 双通道**
- 利用 Spring Boot 4.1 首次内置的 gRPC 自动配置，秒杀结果推送走 gRPC（低延迟 + HTTP/2 多路复用），服务治理走 Dubbo（SPI/负载均衡/容错/VT 线程池），各取所长
- Dubbo 3.3.6 + Nacos 注册中心，业务线程池改为 VT（`threadpool="virtual"`）

**双 Token 无状态认证**
- Access JWT（HS256, 15min 纯 CPU 验签）+ Refresh Token（Redis, 7 天 Rotation），99% 请求零 IO
- 并发刷新合并（`CompletableFuture` + `ConcurrentHashMap`）+ 分布式锁 `SETNX` 防多节点重复签发

**WebSocket 长连接服务**
- JSR-356 `@ServerEndpoint` + `ConcurrentHashMap` 双维度会话管理（sessionId × roomId）
- `Thread.startVirtualThread` 并行广播弹幕/礼物，`Semaphore(200)` 控并发，1000 人房间延迟 1000ms → ~1ms
- 连接上限保护 + 30s 心跳检测 + 匿名游客模式 + 断线重连指数退避

**Redis ZSet 实时排行榜**
- 跳表 O(log N) 增删改查，`ZINCRBY` 原子加分 + `ZREVRANGE` TopN 查询 + `ZSCORE`/`ZREVRANK` 个人排名
- WebSocket 送礼后 Dubbo RPC 异步触发加分（< 5ms），`@Scheduled` 每 5min Top100 快照落库

**Spring Cloud Gateway 统一入口**
- WebFlux 响应式网关：HMAC-SHA256 签名验签（±5min 时间窗口 + Nonce ReactiveRedis 防重放）→ JWT 鉴权（`AntPathMatcher` 白名单）→ Sentinel 热点参数限流

---

### 技术栈

`Java 25 LTS` `Spring Boot 4.1` `Spring Cloud 2025.1` `Dubbo 3.3.6` `gRPC` `Redis` `Kafka` `MySQL` `Nacos` `Sentinel` `Caffeine` `WebSocket` `Vue 3` `Docker`

---

### 量化数据

| 指标 | 数据 |
|---|---|
| 秒杀库存分片吞吐 | 4 片 × 8w QPS = 32w QPS 理论值 |
| Caffeine 过滤无效请求 | 减少 ~90% Redis 查询 |
| 弹幕 VT 并行广播 | 1000 人延迟 1000ms → ~1ms |
| 双 Token Redis 压力 | 较 Session 方案降 ~900 倍 |
| 全项目测试 | 74 tests, 0 failures |
| 模块数 | 6 个微服务 |

---

### 面试追问（背面）

| 简历 bullet | 面试官大概率追问 |
|---|---|
| Redis Lua 4 分片原子扣库存 | Lua 为什么原子？4 片怎么算的？DECR 负数？一人一单？|
| Kafka 异步削峰 | 为什么不用 RocketMQ 延时？零拷贝怎么实现？|
| gRPC + Dubbo 双通道 | Dubbo 和 gRPC 什么场景选哪个？为什么不是全换 gRPC？|
| 双 Token 99% 请求零 IO | 和 Session 比差多少倍？Refresh 泄露了怎么办？|
| VT `startVirtualThread` 广播 | VT 和平台线程区别？pinning 怎么解决？JDK 25 JEP 491？|
| Caffeine refreshAfterWrite | 和 expireAfterWrite 区别？一致性怎么保证？|
| Java 25 LTS | JEP 491 synchronized pinning 修复 / ScopedValue 正式 API / 紧凑对象头 |
