好的，我结合前两版，把映射表做了一次“去重补漏”，并将学习计划的**串联逻辑**标注得更清晰。下面是最终版。

---

## 📋 简历技术点 ↔ 项目代码 完整映射

### 一、Java 核心

| 技术点 | 项目代码 | 路径 | 备注 |
|--------|:---:|------|------|
| ArrayList / HashMap 底层 | ❌ | — | 纯八股，需额外复习源码 |
| JDK 21 源码研读 | ❌ | — | 纯理论 |
| 反射、泛型、注解 | ⚠️ | `vtsl-common/annotation/` | 自定义 `@RequireAuth` 等，建议补一个注解处理器加深理解 |
| 异常体系 | ✅ | `vtsl-common/exception/` (BizException, GlobalExceptionHandler) | 全局异常处理实战 |

### 二、并发编程

| 技术点 | 项目代码 | 路径 | 备注 |
|--------|:---:|------|------|
| AQS 核心原理 | ❌ | — | Semaphore 背压底层是 AQS，可结合项目讲 |
| synchronized 锁升级 | ❌ | — | 纯八股 |
| ReentrantLock | ❌ | — | 纯八股 |
| ThreadLocal | ⚠️ | `vtsl-common/context/UserContext.java` | 项目用 ScopedValue 替代，代码中有对比思路 |
| ScopedValue | ✅ | `vtsl-common/context/UserContext.java` + `UserContextFilter.java` | 非核心业务上下文传递 |
| 虚拟线程 | ✅ | 全项目 | 核心卖点，`SeckillOrderConsumer.java` 等 |
| ForkJoinPool 调度 | ❌ | — | 虚拟线程底层，理论 |
| Semaphore 背压 | ✅ | `vtsl-seckill/consumer/SeckillOrderConsumer.java` | Kafka 消费端限流 |

### 三、JVM

| 技术点 | 项目代码 | 路径 | 备注 |
|--------|:---:|------|------|
| JVM 内存区域 | ❌ | — | 纯理论 |
| Region/TLAB/RSet/CardTable | ❌ | — | 纯理论 |
| G1 GC 原理 | ✅ | `vtsl-docs/performance/02-g1-vs-zgc.md` | 压测基线 |
| 分代 ZGC | ✅ | `vtsl-docs/performance/02-g1-vs-zgc.md` + 配置 | 生产选型，STW 3ms |
| 类加载 / 双亲委派 | ❌ | — | 纯理论 |

### 四、MySQL

| 技术点 | 项目代码 | 路径 | 备注 |
|--------|:---:|------|------|
| B+ 树索引 | ❌ | — | 纯理论 |
| Buffer Pool / DWB | ❌ | — | 纯理论 |
| MVCC (ReadView/Undo Log) | ❌ | — | 纯理论 |
| Redo Log | ❌ | — | 纯理论 |
| 事务隔离级别 | ✅ | `vtsl-seckill/service/SeckillService.java` | 订单更新 |
| 锁体系 | ✅ | `vtsl-seckill/entity/SeckillOrder.java` | @Version 乐观锁 CAS |
| 唯一索引幂等 | ✅ | `vtsl-seckill/entity/SeckillOrder.java` | uk_activity_user |

### 五、Redis

| 技术点 | 项目代码 | 路径 | 备注 |
|--------|:---:|------|------|
| SDS/quicklist/listpack/dict/skiplist | ❌ | — | 纯理论 |
| RDB/AOF/混合持久化 | ❌ | — | 纯理论 |
| 过期淘汰策略 | ✅ | `vtsl-shortlink/service/ShortLinkCache.java` | 24h ± 10% TTL |
| Lua 脚本 | ✅ | `vtsl-seckill/resources/lua/deduct_stock.lua, refund_stock.lua` | 原子扣减/回补 |
| Cluster Hash Tag | ✅ | `vtsl-seckill/service/StockService.java` | 同槽无 CROSSSLOT |
| Master 路由 | ✅ | `vtsl-seckill/resources/application.yml` | 避免 replica lag |
| 布隆过滤器 | ⚠️ | `vtsl-seckill/service/ActivityBloomFilter.java`（已注释） | 理解弃用原因 |
| Pub/Sub | ❌ | — | 未涉及 |

### 六、Spring 生态

