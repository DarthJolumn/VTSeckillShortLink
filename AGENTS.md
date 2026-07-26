# LiveMall 知识库

## JDK 25 + Spring Boot 4.1 升级要点（2026-07-18 审计）

详见 `md/docs/7-杂七杂八/升级报告-JDK25-Boot4.1.md`

### 已知问题

#### JJWT + Gson：claims.get("role", Integer.class) 抛异常

**根因**：升级报告 §4.2 将 `jjwt-jackson` 换成了 `jjwt-gson`。Gson 把 JWT payload 中的所有数字解析为 `Double`，而 JJWT 内置类型转换不支持 `Double → Integer`。

**排查方法**：Gateway 日志出现以下错误即为该问题：
```
Cannot convert existing claim value of type 'class java.lang.Double' to desired type 'class java.lang.Integer'
```

**修复方式**：所有从 JWT claims 读取 `Integer` 类型的地方，将：
```java
Integer role = claims.get("role", Integer.class);
```
改为：
```java
Integer role = ((Number) claims.get("role")).intValue();
```

受影响的文件（共 3 处）：
- `livemall-gateway/.../JwtAuthGlobalFilter.java:82`
- `livemall-websocket/.../LiveWebSocket.java:43`
- `livemall-websocket/.../LiveWebSocket.java:135`

#### Spring Cloud Gateway 5.0+ 路由配置路径变更

升级报告 §4.8：Gateway 5.0.2 路由配置路径从 `spring.cloud.gateway.routes` 变为 `spring.cloud.gateway.server.webflux.routes`。

#### Spring Boot 4.x 下 Jackson 包名变更

`com.fasterxml.jackson` → `tools.jackson`（升级报告 §3.3）

#### Gateway 白名单模式：Ant `*` 不匹配多级路径

`application.yml` 中 `gateway.auth.public-get-paths` 使用 Ant 路径匹配器。**`*` 只匹配一级路径**，`/live/room/*` 不匹配 `/live/rooms`。

如果用户反映"匿名用户进直播大厅看不到房间列表"，优先排查此处。

修复方式：将需要公开的路径改为 `**` 模式，例如 `/live/**`。

## 秒杀模块审计修复记录（2026-07-25）

### 已修复问题

#### P0-2: 取消订单分布式事务不一致

**根因**：`cancelOrder()` 和 `TimeoutCancelScheduler` 先 Redis refund 后 DB save，DB 失败时库存已回补但订单未取消 → 库存泄漏。

**修复**：调换顺序为 DB 先（`@Version` CAS 防并发）、Redis 后（Lua EXISTS 幂等）。新增 `ReconciliationScheduler` 每 5 分钟对账补偿 Redis refund 失败的订单。

#### P1-3: TimeoutCancelScheduler 无分页

**根因**：`findByStatusAndCreatedAtBefore` 无 LIMIT，积压订单全部加载 → OOM 风险。

**修复**：新增 `Pageable` 重载方法，读取 `timeout-scan-batch: 500` 配置，`PageRequest.of(0, batchSize)` 限制每批 500 条。

#### P1-5: deduct() Redis 故障误导"库存不足"

**根因**：`result == null` 时返回 `-2`（库存不足），用户看到错误提示。

**修复**：`null` → 抛 RuntimeException → `SeckillService.placeOrder()` 捕获后抛 `BizException(503, "系统繁忙")`。区分系统错误（503）和业务错误（400）。

#### P2-6: BloomFilter 多实例不一致

**根因**：3 个实例各自维护独立 BF，新活动上架后其他实例 BF 不含该活动 → 误杀 404。

**修复**：注释掉 BF 逻辑，活动存在性由 Caffeine `getActivity()` 精确判存替代（活动量级小，不需要概率数据结构）。

#### P2-7: soldOutCache 多实例不一致

**根因**：1s TTL 的本地售罄缓存，其他实例 1s 内不知道已售罄 → 多笔无效 Redis deduct。

**修复**：删除 `soldOutCache`、`markSoldOut()`、`isSoldOut()`、`markInStock()`。售罄判断由 Redis Lua `deduct` 返回 `-2` 兜底。

#### P3-8: Controller 注入风格不一致

**修复**：`@Autowired` 字段注入统一改为构造注入（`final` 字段 + 构造函数参数）。

#### P3-10: timeout-scan-batch 有配无码

**修复**：随 P1-3 一起实现，`timeout-scan-batch: 500` 配置已生效。

#### P3-11: SeckillDataWarmup 重复查询（N+1）

**根因**：先 `findByStatusOrderByStartTimeAsc(1)` 查全部活动，再逐个 `findById()` → 多 N 次查询。

