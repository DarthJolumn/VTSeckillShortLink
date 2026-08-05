# Redis 实战用法全梳理（面试专题）

> 六大类型 + Lua + key 设计，每个都有真实代码位置。
> 配套 `01-八股考点与项目代码对照.md` 的 Redis 章节。

---

## 一、总览表

| 类型 | 项目场景 | key 示例 | 核心代码 |
|---|---|---|---|
| **String** | 秒杀库存 / 一人一单 / 幂等 / Nonce / 分布式锁 / 发号器 / 用户 active token / 商品去重 | `stock:{id}`、`ordered:`、`idempotency:`、`nonce:`、`lock:` | `StockService`、`IdempotencyService`、`SignVerifyGlobalFilter`、`ReconciliationScheduler` |
| **List** | KGS 预生成短码队列（LPUSH/RPOP 原子发号） | `shortly-kgs-redis-queue` | `KeyGenerator:43`、`KeyService` |
| **Hash** | 短链 L2 多字段缓存 + 点击计数（HINCRBY） | `s:code:{shortCode}` | `ShortLinkCache:53-126` |
| **Set** | ⚠️ 新项目**无使用点**（老 livemall 有 `device_sessions:{userId}`，重构后改为 String 方案） | — | 见 §4 面试应对 |
| **ZSet** | 排行榜实时 TopN / 个人排名 / 互动加分 | `leaderboard:{roomId}` | `LeaderboardServiceImpl:37,46,66,69` |
| **Pub/Sub** | 秒杀结果推送（Kafka 消费端 → Redis → WS 广播） | `ws:push:seckill-result` | `SeckillOrderConsumer:90`、`RedisPushSubscriber:33-43` |
| **Lua** | 原子扣库存 / 幂等回补 / 限流计数 | — | `deduct_stock.lua`、`refund_stock.lua`、`RedisFixedWindowLimiter` |

---

## 二、逐个类型：怎么用 + 为什么用它

### 1. String —— 最基础也最万能

| 场景 | key 设计 | 为什么用 String |
|---|---|---|
| 秒杀库存 | `stock:{activityId}`（`{}` 是 **Cluster hash tag**，保证同一活动 key 同槽） | 单值 + 数值操作（DECR/INCR） |
| 一人一单 | `ordered:{activityId}:{userId}`，`SETEX 86400` | 存在性判断（Lua 内 GET），TTL 防止永久膨胀 |
| 接口幂等 | `idempotency:{key}`，`setIfAbsent` 5min | **setIfAbsent 原子** = 天然分布式锁语义 |
| 网关防重放 | `nonce:{nonce}`，`setIfAbsent` 60s | 同幂等，一次有效 |
| 分布式锁 | `lock:reconciliation:cancelled`，`setIfAbsent` 300s | 多实例对账任务互斥（见 §5） |
| 发号器 | `shortly:seq`，`opsForValue().increment()` | INCR 原子自增（simple 版短码 ID） |
| 用户 active token | `active_token:{userId}:{deviceId}` | 登录态 + 设备维度踢下线（String 方案替代老版 Set） |
| 商品发布去重 | `dedupKey`，`setIfAbsent` 24h | 幂等发布 |
| 短链 L2 缓存 | `url:{shortKey}` = `{urlId}|{url}` | 单值缓存（api 新版，见审查报告 P0-3 修复） |

### 2. List —— KGS 预生成发号队列（项目独特点）

```
KeyGenerator.generateBatch：批量生成 1000 个随机码
  → Mongo insert（available 账本）
  → Redis LPUSH 入队（一次 pushAll）

KeyService.getKey：RPOP 出队 → Mongo CAS 标记 used → 返回
  → 队列低于阈值 200 → 再批量补货
```

**为什么用 List**：`RPOP` 原子出队，天然无并发竞争（比"先查后删"安全）；预生成模式让发号延迟降到 O(1)；队列长度 = 实时水位，驱动补货。

