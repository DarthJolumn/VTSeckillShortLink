理解你的需求。既然你提供了两个版本的拆解（一个侧重表格映射，一个侧重学习路径），我将二者取其精华，融合成一份**最终版“八股-项目”串联学习方案**。
这份方案的核心逻辑是：**先过理论（补齐盲区）→ 带着问题看代码（实战验证）→ 晚上复盘（输出面试话术）**。
---
## 📋 第一部分：简历技术点 ↔ 项目代码 精准映射总表
这张表是你复习的“导航地图”。**状态标记说明**：
- ✅ **实战**：项目有对应代码，面试必问，复习优先级最高。
- ⚠️ **间接/理论**：项目有应用但未显式体现，或作为对比参照，需结合原理理解。
- ❌ **未涉及**：简历写了但项目无代码，属于“纯八股”，面试容易被问倒，需单独背诵。
### 一、Java 核心与并发编程
| 简历技术点 | 状态 | 项目代码路径 / 场景 | 复习策略 |
| :--- | :---: | :--- | :--- |
| **JDK 21/25 虚拟线程** | ✅ | `SeckillOrderConsumer.java` (消费端)<br>`SeckillController.java` (请求端) | 核心卖点，必须能手绘虚拟线程调度模型图 |
| **ScopedValue** | ✅ | `UserContext.java` (替代 ThreadLocal)<br>`UserContextFilter.java` (绑定上下文) | 对比 ThreadLocal 的内存泄漏问题来讲 |
| **Semaphore 背压** | ✅ | `SeckillOrderConsumer.java` (限制消费速率) | 关联 AQS 原理，解释为何能防 OOM |
| **反射、泛型、注解** | ✅ | `vtsl-common/annotation/` (`@RequireAuth`等) | 了解自定义注解 + 拦截器鉴权流程 |
| **异常体系** | ✅ | `GlobalExceptionHandler.java` | 全局异常处理的统一封装 |
| AQS 原理 | ⚠️ | Semaphore 底层依赖 AQS | 理论八股，需能手写 AQS 核心变量 |
| synchronized 锁升级 | ❌ | — | 纯理论，需背诵 Mark Word 变化过程 |
| ThreadLocal | ⚠️ | 被 ScopedValue 替代 | 重点讲为何替代，以及旧方案的缺陷 |
| ArrayList / HashMap | ❌ | — | 高频八股，单独背诵扩容/扰动函数 |
### 二、JVM 与 性能调优
| 简历技术点 | 状态 | 项目代码路径 / 场景 | 复习策略 |
| :--- | :---: | :--- | :--- |
| **分代 ZGC** | ✅ | `vtsl-docs/performance/02-g1-vs-zgc.md` | 重点：STW 从 856ms -> 3ms 的压测数据 |
| **G1 GC 原理** | ✅ | 同上 (作为对比基线) | 重点：理解 G1 的 Evacuation Pause 痛点 |
| JVM 内存区域 | ⚠️ | 压测分析涉及堆内存分析 | 理论结合调优参数分析 |
| SkyWalking 链路追踪 | ✅ | `vtsl-docs/performance/03-async-shave.md` | 重点：如何定位到 Kafka + Redis 双重瓶颈 |
### 三、Redis 缓存与幂等
| 简历技术点 | 状态 | 项目代码路径 / 场景 | 复习策略 |
| :--- | :---: | :--- | :--- |
| **Redis Lua 脚本** | ✅ | `lua/deduct_stock.lua`<br>`lua/refund_stock.lua` | 核心：查重-扣减-标记原子操作 |
| **多级缓存 (L1/L2)** | ✅ | `ShortLinkCache.java` | 重点：Caffeine 3s + Redis 24h 防雪崩设计 |
| **Cache Aside 模式** | ✅ | `ShortLinkCache.java` | 重点：先更 DB 再删 Redis 的并发一致性问题 |
| **Hash Tag / Cluster** | ✅ | `StockService.java` | 重点：解决 CROSSSLOT 错误，强制走 Master |
| 布隆过滤器 | ⚠️ | `ActivityBloomFilter.java` (已注释) | 了解为何弃用（内存 vs 精准度权衡） |
| SDS / 持久化 / 淘汰 | ❌ | — | 纯理论八股，需单独背诵 |
### 四、MySQL 与 分布式事务
| 简历技术点 | 状态 | 项目代码路径 / 场景 | 复习策略 |
| :--- | :---: | :--- | :--- |
| **唯一索引幂等** | ✅ | `SeckillOrder.java` (`uk_activity_user`) | 核心：防止重复下单的最后一道防线 |
| **乐观锁 (@Version)** | ✅ | `SeckillOrder.java` | 核心：CAS 思想防数据冲突 |
| **分布式事务** | ✅ | `ReconciliationScheduler.java` | 重点：DB 先更新 -> Redis 回补 -> 定时对账 |
| MVCC / 锁体系 | ⚠️ | 业务代码底层支撑 | 理论结合“防超卖”场景讲解 |
| B+ 树 / Buffer Pool | ❌ | — | 纯理论八股 |
### 五、Spring 生态与 微服务
| 简历技术点 | 状态 | 项目代码路径 / 场景 | 复习策略 |
| :--- | :---: | :--- | :--- |
| **Gateway 过滤器链** | ✅ | `JwtAuthGlobalFilter.java`<br>`SignVerifyGlobalFilter.java` | 重点：JWT 鉴权 + HMAC 签名防篡改 |
| **Sentinel 限流** | ✅ | Gateway 配置 | 重点：令牌桶算法参数配置 |
| **Nacos** | ✅ | 配置文件 / 启动类 | 理论：服务注册发现与配置中心原理 |
| Spring AOP | ❌ | — | 简历写了但项目无代码，需补理论 |
| Bean 生命周期 | ✅ | `GrpcClientConfig.java` (`@PreDestroy`) | 理论结合优雅停机场景 |
### 六、消息队列 (Kafka)
| 简历技术点 | 状态 | 项目代码路径 / 场景 | 复习策略 |
| :--- | :---: | :--- | :--- |
| **异步削峰** | ✅ | `SeckillController.java` (异步发送) | 核心：解耦请求与处理，P99 降低 67% |
| **消费可靠性** | ✅ | `SeckillOrderConsumer.java` (手动 ACK + 重试) | 核心：acks=all + 手动提交防丢失 |
| Kafka 幂等 | ✅ | `SeckillOrderConsumer.java` (内存重试 + 唯一索引) | 结合数据库唯一索引讲 |
---
## 📅 第二部分：4天“理论→实战”串联学习计划
### 🔴 Day 1 (7/28)：Redis 核心 + 缓存/库存架构
**目标**：攻克 Redis 底层原理，理解项目中的“多级缓存”和“Lua 原子操作”。
| 时间段 | 学习内容 | 理论 (上午/中午) | 实战 (下午) |
| :--- | :--- | :--- | :--- |
| **上午** | **Redis 八股扫盲** | 1. 底层结构：SDS (O(1)长度)、Dict (渐进式Rehash)、SkipList (索引)<br>2. 持久化：RDB (快照) vs AOF (写操作日志)<br>3. 淘汰策略：LRU/LFU/TTL | — |
| **下午** | **项目代码串联** | 带着问题看代码：<br>1. *为什么短链要用两级缓存？* → 看 `ShortLinkCache.java` TTL 差异设计<br>2. *Lua 如何防超卖？* → 看 `deduct_stock.lua` 的 `EXISTS` + `DECR` 原子性 | 1. `vtsl-shortlink/service/ShortLinkCache.java`<br>2. `vtsl-seckill/resources/lua/deduct_stock.lua`<br>3. `vtsl-seckill/service/StockService.java` (Hash Tag 部分) |
| **晚上** | **复盘与输出** | 1. 画出 **L1 Caffeine -> L2 Redis -> DB** 架构图。<br>2. 准备话术：“为何设计 24h ± 10% TTL？”（答：防雪崩）。 | — |
---
### 🔴 Day 2 (7/29)：并发编程 + 虚拟线程
**目标**：理解虚拟线程调度机制，掌握项目中的“背压”和“上下文传递”设计。
| 时间段 | 学习内容 | 理论 (上午/中午) | 实战 (下午) |
| :--- | :--- | :--- | :--- |
| **上午** | **并发八股扫盲** | 1. **AQS 核心**：State 变量 + CLH 双向队列（理解 Semaphore 基础）<br>2. **锁升级**：synchronized 的 Mark Word 变化过程<br>3. **ThreadLocal 缺陷**：弱引用 Key 导致的 Value 内存泄漏 | — |
| **下午** | **项目代码串联** | 带着问题看代码：<br>1. *ScopedValue 如何解决泄漏？* → 看 `UserContext.java` 的不可变设计<br>2. *虚拟线程为何配 Semaphore？* → 看 `SeckillOrderConsumer.java` 的 `acquire()` 逻辑 | 1. `vtsl-common/context/UserContext.java`<br>2. `vtsl-common/filter/UserContextFilter.java`<br>3. `vtsl-seckill/consumer/SeckillOrderConsumer.java` |
| **晚上** | **复盘与输出** | 1. 手写 Semaphore 限流伪代码。<br>2. 准备话术：“为何用虚拟线程？”（答：IO 密集型，无切换开销，吞吐量翻倍）。 | — |
---
### 🔴 Day 3 (7/30)：JVM + MySQL + 性能调优
**目标**：深入 GC 调优，理解项目最亮点的“性能优化”数据。
| 时间段 | 学习内容 | 理论 (上午/中午) | 实战 (下午) |
| :--- | :--- | :--- | :--- |
| **上午** | **JVM/DB 八股** | 1. **G1 vs ZGC**：G1 的 Region + Remembered Set；ZGC 的染色指针 + 读屏障<br>2. **MySQL MVCC**：Undo Log 版本链 + ReadView 可见性规则 | — |
| **下午** | **项目代码串联** | 带着问题看代码：<br>1. *G1 和 ZGC 差距多大？* → 看 `02-g1-vs-zgc.md` 压测表格<br>2. *如何保证幂等？* → 看 `SeckillOrder.java` 的唯一索引 + 乐观锁 | 1. `vtsl-docs/performance/02-g1-vs-zgc.md`<br>2. `vtsl-docs/performance/03-async-shave.md` (瓶颈定位)<br>3. `vtsl-seckill/entity/SeckillOrder.java` |
| **晚上** | **复盘与输出** | 1. 整理压测数据对比表。<br>2. 准备话术：“如何优化 P99 延迟？”（答：SkyWalking 定位 -> 异步削峰 -> 本地化 Redis）。 | — |
---
### 🔴 Day 4 (7/31)：Spring 生态 + 基础设施
**目标**：串通微服务架构，搞定 Gateway 和 Kafka 可靠性。
| 时间段 | 学习内容 | 理论 (上午/中午) | 实战 (下午) |
| :--- | :--- | :--- | :--- |
| **上午** | **框架八股** | 1. **Gateway**：谓语 + 过滤器链<br>2. **Kafka**：ISR (同步副本列表) + ACK 机制<br>3. **Spring Bean**：生命周期 (扫描->实例化->属性注入->初始化) | — |
| **下午** | **项目代码串联** | 带着问题看代码：<br>1. *如何防篡改？* → 看 `SignVerifyGlobalFilter.java` 的 HMAC 签名校验<br>2. *Kafka 消息如何不丢？* → 看 `SeckillOrderConsumer.java` 的手动 ACK + 重试 | 1. `vtsl-gateway/filter/JwtAuthGlobalFilter.java`<br>2. `vtsl-gateway/filter/SignVerifyGlobalFilter.java`<br>3. `vtsl-seckill/config/KafkaConfig.java` |
| **晚上** | **全盘模拟** | 1. **画全链路架构图**：Gateway -> 秒杀服务 -> Kafka -> 消费者 -> Redis/DB。<br>2. **模拟面试**：对着架构图自讲一遍，重点强调“虚拟线程 + ZGC”的组合拳。 | — |
---
### ⚠️ 特别提醒：简历高危区（纯八股防御）
以下知识点在简历写了，但**项目中没有代码体现**，面试官极可能深挖，请务必准备标准答案：
1.  **HashMap 源码**：必问。准备：数组+链表+红黑树，扰动函数，扩容死循环(1.7)。
2.  **AQS 原理**：必问。准备：`state` 状态，`CLH` 队列，独占/共享模式。
3.  **Spring AOP**：简历写了。准备：JDK 动态代理 vs CGLIB，事务传播机制。
4.  **synchronized 锁升级**：必问。准备：偏向锁 -> 轻量级锁 -> 重量级锁的 Mark Word 变化。
    这个计划把你的简历“吃透”了，既有理论支撑，又有实战印证，非常适合冲刺阶段复习。执行时，**上午背八股，下午看代码**，效率最高。
