# 代码审查报告 — vt-seckill-shortlink（秒杀 + 短链）

> 审查日期：2026-08-05
> 审查范围：`vt-seckill-shortlink/`（Java 后端，重点 vtsl-seckill 与 vtsl-shortlink 三子模块）、`shortly/`（Go 原版对照）、`vtsl-docs/`（设计文档对照）
> 审查方式：codegraph 调用图 + 源码逐文件核对 + Go→Java 逐模块对照
> 结论：架构设计成熟、亮点突出，但存在 **2 个 P0（鉴权失效、端口冲突）**、**1 个 P0.5（热路径打 DB）** 与若干 P1/P2，均给出修复建议。

---

## 1. 项目全貌

### 1.1 模块地图（实际代码 vs 文档）

| 模块 | 实际端口 | 文档端口(01-overview) | 职责 | Dubbo |
|---|---|---|---|---|
| vtsl-gateway | 8080 | 8080 | WebFlux 网关：JWT/签名/短码校验/限流 | — |
| vtsl-user | 8081 | 8081 | 注册登录、双 Token、设备管理 | 20881 |
| vtsl-shortlink-keygenerator | **8082** | 未列 | KGS：Mongo 存 key 状态 + Redis 队列 + gRPC:50051 | — |
| vtsl-shortlink-simple | 8084 | shortlink 8084 | 旧版短链（Redis INCR+Base62，遗留） | 20884 |
| vtsl-shortlink-api | 8085 | 未列 | 新版短链：URL CRUD/重定向/Analytics/限流 | 20885 |
| vtsl-leaderboard | **8084** | 8085 | 排行榜 ZSet | 20885 |
| vtsl-websocket | 8083 | 8083 | 直播间 WS + Pub/Sub | 20883 |
| vtsl-seckill | 8090 | 8090 | 秒杀：策略模式 + Lua + Kafka | 20882 |
| vtsl-common | - | - | ScopedValue/编解码器/gRPC proto/Dubbo API | - |

**矛盾点**：① leaderboard 实际 8084，文档写 8085；② api 实际 8085（恰是文档中 leaderboard 的端口）；③ **leaderboard(8084) 与 shortlink-simple(8084) 端口冲突**。

### 1.2 Go→Java 对照

| Go（shortly/） | Java（vt-seckill-shortlink/） | 还原度 |
|---|---|---|
| shortly-api-service/ | vtsl-shortlink/vtsl-shortlink-api/ | 结构一致，两处退化（见 §4） |
| shortly-kgs-service/ | vtsl-shortlink/vtsl-shortlink-keygenerator/ | 几乎逐行还原（含阈值/Batch/长度常量） |
| proto/key.proto | vtsl-common/src/main/proto/key.proto | 一致（package key，GetKey(Empty)→KeyResponse） |
| handlers/url.go (302 跳转) | UrlController (返回 JSON) | ⚠️ 语义偏差 |

### 1.3 中间件清单核对

| 中间件 | 文档声明 | 代码实际依赖 | 缺口 |
|---|---|---|---|
| MySQL 3306 | ✅ | ✅ 所有 MVC 模块 | — |
| Redis 6379 | ✅ | ✅ 所有模块 | — |
| Redis Cluster 6381-6383 | 文档有 | 秒杀 Lua 用 `{activityId}` hash tag，兼容 Cluster | — |
| Nacos 8848 | ✅ | ✅ | — |
| Kafka 9092 | ✅ | ✅ 秒杀/短链/WS | — |
| Sentinel 8858 | ✅ | ✅ | — |
| **MongoDB 27017** | ❌ 未列 | ✅ **KGS 强依赖**（shortkeys 集合） | **部署缺口** |

---

## 2. 设计亮点（面试弹药）

