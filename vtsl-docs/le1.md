# 简历技术点 ↔ 项目代码 学习清单

> 学完一项就把 `[ ]` 改成 `[x]`，按 Day 顺序执行。

---

## 一、总表

| 打勾 | # | 类别 | 知识点 | Day | 学习方式 | 代码路径 / 说明 |
|:---:|:-:|:----|:-------|:---:|:--------|:----------------|
| [x] | 1 | Java | ArrayList / HashMap 底层源码 | 4 | 纯八股 | 扩容流程、扰动函数、红黑树条件 |
| [x] | 2 | Java | 反射、泛型、注解 | 4 | 代码 | `vtsl-common/annotation/` — `@RequireAuth` 等 |
| [x] | 3 | Java | 异常体系 | 4 | 代码 | `vtsl-common/exception/` — BizException + GlobalExceptionHandler |
| [x] | 4 | 并发 | AQS 核心原理（state/CLH/模式） | 2 | 八股+代码 | Semaphore 背压底层就是 AQS 共享模式 |
| [x] | 5 | 并发 | synchronized 锁升级 | 2 | 纯八股 | Mark Word 64 位变化、偏向→轻量→重量 |
| [x] | 6 | 并发 | **ReentrantLock** | 2 | 代码 | `SnowflakeIdGenerator.java:23` — 直接使用 |
| [x] | 7 | 并发 | ThreadLocal 内存泄漏 | 2 | 八股+代码 | 对比 ScopedValue，理解为什么弃用 |
| [x] | 8 | 并发 | **ScopedValue** | 2 | 代码 | `UserContext.java` + `UserContextFilter.java` |
| [ ] | 9 | 并发 | 虚拟线程（JDK 21/25） | 2 | 八股+代码 | 全项目基底，重点看 `SeckillOrderConsumer.java:54` |
| [ ] | 10 | 并发 | ForkJoinPool 调度 | 2 | 纯八股 | 虚拟线程底层载体，了解即可 |
| [ ] | 11 | 并发 | **Semaphore 背压** | 2 | 代码 | `SeckillOrderConsumer.java:42` — 上限对齐 HikariCP |
| [x] | 12 | JVM | JVM 内存区域 | 3 | 纯八股 | 堆/栈/方法区/元空间 |
| [ ] | 13 | JVM | G1 GC 原理（Region/RSet/CardTable） | 3 | 八股+代码 | `vtsl-docs/performance/02-g1-vs-zgc.md` — 压测基线 |
| [ ] | 14 | JVM | 分代 ZGC（染色指针/读屏障） | 3 | 代码 | `vtsl-docs/performance/02-g1-vs-zgc.md` — 生产选型 |
| [x] | 15 | JVM | 类加载机制 / 双亲委派 | 4 | 纯八股 | 标准八股 |
| [x] | 16 | MySQL | B+ 树索引结构 | 3 | 纯八股 | 聚簇索引 vs 二级索引 |
| [x] | 17 | MySQL | Buffer Pool 管理 | 3 | 纯八股 | 了解概念即可 |
| [x] | 18 | MySQL | MVCC（ReadView/Undo Log） | 3 | 纯八股 | ReadView 生成时机、版本链 |
| [x] | 19 | MySQL | Redo Log / DWB | 3 | 纯八股 | 两阶段提交、崩溃恢复 |
| [x] | 20 | MySQL | 事务隔离级别 | 3 | 代码 | `SeckillService.java` — 订单状态更新 |
| [x] | 21 | MySQL | 锁体系（乐观锁 CAS） | 3 | 代码 | `SeckillOrder.java` — @Version |
| [x] | 22 | MySQL | 唯一索引幂等 | 3 | 代码 | `SeckillOrder.java` — `uk_activity_user` |
| [ ] | 23 | Redis | 数据结构底层（SDS/dict/skiplist 等） | 1 | 纯八股 | 面试高频 |
| [ ] | 24 | Redis | 持久化（RDB/AOF/混合） | 1 | 纯八股 | 了解即可 |
| [ ] | 25 | Redis | 过期淘汰策略 | 1 | 代码 | `ShortLinkCache.java` — 24h±10% TTL 防雪崩 |
| [ ] | 26 | Redis | **Lua 脚本** | 1 | 代码 | `lua/deduct_stock.lua` + `refund_stock.lua` |
| [ ] | 27 | Redis | **Cluster Hash Tag** | 1 | 代码 | `StockService.java` — `{activityId}` 同槽 |
| [ ] | 28 | Redis | 强制走 Master | 1 | 代码 | `application.yml` — `read-from: MASTER` |
| [ ] | 29 | Redis | 布隆过滤器（理解弃用原因） | 1 | 代码 | `ActivityBloomFilter.java` — 多实例不一致 |
| [ ] | 30 | Redis | **Redis Pub/Sub** | 2 | 代码 | `SeckillOrderConsumer.java:79` — convertAndSend |
| [x] | 31 | Spring | IoC 容器 / Bean 生命周期 | 4 | 代码 | `GrpcClientConfig.java` — @PreDestroy |
| [x] | 32 | Spring | AOP | 4 | 纯八股 | ⚠️ 简历写了但项目没用到，补理论 |
| [x] | 33 | Spring | 自动配置原理 | 4 | 代码 | `VtslGatewayApplication.java` — scanBasePackages |
| [ ] | 34 | Spring | **Gateway 过滤器链** | 4 | 代码 | `JwtAuthGlobalFilter.java` + `SignVerifyGlobalFilter.java` |
| [ ] | 35 | Spring | Nacos 服务注册发现 | 4 | 代码 | `application.yml` — 配置中心 |
| [ ] | 36 | Spring | **Sentinel 限流熔断** | 4 | 代码 | Gateway 令牌桶配置 |
| [ ] | 37 | Spring | SkyWalking 链路追踪 | 3 | 代码 | `vtsl-docs/performance/03-async-shave.md` |
| [ ] | 38 | Kafka | **异步发送** | 2 | 代码 | `SeckillController.java:87` — kafkaTemplate.send() |
| [ ] | 39 | Kafka | **手动 ACK** | 2 | 代码 | `SeckillOrderConsumer.java` — ack.acknowledge() |
| [ ] | 40 | Kafka | **幂等消费** | 2 | 代码 | 唯一索引 + 内存重试 3 次 |
| [ ] | 41 | Kafka | **背压机制** | 2 | 代码 | Semaphore 阻塞 poll → Broker 降速 |
| [ ] | 42 | Kafka | **acks=all 副本同步** | 2 | 代码 | `KafkaConfig.java` |
| [ ] | 43 | 分布式 | Cache Aside 模式 | 1 | 代码 | `ShortLinkCache.java` — 先 DB 后 Redis |
| [ ] | 44 | 分布式 | 最终一致性（TTL 自然过期） | 1 | 代码 | `ShortLinkCache.java` — L1 3s 过期 |
| [ ] | 45 | 分布式 | 分布式事务（对账补偿） | 3 | 代码 | `ReconciliationScheduler.java` |
| [ ] | 46 | 分布式 | 幂等设计 | 3 | 代码 | `StockService.java` — Lua EXISTS + 唯一索引 |
| [ ] | 47 | 分布式 | 令牌桶限流 | 4 | 代码 | Gateway Sentinel |
| [ ] | 48 | 分布式 | 滑动窗口限流 | 1 | 代码 | `RateLimitService.java` |
| [ ] | 49 | 工具 | 雪花算法 | 4 | 代码 | `SnowflakeIdGenerator.java` |
| [ ] | 50 | 工具 | Base58 编解码 | 4 | 代码 | `ShortCodeCodec.java` |
| [ ] | 51 | 工具 | JWT 鉴权 | 4 | 代码 | `JwtAuthGlobalFilter.java` |
| [ ] | 52 | 工具 | HMAC-SHA256 + Nonce | 4 | 代码 | `SignVerifyGlobalFilter.java` |
| [ ] | 53 | 工具 | DCL 防击穿 | 1 | 代码 | `ShortLinkCache.java` |
| [ ] | 54 | 工具 | Dubbo RPC | 4 | 代码 | `ProductShortLinkService.java` |

