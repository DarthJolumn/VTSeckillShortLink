# lm-ui 与后端适配审查

> 审查日期：2026-07-21
> 前端模块：`lm-ui/`（Vue 3 + TypeScript + Vite 6）
> 后端范围：`LiveMallBk/` Gateway / User / Seckill / Leaderboard / Websocket
> 方法：逐接口比对前端 Store/Composable 的 API 调用、TypeScript 类型定义、后端 Controller/DTO/Entity、Gateway 路由、WebSocket 消息协议

---

## 一、审计结果总览

| 模块 | REST 端点 | 状态 | 备注 |
|---|---|---|---|
| Auth | 4 | ✅ 全部匹配 | `/auth/login` `/auth/register` `/auth/refresh` `/auth/logout` |
| User | 6 | ✅ 全部匹配 | `/user/profile` `/user/balance` `/user/devices` `/user/devices/{id}` `/user/profile`(PUT) `/user/password` |
| Live Room | 5 | ⚠️ 1 个 Gateway bug | 见下文 §2 |
| Seckill | 8 | ✅ 全部匹配 | 前端字段 `name/price/origPrice/stockTotal/startAt/endAt` 与后端 `CreateActivityRequest.toEntity()` 映射一致 |
| Leaderboard | 2 | ✅ 全部匹配 | `/leaderboard/top` `/leaderboard/rank/{userId}` |
| WebSocket | 9 种消息 | ✅ 全部匹配 | 协议字段对齐 |
| **合计** | **34** | **33 ✅ / 1 ⚠️** | |

---

## 二、🔴 关键 BUG：Gateway 白名单不匹配

### 问题

`LiveMallBk/livemall-gateway/src/main/resources/application.yml:96`:

```yaml
gateway.auth.public-get-paths: /live/room/*,/leaderboard/**
```

Ant 通配符 `*` **只匹配一级路径**。`/live/room/*` 匹配 `/live/room/123`，但**不匹配** `/live/rooms`。

前端 `LiveHallPage`（匿名可访问）调用 `GET /live/rooms` 获取房间列表——若用户未登录，Gateway 拦截并返回 401。

### 影响

- 匿名用户打开首页（`/`）→ `LiveHallPage` → 调用 `GET /live/rooms` → Gateway 无 Token → 401 → 页面空白
- 登录用户不受影响（`X-User-Id` 由 `JwtAuthGlobalFilter` 注入）

### 修复

将 `public-get-paths` 中的 `/live/room/*` 改为 `/live/**`：

```yaml
gateway.auth.public-get-paths: /live/** ,/leaderboard/**
```

这样同时覆盖 `GET /live/rooms`（房间列表）和 `GET /live/room/{roomId}`（房间详情），且限制为仅 GET 方法放行。

---

## 三、🟡 潜在问题

### 3.1 时间字段时区歧义

`SeckillActivity.startTime` / `endTime` 后端为 `LocalDateTime`，序列化为 `"2026-07-21T10:00:00"`（无时区）。前端用 `new Date(a.startTime).getTime()` 解析做倒计时差量。

ECMAScript 规范中无时区 ISO 日期时间字符串处理是实现相关——Chrome 视为本地时间。服务器与用户时区不同时倒计时会偏差。

**建议**：后端统一用 `Instant` / 返回 epoch ms，前端直接用数值计算。

### 3.2 设备指纹「当前设备」标记冗余

前端 `stores/user.ts:29`:
```typescript
devices.value = res.data.map(d => ({ ...d, current: d.deviceId === currentId }))
```

后端 `UserController.getDevices()` 同样用 `X-Device-Id` 标记了 `current`。前端覆盖了后端结果，但值相同，功能无影响。

### 3.3 `RoomVO.startedAt` 类型不一致

- 后端 `RoomVO.startedAt`: `LocalDateTime` → 序列化为 ISO 字符串 `"2026-07-21T10:00:00"`
- 前端 `RoomVO.startedAt`: `string`
- JSON 序列化/反序列化均正常工作，类型兼容。

### 3.4 多余的后端端点未使用

