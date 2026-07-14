# WebSocket 模块实现设计 (livemall-websocket)

> 综合 `2.4-WebSocket长连接.md`、`3.2.x` 直播功能、`3.3.x` 弹幕礼物功能、`2.7-数据库设计.md`、`2.8-接口文档.md`，
> 以及前端 `ws-client.js` / `stores/live.js` / `constants/index.js` 的对齐需求，制定本实现设计。

---

## 一、模块定位

`livemall-websocket` 是项目中**唯一**的长连接服务，承担：

| 职责 | 功能编号 |
|------|---------|
| 直播流控制 | 3.2.1 开播 / 3.2.2 关播 |
| 房间管理 | 3.2.3 进直播间 / 3.2.4 离开直播间 |
| 连接维护 | 3.2.5 在线人数 / 3.2.6 心跳 / 3.2.7 踢人 / 3.2.8 断线重连 |
| 弹幕与礼物 | 3.3.1 发送弹幕 / 3.3.2 送礼物 |
| 跨服务推送 | 3.4.5 秒杀结果推送（Dubbo WsPushService）|
| 封禁推送 | 管理员封禁 → WS 踢下线 |

**端口**：8083（HTTP REST 开播/关播 + WebSocket 长连接共用）  
**协议**：Spring MVC + JSR-356 `@ServerEndpoint` + Virtual Threads  
**注册中心**：Nacos（服务名 `livemall-websocket`）  
**Gateway 路由**：`/ws/**` → `livemall-websocket`（lb:ws）

---

## 二、包结构与文件清单

```
com.jolumn.livemallwebsocket/
├── LivemallWebsocketApplication.java          # 入口（已有）
│
├── config/
│   ├── WebSocketConfig.java                   # VT Executor 注入 + ServerEndpointExporter
│   └── GiftConfig.java                        # 礼物配置缓存（@PostConstruct 加载）
│
├── endpoint/
│   └── LiveWebSocket.java                     # @ServerEndpoint("/ws/live/{roomId}")
│                                              #   @OnOpen / @OnMessage / @OnClose / @OnError
│
├── manager/
│   ├── WsSessionManager.java                  # 全局 session 注册表 + 心跳时间戳
│   └── RoomManager.java                       # 房间 → session 集合映射 + 容量管理
│
├── handler/
│   ├── WsMessageHandler.java                  # @OnMessage 分发（PING/BARRAGE/GIFT）
│   ├── BarrageHandler.java                    # 弹幕处理：去重 → DFA → 广播 → 异步入库
│   └── GiftHandler.java                       # 礼物处理：去重 → 广播 → Dubbo加分 → 异步入库
│
├── service/
│   ├── LiveRoomService.java                   # 开播/关播 REST 业务逻辑
│   ├── OnlineCountService.java                # 在线人数统计 + 定时落库
│   └── ReconnectService.java                  # 离线消息管理（Redis List）
│
├── push/
│   └── WsPushServiceImpl.java                 # @DubboService 实现 WsPushService 接口
│
├── entity/
│   ├── LiveRoom.java                          # t_live_room 实体
│   ├── Barrage.java                           # t_barrage 实体
│   └── GiftLog.java                           # t_gift_log 实体
│
├── mapper/
│   ├── LiveRoomMapper.java                    # MyBatis-Plus Mapper
│   ├── BarrageMapper.java
│   └── GiftLogMapper.java
│
├── dto/
│   ├── WsMessage.java                         # WS 消息封装 { type, data, messageId, timestamp }
│   └── GiftItem.java                          # 礼物配置项 { id, name, price, icon }
│
└── task/
    ├── HeartbeatTask.java                     # @Scheduled 10s 扫描超时连接
    └── OnlineSyncTask.java                    # @Scheduled 30s 在线人数落库
```

---

## 三、核心组件设计

### 3.1 WebSocketConfig — VT Executor 注入

```java
@Configuration
public class WebSocketConfig {

    @Bean
    public ServletServerContainerFactoryBean websocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        container.setMaxTextMessageBufferSize(8192);
        container.setMaxBinaryMessageBufferSize(0);
        container.setMaxSessionIdleTimeout(120_000L); // 120s 无消息自动断开
        return container;
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
```

**关键约束**：
- 不设 `setExecutor` → `@OnOpen/@OnMessage/@OnClose` 跑在 Tomcat 线程池，不是 VT
- `setMaxSessionIdleTimeout(120s)` 是兜底，正常心跳 30s 不会触发

