# 模块详解

## Gateway

> `vtsl-gateway` · 端口 8080 · WebFlux 响应式 · **不做业务逻辑**

### 类结构

```
filter/
├── JwtAuthGlobalFilter.java       # Order=-5: JWT 验签 + X-User-Id 注入
├── SignVerifyGlobalFilter.java    # Order=-10: HMAC-SHA256 + Nonce 防重放
├── ShortCodeValidationFilter.java # Order=HIGHEST_PRECEDENCE+10: 短码格式校验
└── GlobalErrorWebExceptionHandler.java # WebFlux 全局异常
config/
└── CorsConfig.java
```

### 过滤器链

| Order | 过滤器 | 跳过条件 |
|-------|--------|---------|
| +10 | `ShortCodeValidationFilter` | 非 GET /s/{code} 路径 |
| -10 | `SignVerifyGlobalFilter` | 请求无签名头 |
| -5 | `JwtAuthGlobalFilter` | 白名单路径 / 签名通过标记 |

### Security

- JWT: HMAC-SHA256 对称签名，secret 源自配置
- 白名单: 完全公开 `/auth/login`, `/ws/**`；GET 公开 `/live/rooms`, `/leaderboard/**`
- 签名: 可选接口签名 + Reactive Redis Nonce 60s TTL 防重放
- 限流: Sentinel 令牌桶 + 熔断降级

### 路由表

| ID | Path | 后端 |
|----|------|------|
| user-service | `/auth/**`, `/user/**` | `lb://vtsl-user:8081` |
| seckill-service | `/seckill/**` | `lb://vtsl-seckill:8090` |
| product-service | `/product/**` | `lb://vtsl-seckill:8090` |
| leaderboard-service | `/leaderboard/**` | `lb://vtsl-leaderboard:8084` |
| live-service | `/live/**` | `lb://vtsl-websocket:8083` |
| websocket-service | `/ws/**` | `lb:ws://vtsl-websocket:8083` |
| shortlink-service | `/s/**` | `lb://vtsl-shortlink:8091`（simple，遗留） |
| shortlink-api-service | `/api/v1/**` | `lb://vtsl-shortlink-api:8085` |

---

## User

> `vtsl-user` · 端口 8081 · Spring MVC + VT · Dubbo:20881

### API

| Method | Path | 说明 | 鉴权 |
|--------|------|------|------|
| POST | `/auth/register` | 注册 | 公开 |
| POST | `/auth/login` | 登录(双Token) | 公开 |
| POST | `/auth/refresh` | 刷新 Token | 公开 |
| GET | `/user/profile` | 用户信息 | JWT |
| GET | `/user/balance` | 余额 | JWT |
| PUT | `/user/profile` | 修改信息 | JWT |
| PUT | `/user/password` | 修改密码 | JWT |
| GET | `/user/devices` | 设备列表 | JWT |
| DELETE | `/user/devices/{id}` | 踢设备 | JWT |

### 双 Token

- accessToken: 15min 有效期，JWT 格式
- refreshToken: 7d 有效期，Redis 存储 + 校验

---

## Seckill

> `vtsl-seckill` · 端口 8090 · Spring MVC + VT · Dubbo:20882 · Kafka 生产/消费

### API

| Method | Path | 说明 | 鉴权 |
|--------|------|------|------|
| POST | `/seckill/activity` | 创建活动 | ADMIN |
| PUT | `/seckill/activity/{id}/status` | 上下架 | ADMIN |
| GET | `/seckill/activity/{id}` | 活动详情 | JWT |
| GET | `/seckill/activity/list` | 活动列表 | 公开/GET |
| POST | `/seckill/order` | 抢购下单 | JWT |
| GET | `/seckill/order/list` | 订单列表 | JWT |
| GET | `/seckill/order/{orderNo}` | 订单详情 | JWT |
| PUT | `/seckill/order/{orderNo}/cancel` | 取消订单 | JWT |
| PUT | `/seckill/order/{orderNo}/refund` | 退款 | ADMIN |

### 核心类

```
controller/SeckillController.java   # REST 接口（@RequestHeader 取用户）
service/
├── SeckillService.java             # 业务编排
├── StockService.java               # Redis Lua 扣减/回补
└── ActivityCacheService.java       # Caffeine L1 活动缓存
consumer/
└── SeckillOrderConsumer.java       # Kafka 消费（Semaphore 背压）
scheduler/
├── TimeoutCancelScheduler.java     # 15s 超时取消
└── ReconciliationScheduler.java    # 5min 对账补偿
config/
├── KafkaConfig.java                # Kafka 配置
├── GrpcClientConfig.java           # gRPC 客户端（备用推送）
└── RedisScriptWarmup.java          # Lua 脚本预热
entity/
├── SeckillActivity.java            # 活动实体（@Version 乐观锁）
└── SeckillOrder.java               # 订单实体（@Version 乐观锁）
warmup/
└── SeckillDataWarmup.java          # 启动预热
```

---

## ShortLink