**面试扩展**：这是经典 **Key Generation Service（KGS）预生成模式**——发号器只做"生成+入队"，业务侧"取号"零数据库写。对比：DB 自增 ID（单点）、雪花 ID（时钟依赖）、美团 Leaf（号段模式）——KGS 是"号段模式"的另一种形态（内存/Redis 队列替代 DB 段）。

### 3. Hash —— 短链 L2 多字段

`ShortLinkCache` 用 Hash 存一个短码的完整属性：
```
s:code:Pabc123 → { url, productId, expireAt, clickCount, status }
```
操作：`opsForHash().get/putAll/entries/increment(FIELD_CLICK_COUNT, 1)`

**为什么用 Hash**：
- 一个短码 5 个属性，一次网络请求全部拿到（对比 String 拼接/多个 key）
- **字段级更新**：`HINCRBY clickCount` 原子自增，不覆盖其他字段（String 整串覆盖会丢并发）
- 空间省：Hash 小对象用 ziplist 压缩存储

### 4. Set —— 新项目没用到，怎么应对面试

**事实**：`vtsl-seckill-shortlink` 里 Redis Set **无使用点**。老版 LiveMall 用过 `device_sessions:{userId}`（登录设备去重，SADD/SREM/SMEMBERS），vtsl 重构后改为 `active_token:{userId}:{deviceId}` String 方案。

**面试应对**（诚实 + 会原理）：
> "新版本里设备会话改用了 active_token String 方案，所以 Set 没有落地点。但 Set 的去重/并交差语义我熟——比如**共同关注**（两个 Set 交集）、**随机抽奖**（SPOP）、**标签体系**（SISMEMBER 常数级判断）。如果这个项目需要'某用户的所有设备'这种集合查询，我会用 Set 存 `device_sessions:{userId}`。"

### 5. ZSet —— 排行榜（数据类型的"主角"）

`LeaderboardServiceImpl` 四个操作全覆盖 ZSet 能力：
| 操作 | 方法 | 语义 |
|---|---|---|
| 加分 | `opsForZSet().incrementScore(key, userId, weight)` | 互动事件权重：WATCH 0.3 / LIKE 0.5 / COMMENT 1.0 / SHARE 2.0 / GIFT 5.0 |
| TopN | `reverseRangeWithScores(key, 0, n-1)` | 实时榜单 |
| 个人分数 | `score(key, member)` | 排名详情 |
| 个人排名 | `reverseRank(key, member)` | O(logN) 跳表定位 |

**为什么用 ZSet**：分数有序天然支持 TopN；跳表 O(logN) 插入 + 范围查询；对比 MySQL `ORDER BY score LIMIT` 是 O(N) 全表排序。项目还配了 `@Scheduled` 5min 快照落库（历史榜单用 MySQL 兜底）。

### 6. Pub/Sub —— 秒杀结果推送链路

```
SeckillOrderConsumer（Kafka 落库成功）
  → redisTemplate.convertAndSend("ws:push:seckill-result", JSON)
  → 所有 websocket 实例的 RedisMessageListenerContainer 收到（PatternTopic 订阅）
  → 广播给本实例的本地 WS 连接
```

**为什么用 Pub/Sub**：多实例广播——替代"单点 gRPC 推送"（老方案 WS 实例只向调它的那个秒杀实例要结果，其他实例收不到）；Redis Pub/Sub 让"任意秒杀实例 → 所有 WS 实例"解耦。

**面试扩展（可以主动讲的取舍）**：Pub/Sub 是**即发即弃**（订阅者掉线就丢消息），所以秒杀结果的主链路是 Kafka（可靠），Redis Pub/Sub 只是"通知 WS 实例"的旁路；如果要求可靠投递，应换 Redis Stream（消费者组 + 消息持久化）。

### 7. Lua —— 原子性三件套

