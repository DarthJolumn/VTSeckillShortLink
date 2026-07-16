# 05 — WebSocket 实时通信

> **目标：** 理解 WebSocket 长连接的设计，能说清楚连接管理、心跳、跨节点路由、弹幕处理。

---

## 1. 为什么用 WebSocket

```
HTTP 轮询：客户端每 N 秒问一次"有新消息吗？" → 99% 请求白费
HTTP 长轮询：客户端等，有消息才返回 → 半双工，服务器不能主动推
WebSocket：全双工，服务端随时推 → 弹幕/秒杀结果/踢人都是服务端主动推的

WebSocket 头部仅 2-6 字节，HTTP 头部动辄几百字节。
100w 连接 × 每 30s 心跳 → HTTP 光头部就 100w × 1KB = 1GB/s 带宽浪费
```

## 2. 技术模型

```
@ServerEndpoint("/ws/live/{roomId}")
每个连接 = 一个 LiveWebSocket 实例 + 一个 VT

@OnOpen  → 握手，验 token（可选），创建 WsSession
@OnMessage → 消息分发：PING/PONG/BARRAGE/GIFT/AUTH
@OnClose → 清理 session
@OnError → 记录日志

VT 配置（关键）：
  @Bean ServletServerContainerFactoryBean
    → setExecutor(Executors.newVirtualThreadPerTaskExecutor())
  不设此配置则跑在 Tomcat 默认线程池
```

## 3. 连接认证（匿名 + 升级）

该项目支持**匿名观看**和**按需登录**：

```
连接建立时：
  - URL 带 token → 解析 JWT → 认证连接（可发弹幕/送礼/抢购）
  - URL 不带 token → 匿名连接（只能看，不能互动）

匿名升级：
  客户端发 AUTH 消息 → 服务端解析 token → WsSession.upgrade()
  → 从匿名升级为认证用户
```

**代码证据（`LiveWebSocket.java`）：**
```java
@OnOpen
public void onOpen(Session session, @PathParam("roomId") Long roomId) {
    String token = extractTokenFromQuery(session.getQueryString());
    // 有 token → 验签提取 userId/role
    // 无 token → 匿名连接
    WsSession ws = new WsSession(session, roomId, userId, role);
    sessionManager.add(ws);
}

@OnMessage
public void onMessage(Session session, String message) {
    if ("PING".equals(type)) { /* PONG */ return; }
    if ("AUTH".equals(type)) { handleAuth(ws, session, msg); return; }
    if (ws.isAnonymous()) { /* 拒绝：请先登录 */ return; }
    // BARRAGE / GIFT / SEC_KILL ...
}
```

## 4. 连接管理

```
WsSessionManager:
  ConcurrentHashMap<String, WsSession> sessions  // sessionId → WsSession
  ConcurrentHashMap<Long, Set<String>> roomMap   // roomId → Set<sessionId>

Redis:
  room:{roomId} → Set<userId>   // 房间在线用户
  ws:route:{userId} → {nodeId}:{sessionId}  // 跨节点路由
```

## 5. 心跳检测

```
客户端 → 每 30s 发 PING
服务端 → 收到 PING 回 PONG，更新 lastActiveTime
服务端 → @Scheduled 每 10s 扫描 → lastActiveTime > 60s → 关闭连接

为什么是 30s/60s：
  - 太频繁：浪费带宽（100w 连接 × 每 5s = 200 QPS 无用流量）
  - 太久：死连接占用资源时间长
  - 30s 是业界通用值
```

## 6. 断线重连

**客户端（`ws-client.js`）：**
```javascript
// 指数退避重连
RECONNECT_BACKOFF = [1000, 2000, 4000, 8000, 16000, 30000]
// 1s → 2s → 4s → 8s → 16s → 30s

// 最多重试 10 次，之后放弃
if (reconnectCount >= WS_RECONNECT_MAX) { /* 彻底关闭 */ }
```

**服务端离线消息：**
```
Redis List: offline:{userId}
TTL: 5min（5min 不重连则丢弃）

推送前判断 session 是否存活：
  - 存活 → 直接发
  - 已断开 → 追加到 offline:{userId}

重连成功后拉取离线消息 → 发送 → DELETE offline:{userId}
```

## 7. 跨节点路由

**问题：** 多节点部署时，user A 在节点 1，user B 在节点 2，A 要给 B 发消息怎么路由？

**方案：**
```
1. 连接时 → Redis SET ws:route:{userId} = {nodeId}:{sessionId}
2. 推送时 → 查 Redis 路由表：
   - 本节点 → 直接发
   - 其他节点 → Dubbo RPC WsPushService.push(userId, message)
3. 心跳时 → 续期 TTL（30min）
```

**WsPushService（Dubbo 接口）：**
```java
public interface WsPushService {
    boolean push(Long userId, String message);        // 单用户推送
    boolean pushToRoom(Long roomId, String message);  // 房间广播
    boolean kickDevice(Long userId, String sessionId); // 踢人
}
```

## 8. 弹幕处理

```
客户端发送 → {"type": "BARRAGE", "data": {"content": "666"}}
  → 幂等去重: Redis SETNX barrage:{messageId} 1 EX 300（5min）
  → DFA 敏感词过滤（查 Trie 树，命中则替换为 ***）
  → 房间广播
  → 异步写 MySQL t_barrage（历史回放）
```

**为什么服务端过滤：** 客户端可被篡改，服务端是安全底线。

## 9. 踢人

```
新连接建立 → 查 Redis ws:route:{userId}：
  → 已有旧 session → 发 KICK 消息给旧连接 → 等 3s → 强制 close
  → 更新 Redis ws:route:{userId}
```

## 10. 百万连接的内存评估

```
每个 WebSocket Session 约 1-2KB
  百万连接 = 1-2 GB（主要是 TCP 缓冲区，不是堆内存）
  
VT 内存：每个 VT 栈按需分配，初始约 1KB
  百万 VT = 约 1GB

总计：百万连接约 2-3GB 内存
```

---

## 面试追问速答

| 追问 | 回答 |
|------|------|
| HTTP 轮询 vs WebSocket | 轮询 99% 请求白费，WS 全双工 + 头部 2-6 字节 |
| @ServerEndpoint 线程安全吗 | 每个连接一个实例，天然线程安全 |
| VT 在 WS 的优势 | 一个连接一个 VT，无线程池限制，内存按需分配 |
| 用什么 JSON 库 | Jackson ObjectMapper |
| 消息去重怎么做的 | Redis SETNX 防重 + 客户端 messageId |
| 为什么不用 Netty | Spring 封装好了，VT 屏蔽 IO 差异，没必要自建 |