1. **秒杀策略模式**：`SeckillStrategyFactory` 按 `SeckillMode` 分发 `RedisAsyncStrategy` / `RedisSyncStrategy` / `DBQueueStrategy`，同一 `placeOrder` 流程可切换"Lua+Kafka 异步落库 / Lua+同步落库 / DB 队列"，支撑压测对比。
2. **Lua 原子扣减**（`deduct_stock.lua`）：`DECR` 扣库存 + `SETEX` 一人一单去重 + 失败 `INCR` 回补，脚本内原子；key 经 `KEYS` 数组传入兼容 Cluster；`seckill.dedup-enabled` 可关闭去重做压测。
3. **Kafka 消费背压**（`SeckillOrderConsumer`）：`Semaphore(30)` 对齐 HikariCP 池 → 耗尽时阻塞 listener 线程 → poll 暂停 → Broker 降速，从根源杜绝 VT 堆积；手动 ACK + `TransactionException` 内存重试 3 次 + `DuplicateKeyException` 幂等兜底 + Redis Pub/Sub 推送秒杀结果。
4. **KGS 预生成模式**：Redis List 队列 RPOP 原子发号（天然无并发冲突）；队列低于 `QUEUE_THRESHOLD=200` 时批量补货 `BATCH_SIZE=1000`；`MAX_GENERATE_ATTEMPTS=3` 重试 + Mongo 唯一索引防重。
5. **短链二级缓存**：Caffeine L1(3s) + Redis L2(24h)；gRPC 客户端 2s deadline + `@PreDestroy` 优雅关闭。
6. **技术栈前沿**：JDK 25（ZGC + VT + ScopedValue + JEP 491）+ Boot 4.1 + Gateway 5.0（`server.webflux.routes` 新路径）+ Dubbo 3.3.6。

---

## 3. 问题清单

### 🔴 P0-1 短链 api 模块鉴权完全失效（scanBasePackages 缺失）

**位置**：`vtsl-shortlink/vtsl-shortlink-api/src/main/java/com/jolumn/vtslshortlinkapi/VtslShortlinkApiApplication.java:6`（以及 `VtslKeyGeneratorApplication.java:6`）

**现状**：裸 `@SpringBootApplication`，默认只扫 `com.jolumn.vtslshortlinkapi`。对照其余全部模块（user 扫 `com.jolumn.vtslcommon` 全树；seckill 显式列出 `filter/interceptor/exception/util/dto/annotation`），vtsl-common 下这些 `@Component` **全部不会注册**：

- `com.jolumn.vtslcommon.filter.UserContextFilter` → `X-User-Id` 头不绑定 ScopedValue
- `com.jolumn.vtslcommon.interceptor.AuthInterceptor` → `@RequireAuth` / `@RequireRole` / `@PublicApi` 不生效
- `com.jolumn.vtslcommon.exception.GlobalExceptionHandler`（`@RestControllerAdvice`）→ `BizException` 变 500 裸栈

**后果**：`UserContext.currentUserId()` 恒为 null（`UserContext.java:16` `isBound() ? get() : null`），`UrlController.requireUserId()`（`UrlController.java:93-99`）抛 401 → **`/api/v1/url/shorten`、`/api/v1/url/**`、`/analytics/**` 全部 401**；`/api/v1/url/redirect/**` 是 `@PublicApi` 但拦截器未注册（不拦），反而是唯一"可用"接口。**api 模块当前实际不可用**。

> 这是 AGENTS.md《ScopedValue 用户上下文改造》一节记录的同类坑在新模块的重演，且本次是"整包漏扫"（旧坑只是漏 `.filter` 子包）。

**修复建议**：照抄 seckill 模板：

```java
@SpringBootApplication(scanBasePackages = {
        "com.jolumn.vtslshortlinkapi",
        "com.jolumn.vtslcommon.filter",
        "com.jolumn.vtslcommon.interceptor",
        "com.jolumn.vtslcommon.exception",
        "com.jolumn.vtslcommon.annotation",
        "com.jolumn.vtslcommon.context",
        "com.jolumn.vtslcommon.util",
        "com.jolumn.vtslcommon.dto"
})
```

keygenerator 同样补 `com.jolumn.vtslcommon.exception`（其为纯 gRPC 服务，无 Servlet Filter 需求，可不扫 filter/interceptor）。

### 🔴 P0-2 端口冲突：vtsl-leaderboard(8084) == vtsl-shortlink-simple(8084)

**位置**：`vtsl-leaderboard/src/main/resources/application.yml:5`、`vtsl-shortlink/vtsl-shortlink-simple/src/main/resources/application.yml:5`

**现状**：两服务均 `server.port: 8084`，且都注册 Nacos，同时启动必冲突。文档 `01-overview.md` 声称 leaderboard 8085 / shortlink 8084——代码与文档互相矛盾。

