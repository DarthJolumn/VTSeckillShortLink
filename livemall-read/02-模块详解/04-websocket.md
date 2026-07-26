# WebSocket 长连接 (websocket)

> 端口 **8083** · Spring MVC + VT · Dubbo 端口 **20883** · Dubbo **Provider**

## 职责

开播/关播 / 进直播间 / 离开 / 在线人数 / 心跳 / 踢人 / 断线重连 / 弹幕 / 送礼 / 秒杀结果推送

## 类结构

```
endpoint/
└── LiveWebSocket.java          # JSR-356 @ServerEndpoint("/ws/live/{roomId}")
service/
├── LiveRoomService.java        # 直播间 REST 业务（开播/关播/房间信息）
├── LeaderboardServiceClient.java # Dubbo 排行榜客户端
└── WsPushServiceImpl.java      # Dubbo WsPushService 实现（推送给指定用户）
manager/
├── WsSessionManager.java       # 全局连接管理（ConcurrentHashMap + 房间索引）
├── WsHeartbeatManager.java     # 心跳超时清理（@Scheduled 10s）
└── DeviceTracker.java          # 单端观看踢人逻辑
config/
├── WebSocketConfig.java        # 注入 VT Executor 到 @ServerEndpoint
└── WebConfig.java              # REST 拦截器配置
```

## WebSocket 协议

### 客户端 → 服务端

```json
{"type":"PING"}
{"type":"AUTH","data":{"token":"..."}}
{"type":"BARRAGE","data":{"content":"..."}}
{"type":"GIFT","data":{"giftId":1,"quantity":1}}
{"type":"SEC_KILL","data":{"activityId":1}}
```

### 服务端 → 客户端

```json
{"type":"CONNECTED","data":{"anonymous":true,"displayName":"匿名用户","online":42}}
{"type":"AUTH_OK","data":{"userId":1,"role":0,"displayName":"xxx"}}
{"type":"AUTH_FAILED","data":{"reason":"Token 无效"}}
{"type":"BARRAGE","data":{"userId":1,"username":"xxx","content":"...","timestamp":...}}
{"type":"GIFT","data":{"userId":1,"username":"xxx","giftId":1,"timestamp":...}}
{"type":"ERROR","data":{"reason":"..."}}
```

## REST API

| Method | Path | 说明 | 鉴权 |
|--------|------|------|------|
| POST | `/live/rooms` | 开播 | JWT(ANCHOR) |
| PUT | `/live/room/{id}/close` | 关播 | JWT(ANCHOR) |
| GET | `/live/rooms` | 房间列表 | GET公开 |
| GET | `/live/room/{id}` | 房间详情 | GET公开 |

## 关键设计

- **VT**：`WebSocketConfig` 注入 `Executors.newVirtualThreadPerTaskExecutor()`
- **连接上限**：`ws.max-sessions=200000`，超限返回 TRY_AGAIN_LATER
- **匿名观看**：未携带 Token 或 Token 无效 → 匿名连接，仅能看弹幕不能发言
- **AUTH 升级**：连接后可通过 `AUTH` 消息提供 Token 升级为认证用户
- **在线统计**：`WsSessionManager` 实时维护房间人数，`@Scheduled(30s)` 同步到 Redis
- **秒杀结果推送**：消费 `ws:push:seckill-result` 频道（Redis Pub/Sub）推送秒杀结果给用户
- **单向依赖**：`seckill` → `websocket`，直播间 `roomId` 与秒杀活动关联