### 3.2 WsSessionManager — 全局 Session 注册表

```java
@Component
public class WsSessionManager {

    // sessionId → Session
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    // sessionId → lastHeartbeatTimestamp
    private final ConcurrentHashMap<String, Long> lastHeartbeats = new ConcurrentHashMap<>();
    // sessionId → userId（快速反查，避免每次解析 Session userProperties）
    private final ConcurrentHashMap<String, Long> sessionUserMap = new ConcurrentHashMap<>();
    // sessionId → roomId
    private final ConcurrentHashMap<String, Long> sessionRoomMap = new ConcurrentHashMap<>();

    public void add(Session session, Long userId, Long roomId) { ... }
    public void remove(String sessionId) { ... }
    public Session get(String sessionId) { ... }
    public Long getUserId(String sessionId) { ... }
    public Long getRoomId(String sessionId) { ... }
    public void updateHeartbeat(String sessionId) { ... }
    public Long getLastHeartbeat(String sessionId) { ... }

    // 快照：避免迭代时并发修改
    public Map<String, Session> getSnapshot() { return new HashMap<>(sessions); }
    public int getTotalSessions() { return sessions.size(); }
}
```

### 3.3 RoomManager — 房间 → Session 映射

```java
@Component
public class RoomManager {

    // roomId → Set<sessionId>
    private final ConcurrentHashMap<Long, Set<String>> roomSessions = new ConcurrentHashMap<>();

    public void join(Long roomId, String sessionId) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void leave(Long roomId, String sessionId) {
        Set<String> set = roomSessions.get(roomId);
        if (set != null) {
            set.remove(sessionId);
            if (set.isEmpty()) roomSessions.remove(roomId);
        }
    }

    public Set<String> getMembers(Long roomId) {
        return roomSessions.getOrDefault(roomId, Collections.emptySet());
    }

    public int getOnlineCount(Long roomId) {
        Set<String> set = roomSessions.get(roomId);
        return set == null ? 0 : set.size();
    }

    public void closeAll(Long roomId) {
        Set<String> members = roomSessions.remove(roomId);
        if (members != null) {
            for (String sid : members) {
                // 由调用方负责关闭 session
            }
        }
    }
}
```

**容量管理**：`MAX_SESSIONS = 200_000`（约 200MB 内存），超出时 FIFO 淘汰最旧连接。

### 3.4 LiveWebSocket — @ServerEndpoint

```java
@ServerEndpoint("/ws/live/{roomId}")
@Component
public class LiveWebSocket {

    // 注意：@ServerEndpoint 每个连接一个实例，不能用 Spring 注入
    // 通过 SpringConfigurator + ApplicationContext 获取 Bean
    private static ApplicationContext ctx;

    @OnOpen
    public void onOpen(Session session,
                       @PathParam("roomId") Long roomId,
                       @QueryParam("token") String token) {
        // 1. JWT 验证 → userId, role
        // 2. 校验房间存在且 status=1（开播中）
        // 3. 踢旧连接（3.2.7 逻辑）
        // 4. 注册到 WsSessionManager + RoomManager
        // 5. Redis: SADD room:members:{roomId} userId
        // 6. Redis: SET ws:route:{userId} {nodeId}:{sessionId} EX 30min
        // 7. 拉取离线消息（3.2.8）
        // 8. 广播 ONLINE_COUNT 更新
    }

    @OnMessage
    public void onMessage(Session session, String message,
                          @PathParam("roomId") Long roomId) {
        // 委托给 WsMessageHandler 分发
    }

    @OnClose
    public void onClose(Session session, @PathParam("roomId") Long roomId) {
        // 清理 WsSessionManager + RoomManager + Redis
        // 广播 ONLINE_COUNT 更新
    }

    @OnError
    public void onError(Session session, Throwable error,
                        @PathParam("roomId") Long roomId) {
        // 记录日志，触发 onClose 清理
    }
}
```

**Spring Bean 获取**：由于 `@ServerEndpoint` 每个连接创建新实例，无法直接 `@Autowired`。通过 `SpringConfigurator` 或静态 `ApplicationContext` 引用获取 Bean：

```java
@ServerEndpoint(value = "/ws/live/{roomId}", configurator = SpringConfigurator.class)
```

### 3.5 WsMessageHandler — 消息分发