| 技术点 | 项目代码 | 路径 | 备注 |
|--------|:---:|------|------|
| IoC 容器 | ✅ | `vtsl-seckill/config/` | Bean 依赖注入 |
| AOP | ❌ | — | 简历写了但项目没用到，高危 |
| 自动配置原理 | ✅ | `vtsl-gateway/VtslGatewayApplication.java` | scanBasePackages |
| Bean 生命周期 | ✅ | `vtsl-seckill/config/GrpcClientConfig.java` | @PreDestroy 关闭 |
| Gateway | ✅ | `vtsl-gateway/filter/` | 路由、鉴权、限流 |
| Nacos | ✅ | `application.yml` | 服务发现/配置 |
| Sentinel | ✅ | `vtsl-gateway/filter/` | 令牌桶限流 |
| SkyWalking | ✅ | `vtsl-docs/performance/03-async-shave.md` | 瓶颈定位 |

### 七、Kafka

| 技术点 | 项目代码 | 路径 | 备注 |
|--------|:---:|------|------|
| 异步发送 | ✅ | `vtsl-seckill/controller/SeckillController.java` | kafkaTemplate.send() |
| 手动 ACK | ✅ | `vtsl-seckill/consumer/SeckillOrderConsumer.java` | 消费确认 |
| 幂等消费 | ✅ | 同上 + 唯一索引 | 内存重试 3 次 |
| 背压 | ✅ | 同上 (Semaphore) | 上限 = HikariCP 池 |
| acks=all | ✅ | `vtsl-seckill/config/KafkaConfig.java` | 零丢失 |

### 八、分布式理论

| 技术点 | 项目代码 | 路径 | 备注 |
|--------|:---:|------|------|
| Cache Aside | ✅ | `vtsl-shortlink/service/ShortLinkCache.java` | 先 DB 后 Redis |
| 最终一致性 | ✅ | 同上 (L1 3s 过期) | TTL 随机偏移 |
| 分布式事务 | ✅ | `vtsl-seckill/scheduler/ReconciliationScheduler.java` | DB → Redis + 对账 |
| 幂等设计 | ✅ | `vtsl-seckill/service/StockService.java` | Lua EXISTS + 唯一索引 |
| 限流（令牌桶/滑动窗口）| ✅ | `vtsl-gateway/` 和 `vtsl-shortlink/service/RateLimitService.java` | Sentinel + 自定义 |

### 九、工具 / 算法

| 技术点 | 项目代码 | 路径 |
|--------|:---:|------|
| 雪花算法 | ✅ | `vtsl-common/util/SnowflakeIdGenerator.java` |
| Base58 | ✅ | `vtsl-common/codec/ShortCodeCodec.java` |
| JWT | ✅ | `vtsl-gateway/filter/JwtAuthGlobalFilter.java` |
| HMAC-SHA256 | ✅ | `vtsl-gateway/filter/SignVerifyGlobalFilter.java` |
| DCL 防击穿 | ✅ | `vtsl-shortlink/service/ShortLinkCache.java` |
| Dubbo RPC | ✅ | `vtsl-common/api/ProductShortLinkService.java` |

---

## 🗓 4 天“八股→代码”串联计划（7/28 - 7/31）

### 🔴 Day 1：Redis + 缓存与库存设计

**上午：八股先修**
- 底层数据结构：SDS, quicklist, listpack, dict, skiplist
- 持久化：RDB vs AOF vs 混合，写时复制
- 过期淘汰策略：LRU, LFU, TTL 及实际选择
- Cluster 分片：槽、Hash Tag、MOVED/ASK 重定向

**下午：带着概念看代码**（顺序不要变）
1. `ShortLinkCache.java` —— 感受 L1 Caffeine 3s + L2 Redis 24h±10% TTL 如何用刚学的“过期策略”防雪崩，DCL 防击穿
2. `RateLimitService.java` —— Lua 实现滑动窗口限流，体会 Lua 原子性
3. `StockService.java` → `lua/deduct_stock.lua` → `lua/refund_stock.lua` —— 秒杀扣减：Hash Tag 保证同槽，Lua 查重-扣减-标记，回补幂等
4. `ActivityBloomFilter.java`（已注释）—— 思考为什么弃用，深化“缓存穿透”理解

**晚上：复盘串联**
- 画图：多级缓存 + 秒杀库存数据流
- 口头回答：“为什么 24h±10% 能防雪崩？”“Lua 能替代 Redis 事务吗？”“Hash Tag 的代价是什么？”

---

### 🔴 Day 2：并发编程 + 虚拟线程与异步

**上午：八股先修**
- AQS 模型：state, CLH 队列，独占/共享
- synchronized 锁升级全过程（偏向锁→轻量锁→重量锁）
- ReentrantLock 与 Condition
- ThreadLocal 内存泄漏与 ScopedValue 的不可变、天然隔离优势
- 虚拟线程调度：ForkJoinPool 作为载体线程（了解即可）

