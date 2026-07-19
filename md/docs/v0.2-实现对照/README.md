# LiveMall v0.2 — 实际实现对照文档

> v0.1 = 原始设计文档（md/docs/1.x ~ 6.x）
> v0.2 = 实际落地代码 + 架构升级（JDK 25 / Boot 4.1 / gRPC / 77 tests）
> 本文档标记设计 vs 实现的差异

---

## 技术栈变更

| 组件 | v0.1 设计 | v0.2 实际 | 原因 |
|---|---|---|---|
| JDK | 21 | **25 LTS** | JEP 491 VT pinning 修复, ScopedValue 正式 API |
| Spring Boot | 3.2.7 | **4.1.0** | gRPC 自动配置, Jackson 3, Jakarta EE 11 |
| Spring Cloud | 2023.0.3 | **2025.1.2** | 网关 artifact 改名 |
| SCA | 2023.0.1.2 | **2025.1.0.0** | Nacos 3.1.1, Sentinel 1.8.9 |
| Dubbo | 3.3.0 | **3.3.6** | VT 兼容性 |
| Jackson | 2.x | **3.1.4** (tools.jackson) | Boot 4.x 默认, JsonProcessingException→JacksonException |
| RPC | 纯 Dubbo | **Dubbo + gRPC 双通道** | Boot 4.1 内置 gRPC, 秒杀推送用 |
| 注解处理 | 默认 | **-proc:full** | JDK 23+ 默认关闭, Lombok 需显式开启 |
| ScopedValue | --enable-preview | 正式 API | JDK 25 下不再需要预览标志 |

---

## 模块对照

### common（公共模块）— 20 源

| 功能 | 设计 | 实际文件 |
|---|---|---|
| 统一返回 | Result\<T\> | `Result.java` |
| 异常体系 | BizException + GlobalExceptionHandler | `BizException.java`, `GlobalExceptionHandler.java` |
| JWT | HS256, ClaimBuilder → v0.2 用 Jwts.builder() | `JwtUtil.java` |
| 幂等 | Redis SETNX, 5min TTL | `IdempotencyService.java` |
| HMAC | SHA256 签名 | `HmacUtil.java` |
| 用户上下文 | ScopedValue (preview→正式) | `UserContext.java` |
| 注解 | @PublicApi, @RequireAuth, @RequireRole | `annotation/` |
| Dubbo API | UserDubboApi, WsPushService, LeaderboardService | `api/` |
| gRPC Proto | 新增, 原始设计无 | `seckill-push.proto` + 生成代码 |
| DFA 敏感词 | 设计有, 未集成到 WS | `SensitiveFilter.java`（已实现, 待集成） |
| Snowflake | 设计有, 未集成到订单号 | `SnowflakeIdGenerator.java`（已实现, 订单号仍用 UUID） |
| **未实现** | Knife4j API 文档 | ❌ 未引入 |

### user（用户服务）— 15 源 + 3 测

| 功能 | 状态 | 测试覆盖 |
|---|---|---|
| 注册 + BCrypt + 幂等 | ✅ | AuthControllerTest(13) |
| 登录 + 双 Token + 设备记录 | ✅ | AuthControllerTest |
| Refresh Rotation + 并发合并 | ✅ | UserServiceTest(18) |
| 退出登录 | ✅ | AuthControllerTest |
| 个人信息查询 | ✅ | UserControllerTest(6) |
| 设备列表 + 踢设备 (Dubbo→WS) | ✅ | UserControllerTest |
| 封禁/解禁 | ✅ 已实现 | — |
| **未实现** | 修改密码、修改昵称头像 | ❌ |

### websocket（直播长连接）— 17 源 + 2 测

| 功能 | 状态 | 测试覆盖 |
|---|---|---|
| JSR-356 @ServerEndpoint | ✅ | — |
| JWT 握手验签 + 匿名降级 | ✅ | — |
| 连接上限保护 (maxSessions) | ✅ | — |
| 弹幕广播 (VT 并行 + Semaphore(200)) | ✅ | WsPushServiceImplTest(7) |
| 送礼广播 + Dubbo 排行榜加分 | ✅ | — |
| PING/PONG 心跳 | ✅ | — |
| AUTH 消息升级匿名→认证 | ✅ | — |
| KICK 踢人 + 3s VT 延迟关闭 | ✅ | WsPushServiceImplTest |
| 开播/关播 CRUD | ✅ | — |
| 房间列表 + 在线人数 | ✅ | — |
| gRPC SeckillPushGrpcService | ✅ 新增 | — |
| WsSessionManager 会话管理 | ✅ | WsSessionManagerTest(13) |
| Dubbo WsPushService | ✅ | WsPushServiceImplTest |
| **未实现** | DFA 敏感词集成到弹幕 | ❌ |
| **未实现** | ws:route Redis 跨节点路由 | ❌ |
| **未实现** | 关播 ROOM_CLOSED 广播 | ❌ |
| **未实现** | 心跳扫描 @Scheduled | ❌ |