```java
@Component
public class WsMessageHandler {

    public void handle(Session session, String rawMessage, Long roomId) {
        WsMessage msg = JsonUtil.parse(rawMessage, WsMessage.class);

        switch (msg.getType()) {
            case "PING"    -> handlePing(session);
            case "BARRAGE" -> barrageHandler.handle(session, msg, roomId);
            case "GIFT"    -> giftHandler.handle(session, msg, roomId);
            default        -> log.warn("未知消息类型: {}", msg.getType());
        }
    }

    private void handlePing(Session session) {
        wsSessionManager.updateHeartbeat(session.getId());
        session.getAsyncRemote().sendText("{\"type\":\"PONG\"}");
    }
}
```

### 3.6 房间广播 — AsyncRemote 无锁

```java
// 在 LiveWebSocket 或 RoomManager 中
public void broadcast(Long roomId, WsMessage message) {
    String json = JsonUtil.toJson(message);
    Set<String> members = roomManager.getMembers(roomId);

    for (String sid : members) {
        Session s = wsSessionManager.get(sid);
        if (s != null && s.isOpen()) {
            // ★ VT 下必须用 AsyncRemote，禁止 synchronized + getBasicRemote
            s.getAsyncRemote().sendText(json);
        }
    }
}
```

---

## 四、角色权限设计

### 4.1 角色定义（对齐前端 `constants/index.js`）

| 角色 | role 值 | 权限 |
|------|---------|------|
| 观众 (AUDIENCE) | 1 | 进直播间、发弹幕、送礼物、看排行、抢购 |
| 主播 (ANCHOR) | 2 | 观众权限 + 开播/关播 |
| 管理员 (ADMIN) | 3 | 主播权限 + 封禁用户、强制关播 |

### 4.2 权限校验点

| 操作 | 校验方式 | 失败响应 |
|------|---------|---------|
| WS 连接 | JWT 验签 → userId + role | close(4001) |
| 开播 `PUT /live/room/{roomId}/start` | role ∈ {2,3} + room.anchor_id == userId | 403 |
| 关播 `PUT /live/room/{roomId}/stop` | role ∈ {2,3} + room.anchor_id == userId | 403 |
| 发弹幕 | role ∈ {1,2,3}（所有角色） | 静默丢弃 |
| 送礼物 | role ∈ {1,2,3}（所有角色） | 静默丢弃 |
| 管理员封禁推送 | 由 user 服务触发，WS 服务执行踢下线 | — |

### 4.3 JWT 中的角色传递

前端连接 WS 时在 query 中传 Access Token：

```
ws://host:8083/ws/live/{roomId}?token=<AccessToken>
```

`@OnOpen` 中通过 `JwtUtil.parse(token)` 提取：
- `claims.getSubject()` → userId
- `claims.get("role", Integer.class)` → role

角色信息存入 `session.getUserProperties()`，后续消息处理时直接读取，无需重复解析 JWT。

### 4.4 前端角色联动

| 前端页面 | 角色 | WS 交互 |
|---------|------|---------|
| `LiveRoom.vue` | 观众/主播/管理员 | 连接 WS，收发弹幕/礼物/排行/秒杀结果 |
| `StreamConsole.vue` | 主播/管理员 | **摄像头通过 `getUserMedia()` 在浏览器本地获取**，不通过 WS 传输视频流 |
| `Dashboard.vue` | 管理员 | 不连接 WS，通过 REST API 获取数据 |

> **重要**：本项目**不传输视频流**。摄像头画面仅主播本地可见（`StreamConsole.vue` 的 `<video>` 预览），观众端看到的是模拟播放器 + WS 实时数据（弹幕/礼物/排行/秒杀）。这是秋招演示项目的合理简化。若需真实推流，需引入 SRS/Nginx-RTMP 等流媒体服务器，不在当前范围内。

---

## 五、Redis Key 设计

| Key | 类型 | TTL | 用途 |
|-----|------|-----|------|
| `room:members:{roomId}` | Set | 随房间生命周期 | 房间在线用户 ID 集合 |
| `ws:route:{userId}` | String | 30min | 跨节点路由：`{nodeId}:{sessionId}` |
| `offline:{userId}` | List | 5min | 离线消息缓冲（max 100 条）|
| `barrage:{messageId}` | String | 5min | 弹幕幂等去重 |
| `gift:{messageId}` | String | 5min | 礼物幂等去重 |
| `lock:online_sync` | String | 5s | 在线人数落库分布式锁 |

---

## 六、MySQL 表（已有 DDL）

| 表 | 用途 | 写入时机 |
|----|------|---------|
| `t_live_room` | 直播间状态 | 开播/关播时 UPDATE |
| `t_barrage` | 弹幕历史 | 异步 VT 写入（广播后） |
| `t_gift_log` | 礼物流水 | 异步 VT 写入（广播后） |

