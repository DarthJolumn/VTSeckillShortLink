# LiveMall Redis 设计文档

> 全局视角，覆盖用户服务、网关、秒杀、WebSocket、排行榜

---

## 一、Key 全景图

### 按模块分类

#### 用户服务（已有）

| Key | 类型 | TTL | 淘汰优先级 | 说明 |
|-----|------|-----|-----------|------|
| `refresh:{token}` | String | 7d | **低** | 存 `userId:role:deviceId`，登录态核心 |
| `device_sessions:{userId}` | Set | 7d | **低** | 存设备 ID 列表 |
| `idempotency:register:{key}` | String | 5min | **高** | 注册幂等 |
| `idempotency:login:{key}` | String | 5min | **高** | 登录幂等缓存（LoginResponse JSON） |

#### 网关

| Key | 类型 | TTL | 淘汰优先级 | 说明 |
|-----|------|-----|-----------|------|
| `nonce:{nonce}` | String | 60s | **高** | 签名防重放 |

#### 秒杀服务（待开发，按设计文档）

| Key | 类型 | TTL | 淘汰优先级 | 说明 |
|-----|------|-----|-----------|------|
| `idempotency:seckill:{key}` | String | 5min | **高** | 创建活动幂等 |
| `stock:shard:{activityId}:{shard}` | String (int) | 无 TTL | **不淘汰** | 库存分片计数器，核心数据 |
| `ordered:{activityId}:{userId}` | String | 24h | **中** | 防重复抢购标记 |
| `activity:online:{activityId}` | String | 24h | **低** | 活动已上架标记 |
| `order:lock:{orderNo}` | String | 10s | **高** | 回补库存临时锁 |
| `activity:cache:{activityId}` | String (JSON) | 活动结束后删除 | **低** | 活动信息缓存 |

#### WebSocket 服务（待开发）

| Key | 类型 | TTL | 淘汰优先级 | 说明 |
|-----|------|-----|-----------|------|
| `ws:route:{userId}` | String | 会话期 | **低** | WebSocket 节点路由信息 |
| `ws:offline:msg:{userId}` | List | 5min | **高** | 离线消息暂存 |
| `ws:room:{roomId}:online` | Set | 直播期 | **中** | 直播间在线用户 |

#### 排行榜服务（待开发）

| Key | 类型 | TTL | 淘汰优先级 | 说明 |
|-----|------|-----|-----------|------|
| `rank:gift:live` | ZSet | 无 TTL | **不淘汰** | 直播间礼物实时排行 |
| `rank:gift:history:{date}` | ZSet | 30d | **中** | 历史排行快照 |
| `rank:snapshot:lock` | String | 10s | **高** | 定时快照分布式锁 |

---

## 二、淘汰策略分层设计

```
┌─────────────────────────────────────────────────────────┐
│  不淘汰区（noeviction 行为）                              │
│  stock:shard:*  rank:gift:live                          │
│  "丢了数据=业务崩了，宁可 OOM 告警也不能丢"                │
├─────────────────────────────────────────────────────────┤
│  low-priority 淘汰区（volatile-ttl）                     │
│  refresh:* device_sessions:* activity:online:*            │
│  ws:route:* activity:cache:*                            │
│  "丢了影响用户体验但可恢复"                                │
├─────────────────────────────────────────────────────────┤
│  high-priority 淘汰区（volatile-ttl 优先淘汰）            │
│  idempotency:* nonce:* order:lock:* ws:offline:msg:*     │
│  rank:snapshot:lock                                     │
│  "丢了完全可接受，业务降级而已"                            │
└─────────────────────────────────────────────────────────┘
```

**核心原则**：所有 key 都设 TTL，用 `volatile-ttl` 策略。TTL 本身就表达了数据重要性——TTL 越短的越优先被淘汰。

**特殊处理**：`stock:shard:*` 和 `rank:gift:live` 不设 TTL，`volatile-ttl` 不会淘汰它们（策略只作用于有 TTL 的 key）。它们靠业务逻辑（活动下架/结算）主动删除。

---

## 三、Hash vs String 决策

| 数据 | 结构 | 理由 |
|------|------|------|
| `refresh:{token}` | String | 独立 key，独立 TTL（7d），独立访问 |
| `device_sessions:{userId}` | Set | 需要 SMEMBERS/ SADD/ SREM 集合操作 |
| `stock:shard:{activityId}:{shard}` | String (int) | 独立 key，Lua 脚本原子 DECR/INCR |
| `ordered:{activityId}:{userId}` | String | 独立 key，独立 TTL（24h），Lua 原子操作 |
| `activity:cache:{activityId}` | **可用 Hash** | 同一活动的多个字段（title, price, stock 等）→ 省 key 个数 × dictEntry 开销 |