**修复建议**：leaderboard 改为 8085（对齐文档），或 simple 改为文档之外的端口。注意 api 模块已是 8085，若 leaderboard 改 8085 需同时确认 simple 的去留（见 P2-3）。

### 🔴 P0-3 短链热路径缓存命中仍打 DB（对 Go 原版的退化）

**位置**：`vtsl-shortlink-api/.../service/UrlService.java:125-142`（`redirect()`）

```java
String originalUrl = getFromCache(shortKey);      // 缓存只存 originalUrl 字符串
if (originalUrl == null) {
    Url url = urlRepository.findByShortKeyAndDeletedAtIsNull(shortKey)...;  // miss 查库
    ...
} else {  // ← cache HIT 仍查库
    Url url = urlRepository.findByShortKeyAndDeletedAtIsNull(shortKey).orElse(null);
    if (url != null) analyticsAsyncService.record(url.getId(), ...);
}
```

**根因**：为拿 `url.getId()` 记 analytics，缓存命中时仍执行一次 MySQL 查询。Go 原版缓存的是完整 DTO（`handlers/url.go:280` `go storeAnalytics(ctx, cachedDTO.ID)`），**Java 版缓存值退化成了纯字符串**，直接击穿"缓存拦截热点消除网络 IO"的设计目标。日读 100 万场景 = 每次跳转一次 DB 查询。

**修复建议**：缓存 value 改为 `id|originalUrl`（`CACHE_PREFIX` 存 `"<id>:<url>"`），或 Caffeine 直接缓存 `Url` 实体；miss 时组装，hit 时零 DB 访问。

### 🟡 P1-1 KGS getKey() 双写不一致：脏 key 死循环（Go 原版同样存在）

**位置**：`vtsl-shortlink-keygenerator/.../service/KeyService.java:46-55`

**现状**：`rightPop` 出队 → Mongo `updateFirst` 标记 USED → `modifiedCount==0` 则 push 回队列。若 Mongo 写"超时但实际成功"，队列中出现**已 USED 的脏 key**，此后每次取到它都 `modifiedCount==0` → 再 push 回 → **永久死循环 + 队列污染**。Go 版 `key_service.go:63-65` 逻辑相同。

**修复建议**：`updateFirst` 加 CAS 条件（`Criteria.where("key").is(key).and("status").is("available")`），失败即丢弃该 key（或补货），不再 push 回；或改为队列内直接置标记。面试被问"KGS 如何保证不重复发号"时，这是必须能自圆其说的点。

### 🟡 P1-2 KGS 依赖 MongoDB，部署清单无 Mongo

**位置**：`vtsl-shortlink-keygenerator/.../application.yml:11-13`；`vtsl-docs/architecture/01-overview.md` §中间件

**现状**：keygenerator 强依赖 `mongodb://192.168.147.132:27017`（库 `shortly`，集合 `shortkeys`），但 VM 中间件清单只有 Redis/MySQL/Nacos/Kafka/Sentinel。KGS 部署即缺依赖；且项目其他模块统一用 MySQL，短链 key 状态存 Mongo 属"忠实还原 Go 版"的架构孤立点。

**修复建议**（择一）：① VM 补装 MongoDB（最快，保持还原度）；② key 状态表迁移 MySQL（`shortkeys(key VARCHAR(64) UNIQUE, status, created_at)`），删除 spring-data-mongodb 依赖，与全项目存储统一——面试时可主动讲"为什么从 Mongo 迁 MySQL：减少中间件种类、KGS 是唯一 Mongo 消费者"。

### 🟡 P1-3 create() 自定义短码并发竞态

**位置**：`UrlService.java:65-74`

**现状**：`existsByShortKeyAndDeletedAtIsNull` 检查 + `save` 非原子；软删除（`deleted_at`）下 MySQL 无法建部分唯一索引，并发提交相同自定义短码可同时通过检查 → 两行同 key 数据。KGS 生成的码有 Mongo 唯一索引兜底，**仅自定义短码场景受影响**。

**修复建议**：加"唯一键冗余列"（如 `short_key_unique` 存 `deleted_at IS NULL ? short_key : short_key + '_del_' + id`）配合唯一索引；或 DB 层 `INSERT ... ON DUPLICATE KEY` 后重查。低频场景也可接受现状并在面试中说明取舍。

### 🟡 P1-4 秒杀 Consumer 活动缺失直接 ack 丢单