---

## 二、4 天执行计划

### Day 1（7/28）：Redis + 缓存

| 时段 | 项目 |
|:---|:----|
| 上午八股 | #23 SDS/dict/skiplist、#24 RDB/AOF、#25 过期策略、#27 Hash Tag |
| 下午代码 | #25 `ShortLinkCache.java` → #43 Cache Aside → #44 最终一致性 → #53 DCL → #48 `RateLimitService.java` → #27 `StockService.java` → #26 `deduct_stock.lua` → #29 `ActivityBloomFilter.java` |
| 晚上复盘 | 画多级缓存+库存扣减数据流图 |

### Day 2（7/29）：并发 + 虚拟线程 + Kafka

| 时段 | 项目 |
|:---|:----|
| 上午八股 | #4 AQS → #5 锁升级 → #6 ReentrantLock → #7 ThreadLocal → #9 虚拟线程 → #10 ForkJoinPool |
| 下午代码 | #8 `UserContext.java` → #8 `UserContextFilter.java` → #30 Redis Pub/Sub → #9 `SeckillOrderConsumer.java` → #11 Semaphore 背压 → #38 `SeckillController.java` → #42 `KafkaConfig.java` → #39 手动 ACK → #40 幂等消费 |
| 晚上复盘 | ScopedValue vs ThreadLocal 对比表、背压流程图 |