**唯一推荐改用 Hash 的场景：活动缓存**。活动的 title、price、startTime、endTime 等属于同一实体，一起读写，同一生命周期。

```
# 当前设计（String）
activity:cache:123:title       → "iPhone 15"
activity:cache:123:price       → "5999"
activity:cache:123:startTime   → "2026-07-20 10:00"

# 优化后（Hash）
activity:cache:123  →  { title: "iPhone 15", price: "5999", startTime: "..." }
                     HSET activity:cache:123 title "iPhone 15" price "5999" ...
                     节省 2 个 key 的 dictEntry 开销
```

---

## 四、Key 命名规范

```
{业务域}:{实体}:{主键}:{属性}
```

| Key | 是否符合规范 | 说明 |
|-----|-------------|------|
| `refresh:{token}` | ✅ | 业务域=refresh，主键=token |
| `device_sessions:{userId}` | ✅ | 业务域=device_sessions，主键=userId |
| `stock:shard:{activityId}:{shard}` | ✅ | 业务域=stock，实体=shard |
| `ordered:{activityId}:{userId}` | ✅ | 业务域=ordered |
| `activity:online:{activityId}` | ✅ | 业务域=activity，属性=online |

**反例**：`seckill_stock_123_0` → 应改为 `stock:shard:123:0`

---

## 五、集群 Hash Tag 规划

如果未来上 Redis Cluster，需要保证 Lua 脚本涉及的 key 在同一节点：

| Lua 脚本 | 涉及 Key | Hash Tag |
|----------|---------|----------|
| 库存扣减 | `ordered:{activityId}:{userId}` + `stock:shard:{activityId}:{shard}` | `{activityId}` |
| 库存回补 | `order:lock:{orderNo}` + `stock:shard:{activityId}:{shard}` + `ordered:{activityId}:{userId}` | `{activityId}` |

**改造方案**（从开发阶段就用 hash tag，避免后期改 key 名）：

```
stock:shard:{activityId}:{shard}     →  stock:shard:{{activityId}}:{shard}
ordered:{activityId}:{userId}        →  ordered:{{activityId}}:{userId}
order:lock:{orderNo}                 →  order:lock:{{activityId}}:{orderNo}
```

`{activityId}` 作为 hash tag，同一活动的库存分片、购买记录、回补锁都在同一节点，Lua 脚本可以原子执行。

---

## 六、主从与高可用

### 读写比例分析

| 模块 | 主要读操作 | 主要写操作 | 读写比 | 主从收益 |
|------|-----------|-----------|--------|---------|
| **用户服务** | SMEMBERS 查设备、GET refresh | SET/SADD 登录、GETDEL 退出 | 4:6 写多 | **低** — 读写均衡，分离收益不大 |
| **秒杀服务** | GET activity cache | **Lua 脚本**（DECR 库存、SETEX ordered） | 3:7 写多 | **低** — Lua 脚本走主库，从库帮不上 |
| **排行榜** | ZRANGE topN、ZRANK 个人排名 | ZINCRBY 加分 | 9:1 读多 | **高** — 大量排名查询可走从库 |
| **网关** | 无 | SETNX nonce | 0:10 纯写 | **无** |

### 结论

```
本项目核心路径（秒杀、登录）都是写密集型，主从读写分离的收益很有限。
唯一收益点：排行榜（读多写少）——但排行榜 QPS 不高（<1000），
单节点完全够用，不需要从库分担。

┌─────────────────────────────────────────────────────┐
│                                                     │
│   主从的真正价值不在「读写分离」，在「高可用」。        │
│                                                     │
│   主库宕机 → Sentinel 自动把从库提为主库               │
│   对客户端透明，不丢数据，不中断服务                     │
│   这才是面试官想听的角度                               │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 主从架构图

```
                  ┌──────────┐
                  │  Sentinel │  ← 监控 + 自动故障转移
                  │ 集群 (3)  │
                  └─────┬────┘
                        │
         ┌──────────────┼──────────────┐
         │              │              │
    ┌────▼────┐   ┌────▼────┐   ┌────▼────┐
    │  Master  │──▶│ Slave 1 │   │ Slave 2 │
    │  :6379   │   │ :6380   │   │ :6381   │
    └──────────┘   └──────────┘   └──────────┘
         ▲
         │  写操作全部走 Master
    ┌────┴────────────────────────────┐
    │ Spring Data Redis               │
    │ ReadFrom.REPLICA 配置后读自动路由 │
    └─────────────────────────────────┘
```

### Docker Compose 配置（可选）

```yaml
# 当前：单节点（开发够用）
redis:
  image: redis:alpine
  ports: ["6379:6379"]
  command: redis-server --requirepass redis123 --appendonly yes
  # 淘汰策略
  # CONFIG SET maxmemory-policy volatile-ttl