**修复**：`ActivityCacheService` 新增 `put(id, activity)` 方法，Warmup 直接用已有对象写入 Caffeine。同时移除 `bloomFilter.rebuild()` 调用。

#### P3-12: gRPC ManagedChannel 无优雅关闭

**修复**：`GrpcClientConfig` 添加 `@PreDestroy shutdown()`：先 `channel.shutdown()`，等 5s，超时 `shutdownNow()`。

### 待处理

#### P0-1: refundOrder() 不退库存

无支付模块，退款流程不完整（退钱 + 退货缺一不可）。等支付模块上线后补全 `stockService.refund()` 调用。

### 设计决策

#### Kafka 消费端 Semaphore 背压（非 bug）

`SeckillOrderConsumer` 的 `SEM.acquire()` 阻塞 Kafka listener 线程是**有意设计**：信号量耗尽 → 阻塞 poll → Broker 感知消费慢 → 降速。从根源杜绝 VT 堆积引发的连接耗尽。上限对齐 HikariCP 池大小（30）。

#### Redis Cluster 配置

两个 seckill 模块的 `application.yml` 均已配置 `read-from: MASTER`（避免 replica lag）。seckill-single 的 `deduct_stock_single.lua` 和 `StockService` 的 key 已加 `{activityId}` hash tag（解决 CROSSSLOT）。

## 项目面试成熟度评估（2026-07-25）

### 已完成维度

| 维度 | 实现 | 面试话术 |
|------|------|---------|
| **限流** | Sentinel Dashboard 设规则 | 「Gateway 层 Sentinel 令牌桶限流，QPS 阈值+熔断降级」 |
| **可观测性** | SkyWalking 链路追踪 | 「SkyWalking 定位跨服务慢调用，拓扑图分析依赖」 |
| **GC 调优** | G1 → ZGC 对比压测 | 「同口径 450 并发，G1 GC 耗时 856ms → ZGC 3ms，P99 降 32%」 |
| **瓶颈定位** | SkyWalking 定位 Kafka Producer 同步发送 | 「SW 追踪最长 3342ms，异步削峰后 300 并发 TPS 1513 / P99 92ms」 |
| **高并发消费** | Semaphore 背压 + 手动 ACK | 「信号量耗尽阻塞 poll → Broker 降速，手动 ACK + 幂等去重 + 内存分级重试，100% 入库成功」 |
| **分布式事务** | DB 先 → Redis 后 + 对账 | 「@Version CAS 防并发，ReconciliationScheduler 每 5 分钟补偿」 |
| **多实例一致性** | 移除 BF + soldOutCache | 「活动量级小，Caffeine 精确判存；售罄由 Lua deduct 兜底」 |
| **WebSocket 推送** | Redis Pub/Sub 多实例推送 | 「替代 gRPC 单点推送，所有实例收到后广播给本地连接」 |
| **集成测试** | JMeter 全链路压测 | 「注册+秒杀混合场景，10w 订单零丢失」 |

### 面试回答模板

**Q: 秒杀系统怎么保证不超卖？**
> Redis Lua 原子扣减（扣库存+判重+设标记），Lua 脚本内完成，无并发问题。Lua 返回 -2 表示库存不足。

**Q: 下单链路是怎样的？**
> 前端 → Gateway 限流 → SeckillService.placeOrder()（Caffeine 判存 + Redis Lua 扣减）→ Kafka → Consumer（Semaphore 背压 + 手动 ACK）→ DB 入库 → Redis Pub/Sub → WebSocket 推送结果

**Q: Kafka 消费失败怎么办？**
> 手动 ACK：消费成功才 ack。TransactionException 内存重试 3 次，超过后 ack 放弃（幂等兜底，DuplicateKey 不会重复入库）。

**Q: 如何防止虚拟线程堆积？**
> Semaphore(30) 上限对齐 HikariCP 池大小。信号量耗尽 → 阻塞 listener 线程 → poll 暂停 → Broker 感知消费慢 → 降速。从根源杜绝 VT 堆积。

**Q: 限流用的什么算法？**
> 令牌桶（Token Bucket）：固定速率放令牌，请求取令牌，桶空则拒绝。允许突发流量（攒令牌后短时间放行一批），适合秒杀场景。漏桶（Leaky Bucket）强制匀速输出，不适合突发。

**Q: 分布式事务怎么处理的？**
> 取消订单：DB 先（@Version CAS 防并发）、Redis 后（Lua EXISTS 幂等回补）。如果 Redis 回补失败，ReconciliationScheduler 每 5 分钟扫描 status=2 的订单，检查 ordered key 是否还在 Redis，在则补偿回补。

### 待处理

- **P0-1**: refundOrder() 不退库存 — 无支付模块，等支付上线后补全

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->