**下午：看代码**
1. `UserContext.java` + `UserContextFilter.java` —— ScopedValue 绑定与清理，对比 ThreadLocal
2. `SeckillController.java` —— 异步 `kafkaTemplate.send()`，快速失败
3. `SeckillOrderConsumer.java` —— 虚拟线程消费 + Semaphore 背压（AQS 共享模式实例）+ 手动 ACK + 内存重试
4. `KafkaConfig.java` —— acks=all 配置

**晚上：复盘串联**
- 画对比表：ThreadLocal vs ScopedValue
- 用自己的话解释：“Semaphore 背压如何防止虚拟线程堆积 OOM？”“为什么虚拟线程适合 IO 密集型？”

---

### 🔴 Day 3：JVM + MySQL + 性能优化全链路

**上午：八股先修**
- JVM 内存布局，G1 Region、RSet、SATB
- ZGC 染色指针、读屏障、分代 ZGC 与 G1 的核心差异（STW 来源）
- MySQL B+ 树，聚集索引/二级索引
- MVCC：ReadView 生成时机、Undo Log 版本链
- 事务隔离级别、锁（Record/Gap/Next-Key）、Redo Log 两阶段提交

**下午：看压测报告 + 关键代码**
1. `01-vt-vs-platform.md` → 虚拟线程 vs 平台线程性能数据
2. `02-g1-vs-zgc.md` → G1 856ms STW vs ZGC 3ms，理解为什么秒杀选 ZGC
3. `03-async-shave.md` → SkyWalking 如何定位 Kafka 同步阻塞 + Redis 网络延迟叠加，异步+本地化的优化路径，P99 降 67%
4. `SeckillOrder.java` → @Version 乐观锁 + uk_activity_user 唯一索引
5. `ReconciliationScheduler.java` + `TimeoutCancelScheduler.java` → 分布式事务补偿，最终一致性

**晚上：复盘**
- 整理压测对比表
- 能清晰讲述：“我们的性能优化三步走：ZGC 换 G1 → 异步 Kafka → Redis 本地化，瓶颈定位靠 SkyWalking”

---

### 🔴 Day 4：Spring 生态 + 微服务基础设施 + Kafka 原理

**上午：八股先修**
- Spring IoC 三级缓存解决循环依赖
- AOP 代理链（JDK vs CGLIB）
- 自动配置原理，`@Conditional`
- Gateway 路由/过滤器链，Sentinel 令牌桶算法
- Kafka 分区、副本、ISR、Leader Epoch，Producer 发送缓冲，Consumer 位移提交，高水位

**下午：看基础设施代码**
1. `JwtAuthGlobalFilter.java` → JWT 鉴权过滤器
2. `SignVerifyGlobalFilter.java` → HMAC-SHA256 签名 + Nonce 防重放
3. `ShortCodeValidationFilter.java` → 短码格式校验
4. `VtslGatewayApplication.java` → 网关启动与扫描路径
5. `ShortCodeCodec.java` → Base58 实现
6. `SnowflakeIdGenerator.java` → 雪花算法生成分布式 ID
7. 回头二刷 `SeckillOrderConsumer.java`，这次从 Kafka 底层原理角度理解：手动 ack 如何保证不丢消息，背压如何匹配 HikariCP

**晚上：复盘 + 模拟面试**
- 画一张完整架构图：Gateway → Nacos → 业务服务 → Kafka → 消费，标注所有关键技术选型
- 练习 3 分钟项目介绍，必须包含“虚拟线程 + 分代 ZGC + 异步削峰”这个性能故事
- 针对简历写了但项目没代码的 **高危点**（AQS、synchronized 锁升级、Spring AOP 等），快速过一遍核心八股

---

## ⚠️ 简历写了但项目零代码的点（面试高危区）

| 知识点 | 风险 | 建议 |
|--------|:--:|------|
| AQS 核心原理 | 🔴 高 | 必须能手画 acquire/release 流程，结合 Semaphore 讲 |
| synchronized 锁升级 | 🔴 高 | 能讲清 Mark Word 变化，需纯八股补 |
| ReentrantLock | 🟡 中 | 结合 AQS，说明与 synchronized 区别 |
| Spring AOP | 🟡 中 | 简历写了，建议自己在项目里加一个简单切面日志 |
| ArrayList / HashMap 扩容 | 🟡 中 | 高频考点，看一遍 JDK 源码即可 |
| MySQL Buffer Pool / DWB | 🟢 低 | 概念题，快速过 |

---

这个最终版把两版计划的核心优点融合了：保留了第一版的结构和第二版的**高危区警示**，同时将学习顺序强化为“先八股、再看代码、晚上画图讲出来”。你可以直接按这个执行，需要的话我可以把某一天的八股整理成问题清单，方便你自我抽查。