> `vtsl-shortlink-simple`（遗留） · 端口 8091 · Spring MVC + VT · Dubbo:20886 · Kafka 消费
> **新版架构**：`vtsl-shortlink-keygenerator`（KGS，端口 8082 / gRPC 50051，Mongo 状态 + Redis 队列预生成短码）+ `vtsl-shortlink-api`（端口 8085 / Dubbo:20885，URL CRUD / 重定向 / Analytics / 二级缓存 / 限流，gRPC 客户端向 KGS 取码）。由 Go `shortly` 项目重构而来（`shortly/` 目录为源码溯源）。

### API（simple 旧版）

| Method | Path | 鉴权 | 说明 |
|--------|------|------|------|
| POST | `/s/create` | 公开 | 创建短链（无用户绑定） |
| GET | `/s/{shortCode}` | 公开 | 解析短链（限流） |
| POST | `/s/manage/create` | JWT | 创建短链（绑定用户） |
| GET | `/s/manage/list` | JWT | 分页查询 |
| GET | `/s/manage/{id}` | JWT | 详情 |
| DELETE | `/s/manage/{id}` | JWT | 软删除 |

### API（新版 vtsl-shortlink-api，`/api/v1/**`）

| Method | Path | 鉴权 | 说明 |
|--------|------|------|------|
| POST | `/api/v1/url/shorten` | JWT + 限流 | 创建短链（KGS 取码或自定义） |
| GET | `/api/v1/url/` | JWT + 限流 | 我的短链列表 |
| GET | `/api/v1/url/{shortKey}` | JWT + 限流 | 详情 |
| PATCH | `/api/v1/url/{shortKey}` | JWT + 限流 | 更新（可换码） |
| DELETE | `/api/v1/url/{shortKey}` | JWT + 限流 | 软删除 |
| GET | `/api/v1/url/redirect/{shortKey}` | 公开 + 限流 | 跳转解析（返回原 URL） |
| GET | `/api/v1/analytics/{urlId}` | JWT + 限流 | 点击统计 |

### 核心类（simple 旧版）

```
controller/ShortLinkController.java  # REST 接口
service/
├── ShortLinkService.java            # 三级解析 + DCL 防击穿
├── ShortLinkCache.java              # L1 Caffeine + L2 Redis Hash
├── IdGenerator.java                 # Redis INCR → Base62 短码
├── StatisticsService.java           # Kafka 统计发送
└── RateLimitService.java            # Lua 滑动窗口限流
consumer/
└── StatisticsConsumer.java          # Kafka → MySQL 按天聚合
```

---

## WebSocket

> `vtsl-websocket` · 端口 8083 · Spring MVC + VT · Dubbo:20883 · Redis Pub/Sub

### 核心类

```
LiveWebSocket.java          # JSR-356 @ServerEndpoint（弹幕/送礼/心跳）
WsSessionManager.java       # 房间级会话管理（ConcurrentHashMap）
RedisPushSubscriber.java    # 订阅秒杀结果 → WS 广播
WsPushServiceImpl.java      # Dubbo WsPushService 实现
```

### 事件类型

| 事件 | 方向 | 说明 |
|------|------|------|
| BARRAGE | 客户端→服务器 | 弹幕消息 |
| GIFT | 客户端→服务器 | 送礼消息 |
| HEARTBEAT | 双向 | 心跳保活 |
| SEC_KILL_RESULT | 服务器→客户端 | 秒杀结果推送 |

---

## Leaderboard

> `vtsl-leaderboard` · 端口 8084 · Spring MVC + VT · Dubbo:20884

### 核心类

```
LeaderboardController.java       # 查询接口
LeaderboardServiceImpl.java      # Redis ZSet 实时积分
SnapshotTask.java                # @Scheduled 每 5min 历史快照
```

### API

| Method | Path | 说明 |
|--------|------|------|
| POST | `/leaderboard/score` | 加分（Dubbo + HTTP） |
| GET | `/leaderboard/top` | 实时 TopN |
| GET | `/leaderboard/rank/{userId}` | 个人排名 |
| GET | `/leaderboard/history` | 历史排行 |

---

## Common

> `vtsl-common` · 公共库，被所有模块依赖

### 包结构

```
context/UserContext.java       # ScopedValue 声明 + 读取方法
filter/UserContextFilter.java   # Servlet Filter → ScopedValue 绑定
interceptor/AuthInterceptor.java# @RequireAuth / @RequireRole 校验
annotation/
├── RequireAuth.java            # 需要登录
├── RequireRole.java            # 需要角色
└── PublicApi.java              # 公开接口
util/
├── SnowflakeIdGenerator.java   # 雪花 ID 生成器
└── JwtUtil.java                # JWT 工具
codec/ShortCodeCodec.java       # Base58 全 64 位编解码器
api/ProductShortLinkService.java# Dubbo RPC 接口
dto/Result.java                 # 统一响应体
exception/
├── BizException.java           # 业务异常
└── GlobalExceptionHandler.java # 全局异常处理
constant/RoleEnum.java          # 角色枚举
```
