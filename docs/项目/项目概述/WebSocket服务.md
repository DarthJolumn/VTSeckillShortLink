# WebSocket 服务 — livemall-websocket

**端口**: 8083 | **gRPC**: 9090 | **Dubbo**: 20883 | **技术栈**: JSR-356 @ServerEndpoint + VT + JPA + Kafka

---

## 1. WebSocket 长连接 — `LiveWebSocket`

**端点**: `ws://host:8080/ws/live/{roomId}`

### 1.1 连接建立 (`@OnOpen`)

| 查询参数 | 必填 | 说明 |
|---------|------|------|
| `roomId` | 是 | 直播间 ID（路径参数） |
| `token` | 否 | 可选，匿名连接降级为"游客" |
| `deviceId` | 否 | 设备标识 |

**连接模式**:
- **匿名模式**: 无 token 或 token 无效 → `guestName` = `游客_AB3F`
- **认证模式**: token 有效 → 解析 JWT 获取 userId/role
- **上限保护**: 全局连接数 ≥ `max-sessions`(200K) 时拒绝新连接（TRY_AGAIN_LATER）

**响应** (`CONNECTED`):
```json
{"type":"CONNECTED","data":{"anonymous":false,"displayName":"user_123","online":42}}
```

### 1.2 运行时认证升级 (`AUTH` 消息)

匿名连接可在运行时通过发送 `AUTH` 消息升级为认证连接：
```json
{"type":"AUTH","data":{"token":"eyJ..."}}
```
- 成功 → `AUTH_OK`（返回 userId/role/displayName）
- 失败 → `AUTH_FAILED`

### 1.3 心跳 (`PING / PONG`)

- 客户端每 30s 发送 `{"type":"PING"}` → 服务端回复 `{"type":"PONG"}`
- 每次收到消息自动 `touch()` 更新活跃时间
- `HeartbeatScanner` 每 10s 扫描，60s 无活动则关闭连接（清理 TCP 幽灵连接）

### 1.4 消息类型

| 客户端 → 服务端 | 说明 |
|----------------|------|
| `PING` | 心跳 |
| `AUTH` | 运行时认证升级 |
| `BARRAGE` | 发送弹幕 |
| `GIFT` | 送礼 |
| `SEC_KILL` | 秒杀请求（预留） |

| 服务端 → 客户端 | 说明 |
|----------------|------|
| `CONNECTED` | 连接成功 |
| `PONG` | 心跳回复 |
| `AUTH_OK` | 认证成功 |
| `AUTH_FAILED` | 认证失败 |
| `NEED_AUTH` | 匿名用户尝试操作提示 |
| `BARRAGE` | 弹幕广播 |
| `GIFT` | 礼物广播 |
| `SEC_KILL_RESULT` | 秒杀结果推送 |
| `ROOM_CLOSED` | 关播通知 |
| `KICK` | 踢下线通知 |
| `ERROR` | 错误消息 |

### 1.5 弹幕 (`BARRAGE`)

```json
{"type":"BARRAGE","data":{"content":"hello"}}
```
广播给房间内所有人，携带 userId/username/timestamp。

### 1.6 送礼 (`GIFT`)

```json
{"type":"GIFT","data":{"giftId":1,"quantity":1}}
```
广播给房间内所有人 + **Dubbo 调用** `LeaderboardService.addScore()` 异步加分。

### 1.7 广播机制

- 使用 `Semaphore(200)` 控制并发 VT 数量
- 每个连接独立 VT 异步发送（`session.getAsyncRemote().sendText()`）
- 避免平台线程 pinning

### 1.8 连接关闭 (`@OnClose / @OnError`)

- `WsSessionManager.remove()` 清理三级索引
- 不区分主动关闭和异常断开

---

## 2. REST 接口 — `LiveRoomController` (`/live`)

### 2.1 开播 `POST /live/room/start`

| 权限 | 说明 |
|------|------|
| `@RequireRole(ANCHOR, ADMIN)` | 仅主播/管理员 |

**请求体**:
```json
{"title":"直播标题","category":"游戏","coverColor":"#ff0000"}
```

**逻辑**:
1. **Dubbo 调用** `UserDubboApi.getById()` 获取主播昵称（通过 `UserServiceClient`）
2. 幂等：同一主播已有直播中房间则直接返回
3. MySQL INSERT `t_live_room`