`GET /leaderboard/history` — 后端已实现，前端未调用。不影响功能。

---

## 四、✅ 已确认匹配的端点详表

### 4.1 Auth（/auth）

| 前端 | 后端 | 请求体 | 响应体 |
|---|---|---|---|
| `POST /auth/login` | `AuthController.login()` | `{username, password}` + `X-Device-Id` + `X-Idempotency-Key` | `{accessToken, refreshToken, expiresIn, tokenType}` |
| `POST /auth/register` | `AuthController.register()` | `{username, password, phone?}` + `X-Idempotency-Key` | `code 200` |
| `POST /auth/refresh` | `AuthController.refresh()` | `{refreshToken}` | `{accessToken, refreshToken, expiresIn, tokenType}` |
| `POST /auth/logout` | `AuthController.logout()` | `{refreshToken}` | `code 200` |

### 4.2 User（/user）

| 前端 | 后端 | 说明 |
|---|---|---|
| `GET /user/profile` | `UserController.getProfile()` | → `UserProfileVO {id,username,nickname,avatar,phone,role,status}` |
| `PUT /user/profile` | `UserController.updateProfile()` | `{nickname?, avatar?, phone?}` → `UserProfileVO` |
| `PUT /user/password` | `UserController.updatePassword()` | `{oldPassword, newPassword}` → `code 200` |
| `GET /user/balance` | `UserController.getBalance()` | → `number` (BigDecimal) |
| `GET /user/devices` | `UserController.getDevices()` | → `[{deviceId, current}]` |
| `DELETE /user/devices/{id}` | `UserController.kickDevice()` | → `code 200` |

### 4.3 Live Room（/live）

| 前端 | 后端 | 说明 |
|---|---|---|
| `GET /live/rooms` | `LiveRoomController.listRooms()` | ⚠️ Gateway 白名单需修复 |
| `GET /live/room/{id}` | `LiveRoomController.getRoom()` | 公开访问 |
| `POST /live/room/start` | `LiveRoomController.start()` | 需 ANCHOR/ADMIN 角色 |
| `POST /live/room/stop` | `LiveRoomController.stop()` | 需 ANCHOR/ADMIN 角色 |
| `GET /live/my-active-room` | `LiveRoomController.getMyActive()` | 需登录 |

### 4.4 Seckill（/seckill）

| 前端 | 后端 | 说明 |
|---|---|---|
| `GET /seckill/activity/list?roomId=` | `SeckillController.activityList()` | 可选 roomId 过滤 |
| `POST /seckill/activity` | `SeckillController.createActivity()` | 字段映射见表 §4.4.1 |
| `PUT /seckill/activity/{id}/status` | `SeckillController.updateStatus()` | body `{status}` |
| `POST /seckill/order` | `SeckillController.placeOrder()` | body `{activityId}` → `{result, orderNo}` |
| `GET /seckill/order/list` | `SeckillController.orderList()` | 按 `X-User-Id` 过滤 |
| `GET /seckill/order/{orderNo}` | `SeckillController.orderDetail()` | |
| `PUT /seckill/order/{orderNo}/cancel` | `SeckillController.cancelOrder()` | |
| `PUT /seckill/order/{orderNo}/refund` | `SeckillController.refundOrder()` | |

#### 4.4.1 createActivity 字段映射

| 前端字段 | 后端 `CreateActivityRequest` | → 后端 `SeckillActivity` 实体 |
|---|---|---|
| `name` | `name` | `title` (via `toEntity()`) |
| `price` | `price` (BigDecimal) | `seckillPrice` |
| `origPrice` | `origPrice` (BigDecimal) | `originalPrice` |
| `stockTotal` | `stockTotal` (Integer) | `totalStock` |
| `startAt` | `startAt` (Long epoch ms) | `startTime` (LocalDateTime) |
| `endAt` | `endAt` (Long epoch ms) | `endTime` (LocalDateTime) |
| `productId?` | `productId` (Long) | `productId` |
| `roomId?` | `roomId` (Long) | `roomId` |