### seckill（秒杀）— 11 源 + 1 测

| 功能 | 状态 | 测试覆盖 |
|---|---|---|
| Redis Lua 4 分片原子扣库存 | ✅ | SeckillServiceTest(7) |
| 回补库存 | ✅ | StockServiceTest(5) |
| 活动管理 (创建/详情/列表/上下架) | ✅ | SeckillServiceTest |
| 抢购下单 (校验→Lua→Kafka) | ✅ | SeckillServiceTest |
| Kafka Consumer 异步创建订单 | ✅ | — |
| gRPC 推送秒杀结果到 WS | ✅ 新增 | — |
| 超时取消 + 乐观锁 CAS | ✅ | — |
| 订单查询/取消/退款 | ✅ | SeckillServiceTest |
| **未实现** | Sentinel 热点参数限流 | ❌ |
| **未实现** | Caffeine 活动缓存 + 售罄标记 | ❌ |
| **未实现** | Snowflake 订单号 (仍用 UUID) | ❌ |
| **未实现** | Kafka 降级为同步下单 | ❌ |

### leaderboard（排行榜）— 6 源 + 1 测

| 功能 | 状态 | 测试覆盖 |
|---|---|---|
| Dubbo addScore (ZINCRBY) | ✅ | LeaderboardServiceImplTest(4) |
| HTTP TopN (ZREVRANGE) | ✅ | LeaderboardServiceImplTest |
| HTTP 个人排名 (ZSCORE+ZREVRANK) | ✅ | LeaderboardServiceImplTest |
| HTTP 历史排行 | ⚠️ 查询快照表，返回空 | — |
| @Scheduled 5min 快照落库 | ✅ | — |
| **未实现** | Caffeine TopN 缓存 | ❌ |
| **未实现** | 多活动快照 (当前只快照 activityId=1) | ❌ |

### gateway（网关）— 5 源 + 2 测

| 功能 | 状态 | 测试覆盖 |
|---|---|---|
| JWT 鉴权 (AntPathMatcher 白名单) | ✅ | JwtAuthGlobalFilterTest(6) |
| HMAC 签名验签 (Timestamp+Nonce) | ✅ | SignVerifyGlobalFilterTest(7) |
| CORS 跨域 | ✅ | — |
| GlobalErrorWebExceptionHandler | ✅ (Boot 4.x 换包 `web.reactive→webflux`) | — |
| Sentinel 限流 | ⚠️ 依赖已引入, 未配规则 | — |
| **未实现** | Sentinel Dashboard 动态规则 | ❌ |

---

## 数据一致性设计（v0.2 新增）

| 环节 | 一致性 | 实现 |
|---|---|---|
| Redis Lua 扣库存 | **强一致** | 单线程原子执行 |
| Kafka → Consumer | **最终一致** | 异步消峰, 订单晚 50ms 可接受 |
| DB uk_activity_user | **强一致** | 唯一索引物理防重 |
| 超时取消 vs 用户取消 | **乐观锁 CAS** | version 字段 + WHERE version=? |
| 退款/取消回补库存 | **强一致** | Lua 脚本 INCR + DEL |

**MySQL：** 默认 RR + ROW，不升级 Serializable。并发安全靠 Redis 原子性 + 唯一索引 + 乐观锁。

**分库分表：** ❌ 不落地。设计上：垂直分库（user / live / trade），水平分表键 user_id（订单）/ room_id（弹幕）。面试能讲。

**Redis 主从：** ❌ 不落地。秒杀路写密集，主从收益低。排行榜（读多）可独立实例。

**MySQL 主从：** ❌ 不落地。Kafka 削峰后写入 < 100 TPS，单实例够。面试：1 主 2 从 + MGR。

---

## 测试覆盖率

| 模块 | 测试文件 | 测试数 | 覆盖范围 |
|---|---|---|---|
| user | AuthControllerTest(13)+UserControllerTest(6)+UserServiceTest(18) | 37 | 认证+设备+刷新 |
| websocket | WsSessionManagerTest(13)+WsPushServiceImplTest(7) | 20 | 会话管理+推送 |
| gateway | JwtAuthGlobalFilterTest(6)+SignVerifyGlobalFilterTest(7) | 13 | JWT+签名 |
| seckill | SeckillServiceTest(7) | 7 | 抢购+取消+退款 |
| leaderboard | LeaderboardServiceImplTest(4) | 4 | ZSet 操作 |
| **总计** | **9 files** | **74 tests** | — |

---

## 待办（v0.3）

| 优先级 | 内容 |
|---|---|
| P0 | JMeter 压测, 获取量化数据 |
| P1 | DFA 敏感词集成到 LiveWebSocket.handleBarrage |
| P1 | Sentinel 限流规则配置 |
| P1 | Snowflake 替换 UUID 订单号 |
| P2 | Caffeine 活动缓存 + 售罄标记 |
| P2 | ws:route Redis 跨节点路由 |
| P2 | 关播 ROOM_CLOSED 广播 |
| P3 | Knife4j API 文档 |