**位置**：`vtsl-seckill/.../consumer/SeckillOrderConsumer.java:66-71`

**现状**：`cacheService.getActivity(activityId)` 为 null（缓存过期/活动被删）时直接 `ack.acknowledge()`，该单永久丢失且无告警。活动被删属低概率，但"已扣库存未落单"是资金类问题，应至少记 error 日志告警。

### 🟢 P2 杂项

| # | 位置 | 问题 |
|---|---|---|
| P2-1 | `01-overview.md` 端口表 | leaderboard 8085 / shortlink 8084 与代码不符（见 P0-2） |
| P2-2 | `vtsl-shortlink-simple/README.md:115` | 声称端口 8091，配置实际 8084 |
| P2-3 | gateway `application.yml:82-92` | `/s/**` → simple、`/api/v1/**` → api 两套短链并存；若 api 为正式版，simple 应下线（端口冲突一并解决） |
| P2-4 | `RedisAsyncStrategy.java:31` | Kafka 消息无 partition key（`userId:activityId:orderNo` 作 value），同用户多单跨分区可能乱序；一人一单 + DuplicateKey 幂等兜底下影响小，面试需能解释 |
| P2-5 | `api/application.yml:64-69` | `shortly.jwt.secret` / `expiration-hours` 配置存在但 api 模块无 JWT 代码（Go 版迁移残留），建议删除避免误读 |
| P2-6 | `SeckillOrderConsumer` | 内存 `retryCounts` 无清理策略（重试成功后未 remove），长时间运行有泄漏风险（低） |

---

## 4. Go→Java 对照偏差（重点，面试必问）

| # | Go 原版 | Java 版 | 偏差性质 |
|---|---|---|---|
| 1 | `ctx.Redirect(http.StatusFound, url)`（**302 跳转**，`handlers/url.go:282/312`） | `Result<String>` 返回 JSON（`UrlController.java:77-82`） | **语义偏差**：短链标准语义是浏览器 302；JSON 返回需前端自行跳转，"短链"名不副实。若为对接统一 `Result` 格式的有意设计，需在文档说明 |
| 2 | 缓存存完整 DTO（`cachedDTO.ID`） | 缓存只存 `originalUrl` 字符串 → hit 打 DB | **退化**（见 P0-3） |
| 3 | Go KGS 无并发锁，同 Java（RPOP 原子） | 同 | 一致 |
| 4 | 队列名 `shortly-kgs-redis-queue` | 同名（`KgsConstants.java:7`） | 一致 |
| 5 | Mongo `shortkeys` 集合 | 同名（MongoTemplate collection `"shortkeys"`） | 一致 |

---

## 5. 修复优先级建议

| 批次 | 项 | 工作量 | 说明 |
|---|---|---|---|
| **第一批（上线阻塞）** | P0-1 scanBasePackages ×2 | 10min | 抄 seckill 模板，改完 api 模块即可用 |
| | P0-2 端口冲突 | 5min | 改 leaderboard → 8085 并同步文档 |
| | P0-3 redirect 缓存带 ID | 30min | 缓存格式改 `id|url`，热路径零 DB |
| **第二批（健壮性）** | P1-1 KGS CAS | 30min | 消灭脏 key 死循环 |
| | P1-2 Mongo 决策 | 1~2h | 补装 Mongo 或迁 MySQL |
| | P1-4 Consumer 告警 | 10min | null 时 error 日志 + 可选补偿 |
| **第三批（收尾）** | P1-3 短码唯一性、P2 杂项 | 按需 | 面试前把 P2 文档对齐即可 |

---

## 附：审查方法说明

- 主链路由 codegraph（`codegraph explore`）定位：KGS `getKey` → gRPC → api `UrlService.create/redirect`；秒杀 `SeckillService.placeOrder` → 策略工厂 → `StockService.deduct` → Lua → `SeckillOrderConsumer`
- 文件行号均为审查当日磁盘实际内容
- 编译验证：`mvn -o compile -pl vtsl-shortlink/vtsl-shortlink-api -am` → **BUILD SUCCESS**（vtsl-common + api 离线编译通过）。注意 P0-1（scanBasePackages）属运行时行为，编译不暴露，正因如此它才危险
- 未运行测试（`mvn test` 需依赖中间件/网络，本机未验证；api/keygenerator 各有 1 个单测：`UserAgentParserTest`、`Base62UtilTest`）