### 2.2 关播 `POST /live/room/stop`

| 权限 | 说明 |
|------|------|
| `@RequireRole(ANCHOR, ADMIN)` | 仅主播/管理员 |

**请求体**: `{"roomId": 1}`

**逻辑**:
1. 验证 roomId + anchorId 归属
2. 更新 `t_live_room` status=0, onlineCount, endedAt
3. 广播 `ROOM_CLOSED` → VT sleep 3s → 关闭所有观众 WS 连接

### 2.3 直播间信息 `GET /live/room/{roomId}`

公开接口（`@PublicApi`），返回 RoomVO。

### 2.4 首页直播列表 `GET /live/rooms`

公开接口，实时在线人数覆盖 DB 缓存值（从 `WsSessionManager` 读取）。

### 2.5 我的活跃直播 `GET /live/my-active-room`

需登录，查当前主播是否有直播中的房间。

---

## 3. Dubbo 服务 — `WsPushServiceImpl`

> **接口**: `com.jolumn.livemallcommon.api.WsPushService`

| 方法 | 说明 | 消费方 |
|------|------|--------|
| `push(userId, msg)` | 推送消息给用户所有会话 | user / seckill |
| `pushToRoom(roomId, msg)` | 推送给房间所有人 | seckill / leaderboard |
| `kickUser(userId, reason)` | 强制踢用户下线 | user |
| `kickDevice(userId, deviceId)` | 踢掉指定设备 | user |

**踢人流程**:
1. 发送 `KICK` 消息（含 reason）
2. VT 等待 3s 让客户端渲染踢人弹窗
3. 强制关闭 `session.close()`

---

## 4. gRPC 服务 — `SeckillPushGrpcService`

**端口**: 9090 | **服务名**: `SeckillPush`

**proto**: `seckill-push.proto`

```protobuf
rpc PushResult(SeckillPushRequest) returns (SeckillPushResponse)
```

- 秒杀服务通过 gRPC 推送秒杀结果到 WebSocket 节点
- 查找用户在线 session → 发送 `SEC_KILL_RESULT` JSON 消息
- 用户不在线 → 返回 `delivered=false, reason="user_offline"`

---

## 5. Kafka 消费者（预留）

- `spring.kafka.consumer.group-id=livemall-ws`
- 消费秒杀订单结果，驱动 `SEC_KILL_RESULT` 推送

---

## 6. 数据模型 — `t_live_room`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT (Auto) | 主键 |
| `title` | VARCHAR(80) | 直播标题 |
| `anchor_id` | BIGINT | 主播用户 ID |
| `anchor_name` | VARCHAR(50) | 主播昵称 |
| `category` | VARCHAR(20) | 分类 |
| `cover_color` | VARCHAR(100) | 封面颜色 |
| `status` | INT | 0=结束 1=直播中 |
| `online_count` | INT | 在线人数（DB 缓存） |
| `started_at` | DATETIME | 开播时间 |
| `ended_at` | DATETIME | 关播时间 |

---

## 7. Session 管理 — `WsSessionManager`

**三级索引**:

```
全局:  sessionId → WsSession
房间:  roomId → Map<sessionId, WsSession>
设备:  userId:deviceId → Set<sessionId>
```

| 方法 | 用途 |
|------|------|
| `getRoomOnline(roomId)` | 实时在线人数 |
| `findByUserId(userId)` | 用户所有会话（多设备/多 Tab） |
| `findByUserIdAndDeviceId(userId, deviceId)` | 踢设备时精确查找 |
| `totalOnline()` | 全局连接数 |

---

---

## 9. WebRTC 推流信令接口

> 主播端通过浏览器摄像头推流需要 WebRTC 信令服务，采用 WHEP（WebRTC-HTTP Egress Protocol）规范。

### 9.1 信令接口

#### `POST /live/webrtc/push` — 创建推流会话（WHEP）

| 权限 | 说明 |
|------|------|
| `@RequireRole(ANCHOR, ADMIN)` | 仅主播/管理员 |

**请求体**（`Content-Type: application/sdp`）:
```
v=0
o=- 4611726877911598307 2 IN IP4 127.0.0.1
s=-
t=0 0
...
```