---

## 七、Dubbo 接口

### 7.1 WsPushService（common 模块定义，websocket 模块实现）

```java
// com.jolumn.livemallcommon.service.WsPushService（需新建）
public interface WsPushService {
    boolean push(Long userId, String message);
    boolean pushToRoom(Long roomId, String message);
    boolean kickUser(Long userId, String reason);
}
```

```java
// livemall-websocket 实现
@DubboService
public class WsPushServiceImpl implements WsPushService {
    // push: 查 ws:route:{userId} → 本节点直接发 / 跨节点 Dubbo 转发
    // pushToRoom: 查 RoomManager 本地广播
    // kickUser: 发 KICK 消息 + 关闭 session
}
```

### 7.2 LeaderboardService（leaderboard 模块提供，websocket 模块消费）

```java
// 送礼时 Dubbo 调用
@DubboReference(check = false)
private LeaderboardService leaderboardService;

// 在 GiftHandler 中
leaderboardService.addScore(activityId, userId, "GIFT", totalAmount);
```

---

## 八、定时任务

### 8.1 HeartbeatTask — 心跳超时扫描

```java
@Scheduled(fixedRate = 10_000) // 10s
public void checkHeartbeat() {
    long now = System.currentTimeMillis();
    Map<String, Session> snapshot = wsSessionManager.getSnapshot();

    for (Map.Entry<String, Session> entry : snapshot.entrySet()) {
        Long lastBeat = wsSessionManager.getLastHeartbeat(entry.getKey());
        if (lastBeat != null && (now - lastBeat) > 60_000) {
            // ★ 异步 VT 执行 close，不阻塞 @Scheduled 线程
            Thread.startVirtualThread(() -> {
                try { entry.getValue().close(); }
                catch (Exception e) { log.warn("Heartbeat close failed: {}", e.getMessage()); }
            });
        }
    }
}
```

### 8.2 OnlineSyncTask — 在线人数落库

```java
@Scheduled(fixedRate = 30_000) // 30s
public void syncOnlineCount() {
    // 分布式锁：仅 leader 节点写
    Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent("lock:online_sync", "1", 5, TimeUnit.SECONDS);
    if (!Boolean.TRUE.equals(locked)) return;

    try {
        for (Long roomId : roomManager.getActiveRooms()) {
            Long count = redisTemplate.opsForSet().size("room:members:" + roomId);
            liveRoomMapper.updateOnlineCount(roomId, count == null ? 0 : count.intValue());
        }
    } finally {
        redisTemplate.delete("lock:online_sync");
    }
}
```

---

## 九、消息协议（对齐前端 WS_TYPE）

### 9.1 上行消息（客户端 → 服务端）

| type | data | 说明 |
|------|------|------|
| `PING` | `{}` | 心跳（30s 间隔）|
| `BARRAGE` | `{messageId, content}` | 发弹幕 |
| `GIFT` | `{messageId, giftId, quantity}` | 送礼物 |
| `SEC_KILL` | `{activityId, reqId}` | 秒杀请求（透传给 seckill 服务）|

### 9.2 下行消息（服务端 → 客户端）

| type | data | 说明 |
|------|------|------|
| `PONG` | `{}` | 心跳回复 |
| `BARRAGE` | `{messageId, userId, nickname, content, timestamp}` | 弹幕广播 |
| `GIFT` | `{messageId, userId, nickname, giftName, quantity, timestamp}` | 礼物特效 |
| `SEC_KILL_RESULT` | `{orderNo, success, msg, reqId}` | 秒杀结果 |
| `KICK` | `{reason}` | 踢下线 |
| `ONLINE_COUNT` | `{count}` | 在线人数更新 |
| `BAN` | `{}` | 账号封禁 |
| `ROOM_CLOSED` | `{}` | 主播关播 |

