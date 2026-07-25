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

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->