**响应**（`Content-Type: application/sdp`）:
```
v=0
o=- 4611726877911598307 2 IN IP4 127.0.0.1
s=-
t=0 0
...
```
**Header** 额外返回:
```
WHIP-Session-Id: <uuid>
```

**逻辑**:
1. 接收前端 RTCPeerConnection 创建的 SDP Offer
2. 转发给媒体服务器（SRS/Janus/Mediasoup），获取 SDP Answer
3. 建立推流会话，返回 Answer
4. 媒体服务器将 WebRTC 流转换为 HTTP-FLV / HLS 供观众端播放

#### `POST /live/webrtc/trickle` — ICE 候选上报

**请求体**:
```json
{"sessionId":"<uuid>","candidate":{"candidate":"...","sdpMid":"0","sdpMLineIndex":0}}
```

**逻辑**:
1. 接收 ICE candidate 并转发给媒体服务器
2. 媒体服务器完成 NAT 穿透

#### `DELETE /live/webrtc/push/{sessionId}` — 关闭推流会话

| 权限 | 说明 |
|------|------|
| `@RequireRole(ANCHOR, ADMIN)` | 仅主播/管理员 |

**逻辑**:
1. 通知媒体服务器关闭推流会话
2. 清理推流资源

### 9.2 媒体服务器选择

| 方案 | 优点 | 缺点 |
|------|------|------|
| **SRS** | 高性能 WebRTC → HLS/FLV 转码，成熟社区 | 需要独立部署 |
| **Janus** | 灵活插件架构，WebRTC 网关 | 复杂度高 |
| **Nginx-RTMP + FFmpeg** | 传统方案，成本低 | WebRTC 需额外转码模块 |

### 9.3 架构

```
Vue 主播端                                     Stream Server (SRS)
  │                                                   │
  ├─ POST /live/webrtc/push (SDP Offer) ────────────▶│ 转 HLS/FLV
  │◀── SDP Answer ──────────────────────────────────│
  ├─ POST /live/webrtc/trickle ────────────────────▶│ ICE 穿透
  ├─ RTCPeerConnection (媒体数据) ──────────────────▶│ WebRTC 接收
  │                                                   │
  │                              ┌────────────────────┘
  │                              ▼
  │                      Vue 观众端 (flv.js / HLS.js)
  │                      GET /live/stream/{roomId}.flv
```

### 9.4 开播流程集成

```
前端 StudioPage                    后端 livemall-websocket     媒体服务器
  │                                     │                        │
  ├─ 1. POST /live/room/start ────────▶│                        │
  │◀── RoomVO                          │                        │
  │                                     │                        │
  ├─ 2. initCamera() → 获取摄像头      │                        │
  │                                     │                        │
  ├─ 3. POST /live/webrtc/push ───────▶│─ WHEP 转发 ──────────▶│
  │◀── SDP Answer + sessionId          │                        │
  │                                     │                        │
  ├─ 4. RTCPeerConnection 建立          │◀── ICE ──────────────│
  │   └─ 音视频数据传输                  │                        │
  │                                     │                        │
  ├─ 5. POST /live/room/start          │                        │
  │   {sessionId} 绑定流                │                        │
  │                                     │                        │
  └─ 6. 直播间状态 = 直播中            │                        │
```

### 9.5 注意事项

| 注意点 | 说明 |
|--------|------|
| **HTTPS 强制** | `getUserMedia` 要求 HTTPS（localhost 除外），生产环境必须配置 |
| **STUN/TURN** | 生产环境需部署 TURN 服务器（coturn）处理 NAT 穿透 |
| **镜像** | 前端预览 `transform: scaleX(-1)` 镜像，推流原画不镜像 |
| **美颜** | 如需美颜需引入 Canvas 中间层 + 第三方美颜 SDK |
| **回声消除** | `getUserMedia` 开启 `echoCancellation: true`，主播不进自己直播间 |

---

## 10. 安全设计

- 匿名双模式：无需登录即可观看直播
- 匿名操作限制：发弹幕/送礼/SEC_KILL 需先 AUTH 升级
- 连接上限保护：全局 200K 软上限
- 踢人流程：先通知后关闭，客户端有 3s 缓冲
- VT 并发控制：`Semaphore(200)` 限制广播和推送并发