### 9.3 WsMessage 统一封装

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WsMessage {
    private String type;
    private Map<String, Object> data;
    private String messageId;
    private Long timestamp;

    public static WsMessage of(String type, Map<String, Object> data) {
        return new WsMessage(type, data, null, System.currentTimeMillis());
    }

    public static WsMessage of(String type, Map<String, Object> data, String messageId) {
        return new WsMessage(type, data, messageId, System.currentTimeMillis());
    }
}
```

---

## 十、REST 接口（HTTP）

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| `PUT` | `/live/room/{roomId}/start` | JWT(role∈{2,3}) | 开播 |
| `PUT` | `/live/room/{roomId}/stop` | JWT(role∈{2,3}) | 关播 |

> Gateway 路由：`/live/**` → `livemall-websocket`（需在 Gateway 配置中添加此路由）

---

## 十一、优雅停机

```java
@PreDestroy
public void gracefulShutdown() {
    // 1. 广播 ROOM_CLOSED 给所有连接
    for (Long roomId : roomManager.getActiveRooms()) {
        broadcast(roomId, WsMessage.of("ROOM_CLOSED", Map.of()));
    }
    // 2. 等待 5s 让客户端优雅断开
    try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    // 3. 强制关闭所有 session
    wsSessionManager.closeAll();
}
```

配合 `application.yml`：
```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

---

## 十二、降级方案

| 故障场景 | 降级动作 | 用户感知 |
|---------|---------|---------|
| Redis 挂了 | 路由失效 → 跨节点推送全断；房间管理切本地 Map | 跨节点消息丢失，在线人数不准 |
| 本节点宕机 | Nacos 摘除，客户端重连到其他节点 | 短暂断线，重连恢复 |
| 离线消息 Redis 满 | LTRIM 淘汰旧消息 | 旧消息丢失 |
| Dubbo 排行榜不可用 | try-catch 记录日志，礼物广播正常 | 排行不加分但礼物特效正常 |
| MySQL 写入失败 | 异步入库失败不影响广播 | 弹幕/礼物实时正常，历史可能丢失 |

---

## 十三、实现顺序

| 步骤 | 内容 | 依赖 |
|------|------|------|
| 1 | common 模块新增 `WsPushService` 接口 + `WsMessage` DTO | — |
| 2 | `WebSocketConfig` + `WsSessionManager` + `RoomManager` | 步骤 1 |
| 3 | `LiveWebSocket`（@OnOpen 认证 + 踢旧 + 注册 + 离线消息）| 步骤 2 |
| 4 | `WsMessageHandler` + `HeartbeatTask`（PING/PONG）| 步骤 3 |
| 5 | `LiveRoomService` + `LiveRoomController`（开播/关播 REST）| 步骤 2 |
| 6 | `BarrageHandler`（弹幕：去重 → DFA → 广播 → 异步入库）| 步骤 4 |
| 7 | `GiftHandler`（礼物：去重 → 广播 → Dubbo加分 → 异步入库）| 步骤 4 |
| 8 | `OnlineCountService` + `OnlineSyncTask` | 步骤 2 |
| 9 | `WsPushServiceImpl`（Dubbo 实现）| 步骤 2 |
| 10 | `ReconnectService`（离线消息 Redis 管理）| 步骤 2 |
| 11 | Gateway 路由补充 `/live/**` → `livemall-websocket` | 步骤 5 |
| 12 | 单元测试 + 集成测试 | 全部 |

---

## 十四、前端对齐检查

| 前端代码 | 后端需对齐 |
|---------|-----------|
| `ws-client.js` 连接 URL：`/ws/live/{roomId}?token=...` | `@ServerEndpoint("/ws/live/{roomId}")` + `@QueryParam("token")` |
| `ws-client.js` 心跳：30s PING，60s PONG 超时 | `HeartbeatTask` 10s 扫描，60s 超时断开 |
| `ws-client.js` 重连：指数退避 1s→2s→4s→8s→16s→30s，max 10 次 | 服务端离线消息 TTL 5min 覆盖重连窗口 |
| `stores/live.js` 订阅 `BARRAGE_DOWN`/`GIFT_DOWN`/`ONLINE_COUNT`/`KICK`/`BAN`/`ROOM_CLOSED` | 下行消息 type 必须与前端 `WS_TYPE` 常量一致 |
| `stores/live.js` 发送 `BARRAGE`/`GIFT`/`SEC_KILL` | `@OnMessage` 必须处理这三种上行类型 |
| `constants/index.js` `WS_TYPE` 枚举 | 后端消息 type 字符串必须完全匹配 |

---

## 关联文档

- 模块级设计：`2.4-WebSocket长连接.md`
- 功能实现：`3.2.1~3.2.8`、`3.3.1~3.3.2`
- 数据库：`2.7-数据库设计.md` §直播域
- 接口协议：`2.8-接口文档.md` §三、WebSocket
- 前端对齐：`livemall-ui-vue/src/infra/ws-client.js`、`livemall-ui-vue/src/stores/live.js`