# 演示用：1 主 + 1 从
redis-master:
  image: redis:alpine
  ports: ["6379:6379"]
  command: >
    redis-server
    --requirepass redis123
    --appendonly yes
    --maxmemory 256mb
    --maxmemory-policy volatile-ttl

redis-slave:
  image: redis:alpine
  ports: ["6380:6379"]
  command: >
    redis-server
    --slaveof redis-master 6379
    --masterauth redis123
    --requirepass redis123
    --maxmemory 256mb
    --maxmemory-policy volatile-ttl
```

### Spring Boot 侧配置（读写分离）

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:192.168.147.132}
      port: 6379
      password: redis123
      lettuce:
        pool:
          max-active: 16
        # 读操作路由到从节点
        read-from: REPLICA
```

**关键**：Lettuce 的 `read-from: REPLICA` 只影响非事务、非 Lua 脚本的读操作。秒杀 Lua 脚本永远走主库，排行榜 ZRANGE 自动走从库。

### 面试话术

> "本项目核心路径是写密集型，主从读写分离收益有限。但 Redis 主从的真正价值不在读写分离，而在高可用。通过 Sentinel 集群监控主库，故障时自动将从库提升为主库，保证服务不中断。
>
> 具体配置上，Spring Data Redis 的 Lettuce 客户端支持 `read-from: REPLICA`，读操作自动路由到从节点，写操作和 Lua 脚本走主节点，无需代码改动。"

---

## 七、内存估算

假设：10w 日活用户，100 个秒杀活动，50 万次抢购

| Key 模式 | 单条大小 | 数量 | 总内存 |
|---------|---------|------|--------|
| `refresh:{token}` | 150B | 10w | 15MB |
| `device_sessions:{userId}` | 200B | 10w | 20MB |
| `idempotency:*` | 100B | 5000 峰值 | 0.5MB |
| `stock:shard:*` | 50B × 4 片 | 100 活动 | 20KB |
| `ordered:*` | 50B | 50w | 25MB |
| `ws:route:*` | 100B | 10w 峰值 | 10MB |
| `rank:gift:live` | 100B × 100 上榜 | 10 直播间 | 100KB |
| **合计** | | | **≈ 70MB** |

加上 Redis 自身开销（dictEntry、jemalloc 碎片）≈ 1.5x，实际约 **100MB**。

**结论**：256MB 内存非常充裕。

---

## 八、淘汰策略配置（Redis 服务端）

```bash
# 所有实例统一配置
CONFIG SET maxmemory 256mb
CONFIG SET maxmemory-policy volatile-ttl
CONFIG REWRITE
```

**为什么不用 `volatile-lru`？**  
TTL 本身就表达了数据重要性等级——幂等缓存 5 分钟 > 订单锁 10 秒 > refresh token 7 天。`volatile-ttl` 让这个优先级自动生效，不需要额外维护 LRU 的访问频率计数。

---

## 九、降级策略

| 场景 | 用户服务 | 秒杀服务 | 排行榜 |
|------|---------|---------|--------|
| Redis 读超时 | 抛异常，提示重试 | Caffeine 本地缓存兜底 | 返回空排行 |
| Redis 写超时 | 登录/注册失败 → 500 | 拒绝抢购 → 提示稍后再试 | 不更新排行 |
| Redis 完全挂 | 所有写操作失败 | 秒杀暂停，降级页面 | 静态排行榜 |
| 内存满触发淘汰 | `volatile-ttl` 自动淘汰短 TTL key | 幂等缓存/订单锁被淘汰（可接受） | 历史快照被淘汰 |

---

## 十、面试扩展点

1. **为什么 Hash 不用在 refresh token 上？**  
   refresh token 是独立 key，独立 TTL（7d），独立访问（每次刷新只用 token 查，不需要其他字段）。拆成 String 更灵活，7 天才过期，不差那点内存。

2. **为什么 activity cache 推荐用 Hash？**  
   活动的 title、price、startTime 等属性属于同一实体，总是一起读写，同一生命周期。用 Hash 省 key × dictEntry 开销，10w 个活动省几百 MB。

3. **为什么 hash tag 要从开发期就用？**  
   如果开发期用 `stock:shard:123:0`，上线 Cluster 后要改成 `stock:shard:{123}:0`——key 名变了，所有代码和 Lua 脚本都要改。从第一天就用 `{activityId}` 格式，零成本兼容。

4. **为什么 volatile-ttl 比 volatile-lru 好？**  
   TTL 本身就是业务设计的数据重要性等级。`volatile-ttl` 让这个设计自然生效，不需要额外维护 LRU 计数器。面试官问"你怎么决定哪个 key 先淘汰"时，回答："TTL 就是我的淘汰优先级"。