### Day 3（7/30）：JVM + MySQL + 性能优化

| 时段 | 项目 |
|:---|:----|
| 上午八股 | #12 JVM 内存 → #13 G1 Region → #14 ZGC 染色指针 → #16 B+树 → #18 MVCC → #19 Redo Log → #20 事务隔离 → #21 乐观锁 |
| 下午代码 | #13+14 `02-g1-vs-zgc.md` → #37 `03-async-shave.md` → #21+22 `SeckillOrder.java` → #45 `ReconciliationScheduler.java` → 超时退单 |
| 晚上复盘 | 整理压测对比表、优化闭环 |

### Day 4（7/31）：Spring 生态 + 基础设施

| 时段 | 项目 |
|:---|:----|
| 上午八股 | #31 IoC 三级缓存 → #32 AOP → #33 自动配置 → #34 Gateway 过滤器链 → #47 令牌桶 |
| 下午代码 | #34 `JwtAuthGlobalFilter.java` → #52 `SignVerifyGlobalFilter.java` → Gateway 限流 → #33 `VtslGatewayApplication.java` → #50 `ShortCodeCodec.java` → #49 `SnowflakeIdGenerator.java` → #54 Dubbo |
| 晚上复盘 | 画全链路架构图、3 分钟模拟面试 |

---

## 三、高危区（纯八股，项目零代码）

| 知识点 | 风险 | 对应清单编号 | 建议 |
|--------|:---:|:----------:|------|
| ArrayList / HashMap 源码 | 🟡 中 | #1 | 手写扩容 + 扰动函数 + 转红黑树条件 |
| AQS 核心原理 | 🔴 高 | #4 | 能手画 state + CLH 队列 + acquire/release 流程 |
| synchronized 锁升级 | 🔴 高 | #5 | 讲清 Mark Word 变化、偏向锁撤销 |
| ReentrantLock | 🟡 中 | #6 | 结合 AQS，对比 synchronized |
| ForkJoinPool | 🟢 低 | #10 | 工作窃取，虚拟线程载体 |
| JVM 内存区域 | 🟡 中 | #12 | 结合 ZGC 染色指针准备 |
| MySQL Buffer Pool / DWB | 🟢 低 | #17 | 了解概念 |
| Spring AOP | 🟡 中 | #32 | 建议补一个日志切面 demo 防追问 |` CXVZ
 