**① 扣库存 `deduct_stock.lua`**（秒杀不超卖核心）：
```lua
-- KEYS[1]=stock:{id}  KEYS[2]=ordered:{id}:{uid}  ARGV[1]=是否去重
EXISTS stock → 不存在返回 -3（未上架）
GET ordered → 已存在返回 -1（重复下单）
DECR stock → >=0 则 SETEX ordered 返回 200；否则 INCR 回补返回 -2
```

**② 幂等回补 `refund_stock.lua`**：
```lua
EXISTS ordered → INCR stock + DEL ordered（只补一次，防并发重复回补）
```

**③ 限流**（api 的 `RedisFixedWindowLimiter` 与 simple 的 `RateLimitService` 同款）：
```lua
GET key → 计数 >= limit 返回 0（拒绝）
INCR + 首次 EXPIRE window
```

**为什么用 Lua**：多个 Redis 命令打包**原子执行**（单线程执行脚本），免事务（MULTI/EXEC 不可回滚）、免竞态窗口。Cluster 下多 key 脚本要求同槽 → 秒杀 key 用 `{activityId}` hash tag。

> ⚠️ 诚实备注：simple 模块 `RateLimitService` 注释写"滑动窗口"，实现是**固定窗口**（INCR + EXPIRE，窗口内累计、到期重置）。两种限流都有缺陷可讲：固定窗口临界双倍流量（窗口交界瞬间可打 2 倍），滑动窗口（ZSet 时间戳）精确但多占内存。面试被问可直接说"项目用的是固定窗口 + Lua 原子，知道滑动窗口的实现和取舍"。

---

## 三、key 设计规范（面试加分点）

```
idempotency:{key}        幂等（SETNX + 5min TTL）
nonce:{nonce}            签名防重放（SETNX + 60s TTL）
stock:{activityId}       库存（{} hash tag，Cluster 同槽）
ordered:{activityId}:{userId}  一人一单（SETEX 24h）
lock:reconciliation:cancelled  对账分布式锁（SETNX + 300s TTL）
s:code:{shortCode}       短链 Hash（多字段）
leaderboard:{roomId}     ZSet 排行榜
ws:push:seckill-result   Pub/Sub 频道
shortly-kgs-redis-queue  KGS 发号队列（List）
active_token:{userId}:{deviceId}  用户登录态
url:{shortKey}          短链 String 缓存（api 新版）
```

规律：`业务:对象:维度` 冒号分层；该带 TTL 的绝不裸奔（幂等 300s / nonce 60s / ordered 24h / 锁 300s）；Cluster 跨 key 脚本必须 hash tag。

---

## 四、分布式锁（可主动展开）

`ReconciliationScheduler:119-120`：
```java
redisTemplate.opsForValue().setIfAbsent("lock:reconciliation:cancelled", "1", 300, SECONDS)
```
- 多实例 @Scheduled 对账任务互斥，抢不到锁就跳过本轮
- **简易版锁**：SETNX + TTL；缺点——不可重入、无看门狗续期、持有者崩溃锁等 TTL 自然过期
- 面试对比：Redisson 分布式锁（可重入 + 看门狗 30s 自动续期 + Lua 解锁校验 owner）；项目选简易版因为**对账任务幂等**（重复执行无害），极端锁过期最多重复扫描，代价可接受——"能用简单方案就不引重依赖"是很好的回答姿态

---

## 五、一句话总结（面试开场）

> "项目 Redis 基本把类型用全了：**String** 管库存/幂等/分布式锁/登录态，**List** 做 KGS 预生成发号队列，**Hash** 存短链多字段并原子计点击，**ZSet** 支撑直播排行榜 TopN，**Pub/Sub** 广播秒杀结果，**Lua** 脚本保证扣库存/回补/限流的原子性。6 种类型 6 个真实场景，外加 key 命名规范和 Cluster hash tag 的实战经验。"