### 4.5 Leaderboard（/leaderboard）

| 前端 | 后端 | 说明 |
|---|---|---|
| `GET /leaderboard/top?activityId=&n=100` | `LeaderboardController.topN()` | → `[{userId, score, rank}]` |
| `GET /leaderboard/rank/{userId}?activityId=` | `LeaderboardController.rank()` | → `{userId, score, rank}` |

### 4.6 WebSocket 消息协议

#### 上行（前端 → 后端）

| type | data | 后端处理 |
|---|---|---|
| `PING` | `{}` | 回复 `PONG` |
| `AUTH` | `{token}` | 验签→升级为认证连接→回复 `AUTH_OK`/`AUTH_FAILED` |
| `BARRAGE` | `{content}` | 校验非空→广播给同房间所有人 |
| `GIFT` | `{giftId, quantity}` | 广播+排行榜加分(Dubbo) |
| `SEC_KILL` | `{activityId}` | 仅日志（占位；抢购走 REST `POST /seckill/order`） |

#### 下行（后端 → 前端）

| type | data | 备注 |
|---|---|---|
| `CONNECTED` | `{anonymous, displayName, online}` | 握手成功 |
| `AUTH_OK` | `{userId, role, displayName}` | AUTH 成功后回复 |
| `AUTH_FAILED` | `{reason}` | Token 无效 |
| `NEED_AUTH` | `{reason}` | 匿名用户尝试发送 |
| `PONG` | `{}` | 心跳回复 |
| `BARRAGE` | `{userId, username, avatar, content, timestamp}` | 弹幕广播（avatar 后端空字符串） |
| `GIFT` | `{userId, username, giftId, giftName, giftIcon, price, gain, quantity, timestamp}` | 元数据由本地补齐 |
| `SEC_KILL_RESULT` | `{orderNo, ok, reason, message, timestamp}` | 异步秒杀结果 |
| `ROOM_CLOSED` | `{roomId}` | 关播通知 |
| `KICK` | `{reason}` | 被踢下线 |
| `ERROR` | `{reason}` | 通用错误 |

前端 `useWebSocket.ts` 在 `GIFT` 消息中本地补齐 `giftName/giftIcon/price/gain`（`getGiftDef(msg.data.giftId)`），后端只传 `giftId/quantity`——**已知设计，前端已处理**。

---

## 五、Gateway 路由映射

| 路由 ID | URI | Path | 前端 proxy |
|---|---|---|---|
| `user-service` | `lb://livemall-user` | `/auth/**`, `/user/**` | `/api/auth/**` → `/auth/**` |
| `seckill-service` | `lb://livemall-seckill` | `/seckill/**` | `/api/seckill/**` → `/seckill/**` |
| `leaderboard-service` | `lb://livemall-leaderboard` | `/leaderboard/**` | `/api/leaderboard/**` → `/leaderboard/**` |
| `live-service` | `lb://livemall-websocket` | `/live/**` | `/api/live/**` → `/live/**` |
| `websocket-service` | `lb:ws://livemall-websocket` | `/ws/**` | `/ws/**` |

前端 Vite 开发服务器代理剥掉 `/api` 前缀后与 Gateway 路由完全对齐。

---

## 六、与前版审查的关系

本报告替代 `前后端适配审查报告.md`（2026-07-19 审查，覆盖已废弃的 `livemall-ui-vue` 模块）。此前报告指出的问题在新版 `lm-ui` 中已全部修复：

| 前报告问题 | 状态 |
|---|---|
| UserController 缺失 `PUT /user/profile` / `PUT /user/password` | ✅ 后端已实现 |
| SeckillActivity 字段名前后端不一致 | ✅ `CreateActivityRequest.toEntity()` 中转 |
| WS 协议 `nickname`↔`username` 不匹配 | ✅ 后端已统一为 `username` 广播 |
| WS 协议 `success`↔`ok` 不匹配 | ✅ 后端已统一为 `ok` |
| 演示登录导致真实 WS 连接空数据 | ✅ `lm-ui` 用 MSW 替代了旧 mock 机制 |
