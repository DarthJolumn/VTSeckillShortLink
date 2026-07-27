# 系统概览

> 高并发直播电商秒杀系统，覆盖秒杀抢购、商品短链、直播间互动、实时排行榜。
> 所有中间件部署于独立 VM（192.168.147.132）。

---

## 架构图

```
                          ┌───────────────────────────────────────────────┐
                          │        Spring Cloud Gateway (WebFlux)         │
                          │     JWT · HMAC-SHA256 签名 · Sentinel 限流     │
                          │     ScopedValue 用户上下文 → 注入请求头        │
                          └───┬────────────┬────────────┬─────────────────┘
                              │            │            │
            ┌─────────────────┘            │            └──────────────────┐
            ▼                               ▼                              ▼
   ┌──────────────┐               ┌──────────────────┐          ┌──────────────────┐
   │  User          │◄────Dubbo────│  WebSocket        │◄──Dubbo──│  Leaderboard     │
   │  :8081         │               │  :8083             │          │  :8084            │
   │  VT · MySQL    │               │  VT · Kafka · WS   │          │  VT · Redis ZSET  │
   │  JWT · Redis   │               │  Redis Pub/Sub     │          │  Dubbo Provider   │
   └──────────────┘               └────────┬─────────┘          └──────────────────┘
                                           │
                                     ┌─────┴──────────┐
                                     │                │
                                     ▼                ▼
                            ┌──────────────┐  ┌──────────────────┐
                            │  Seckill      │  │  ShortLink        │
                            │  :8090         │  │  :8084            │
                            │  Lua · Kafka   │  │  Caffeine+Redis   │
                            │  Redis Cluster │  │  DCL 防击穿       │
                            │  Sentinel      │  │  Rate Limiter     │
                            └──────┬───────┘  └──────────────────┘
                                   │
                                   ▼
                           ┌──────────────┐
                           │  Common       │
                           │  ScopedValue  │
                           │  Snowflake    │
                           │  ShortCode    │
                           └──────────────┘

     ┌──────────────────────────────────────────────────────────────────────┐
     │     VM: 192.168.147.132                                              │
     │     ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌───────┐                  │
     │     │Redis │ │MySQL │ │Nacos │ │Kafka │ │Sentinel│                  │
     │     └──────┘ └──────┘ └──────┘ └──────┘ └───────┘                  │
     └──────────────────────────────────────────────────────────────────────┘
```

---

## 技术栈

| 层 | 技术 | 版本 |
|---|------|------|
| 语言 | Java | 25 (ZGC + Virtual Threads) |
| 框架 | Spring Boot / Spring Cloud | 4.1.0 / 2025.1.2 |
| RPC | Apache Dubbo | 3.3.6 |
| 网关 | Spring Cloud Gateway (WebFlux) | 5.0.2 |
| ORM | MyBatis-Plus / JPA | 3.5.17 |
| 数据库 | MySQL | 8.x (InnoDB) |
| 缓存 | Redis Cluster + Caffeine | 7.x / 3.2.0 |
| 消息队列 | Kafka (KRaft) | 3.x |
| 注册中心 | Nacos | 2.x |
| 限流 | Sentinel | 1.8.9 |
| 链路追踪 | Micrometer Tracing + SkyWalking | 9.7.0 |

---

## 模块职责

| 模块 | 端口 | 职责 |
|------|------|------|
| `vtsl-gateway` | 8080 | 网关 (WebFlux)；路由转发、JWT 鉴权、签名验签、Sentinel 限流 |
| `vtsl-user` | 8081 | 用户服务；注册登录、双 Token、设备管理、封禁解禁 |
| `vtsl-seckill` | 8090 | 秒杀 + 商品；活动管理、Redis Lua 扣减、Kafka 异步下单 |
| `vtsl-shortlink` | 8084 | 短链系统；算法码派生、三级缓存、点击统计 |
| `vtsl-websocket` | 8083 | 直播间；WebSocket 弹幕/送礼、Redis Pub/Sub 秒杀推送 |
| `vtsl-leaderboard` | 8085 | 排行榜；Redis ZSet 实时 TopN + 历史快照 |
| `vtsl-common` | - | 公共库；ScopedValue、编解码器、注解、DTO |

---

## 端口规划

| 服务 | 端口 | 协议 | Dubbo |
|------|------|------|-------|
| gateway | 8080 | HTTP WebFlux | — |
| user | 8081 | HTTP MVC | 20881 |
| websocket | 8083 | HTTP MVC + WS | 20883 |
| shortlink | 8084 | HTTP MVC | 20884 |
| leaderboard | 8085 | HTTP MVC | 20885 |
| seckill | 8090 | HTTP MVC | 20882 |

**中间件**（VM: 192.168.147.132）：

| 中间件 | 端口 |
|--------|------|
| Redis (单节点) | 6379 |
| Redis Cluster | 6381 / 6382 / 6383 |
| MySQL | 3306 |
| Nacos | 8848 |
| Kafka | 9092 |
| Sentinel Dashboard | 8858 |

---

## Gateway 路由

```yaml
spring.cloud.gateway.server.webflux.routes:
  - id: user-service          # Path=/auth/**,/user/**       → lb://vtsl-user
  - id: seckill-service       # Path=/seckill/**             → lb://vtsl-seckill
  - id: product-service       # Path=/product/**             → lb://vtsl-seckill
  - id: leaderboard-service   # Path=/leaderboard/**         → lb://vtsl-leaderboard
  - id: live-service          # Path=/live/**                → lb://vtsl-websocket
  - id: websocket-service     # Path=/ws/**                  → lb:ws://vtsl-websocket
  - id: shortlink-service     # Path=/s/**                   → lb://vtsl-shortlink
```

**白名单**（免 JWT）：
- 完全公开：`/auth/login`, `/auth/register`, `/auth/refresh`, `/ws/**`, `/actuator/health`
- GET 公开：`/live/rooms`, `/live/room/**`, `/leaderboard/**`

---

## 网关过滤器链

| Order | 过滤器 | 职责 |
|-------|--------|------|
| `HIGHEST_PRECEDENCE + 10` | `ShortCodeValidationFilter` | 短码正则校验，非法直接 400 |
| `-10` | `SignVerifyGlobalFilter` | HMAC-SHA256 签名 + Redis Nonce 60s 防重放 |
| `-5` | `JwtAuthGlobalFilter` | JWT 验签 → 注入 `X-User-Id`/`X-User-Role` header |
