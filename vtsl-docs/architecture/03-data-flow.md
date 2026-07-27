# 核心数据流

## 一、秒杀抢购

> 文件: `vtsl-seckill/.../controller/SeckillController.java:72-102`

```
用户 → Gateway(JWT验签, 注入 X-User-Id) → POST /seckill/order
  │
  ├─ SeckillController.placeOrder()
  │   ├─ SnowflakeIdGenerator.nextOrderNo()       // 分布式唯一订单号
  │   │
  │   └─ SeckillService.placeOrder()
  │       ├─ ActivityCacheService.getActivity()   // L1 Caffeine 查活动
  │       ├─ 校验活动状态(进行中)与时间范围
  │       │
  │       └─ StockService.deduct()                // Redis Lua 原子扣减
  │           └─ EVALSHA deduct_stock.lua
  │               ├─ KEYS[1] 不存在 → -3 (未初始化)
  │               ├─ ordered key 存在 → -1 (重复参与)
  │               ├─ DECR stock ≥ 0 → SETEX ordered 24h → 200 (成功)
  │               └─ DECR stock < 0 → INCR 补偿 → -2 (库存不足)
  │
  ├─ result = 200 →
  │   kafkaTemplate.send("seckill-order", msg)    // 异步，不阻塞
  │
  └─ result ≠ 200 → 直接返回错误（不丢 Kafka）
```

### Kafka 消费端

> 文件: `vtsl-seckill/.../consumer/SeckillOrderConsumer.java:45-106`

```
SeckillOrderConsumer.onMessage()
  │
  ├─ SEM.acquire()                                // Semaphore 背压 (上限 30)
  │
  └─ Thread.startVirtualThread(() -> {
      │
      ├─ ActivityCacheService.getActivity()       // 二次检查活动
      │
      ├─ SeckillService.createOrder()             // @Transactional
      │   └─ orderRepo.save(order)                // INSERT t_seckill_order
      │
      ├─ redisTemplate.convertAndSend(            // Redis Pub/Sub 推送结果
      │     "ws:push:seckill-result", msg)         // → WebSocket 广播
      │
      └─ ack.acknowledge()                        // 手动 ACK，消费成功才提交

      异常处理:
      ├─ DuplicateKeyException → ack（唯一索引幂等，不重复入库）
      ├─ TransactionException → 内存重试 3 次 → 超限后 ack + 日志
      └─ 其他异常 → ack + 日志（避免消费卡死）
      })                                          // 结束后 SEM.release()
```

### 取消订单

> 文件: `vtsl-seckill/.../service/SeckillService.java:142-157`

```
cancelOrder(orderNo, userId)                      // @Transactional
  ├─ orderRepo.findByOrderNo(orderNo)             // 查找订单
  ├─ 校验归属(userId)与状态(status=0)
  ├─ order.setStatus(2) + orderRepo.save()        // DB 先（@Version CAS 防并发）
  └─ stockService.refund()                        // Redis 后（Lua EXISTS 幂等）
```

### 超时取消调度器

> 文件: `vtsl-seckill/.../scheduler/TimeoutCancelScheduler.java:39-62`

```
TimeoutCancelScheduler (@Scheduled fixedDelay=15s)
  └─ PageRequest.of(0, batchSize=500)             // 分页，防止 OOM
       └─ 对每个超时订单:
            Thread.startVirtualThread(() ->
              cancelOrder(order)
            )
```

### 对账补偿调度器

> 文件: `vtsl-seckill/.../scheduler/ReconciliationScheduler.java:41-66`

```
ReconciliationScheduler (@Scheduled fixedDelay=5min)
  └─ 查找最近 60min 内 status=2 的订单
       └─ 检查 Redis ordered key 是否残留
            ├─ 存在 → 调用 refund Lua 回补库存
            └─ 不存在 → 已回补，跳过
```

---

## 二、短链解析

> 文件: `vtsl-shortlink/.../service/ShortLinkService.java:128-222`

```
GET /s/{shortCode}
  │
  ├─ RateLimitService.tryAcquire()                // Lua 滑动窗口
  │   ├─ 短码维度: 1000 次/分钟
  │   └─ IP 维度: 100 次/分钟
  │
  └─ ShortLinkService.getOriginalUrl(shortCode)
      │
      ├─ ShortLinkCache.isBlocked()               // O(1) 封禁检查
      │
      ├─ ShortLinkCache.getFromL1()               // L1 Caffeine 3s TTL
      │   └─ 命中 → 直接返回
      │
      ├─ ShortLinkCache.getFromL2()               // L2 Redis Hash 24h TTL
      │   └─ 命中 → 回填 L1 → 返回
      │
      └─ DCL 防击穿:
           ├─ 获得 SETNX lock (5s TTL) →
           │   resolveFallback():
           │   ├─ 算法码 (P/A/L 前缀):
           │   │   ├─ ShortCodeCodec.decode()     // Base58 解码得 productId
           │   │   └─ Dubbo RPC ProductShortLinkService
           │   │       → getProductUrl(productId)  // 返回 /product/{id}
           │   │
           │   └─ DB 码:
           │       └─ ShortLinkRepository.findByShortCode()
           │
           │   → 回填 L1 + L2
           │   → DEL lock
           │
           └─ 未获得锁 → spinWaitForCache()       // 自旋 20×50ms = 1s
                └─ 等待后重新读 L1/L2
      │
      └─ incrementClickCount()                    // Redis HINCRBY
           └─ Kafka → MySQL 按天持久化
```

### 短链创建

> 文件: `vtsl-shortlink/.../service/ShortLinkService.java:72-96`

```
doCreate()
  ├─ md5(originalUrl) → urlHash
  ├─ 检查 L2 Redis: s:hash:{urlHash} → 已存在则直接返回
  ├─ 检查 DB: 已存在则回填 Redis + 返回
  └─ generateWithRetry():
      ├─ IdGenerator.nextCode() → Redis INCR → Base62 编码
      ├─ 写入 DB (DuplicateKey 重试 3 次)
      └─ putHashMapping(): Redis SET s:hash:{urlHash} → shortCode (24h TTL)
```

---

## 三、用户上下文传递（ScopedValue）

> 文件: `vtsl-common/.../context/UserContext.java:5-7`, `filter/UserContextFilter.java:22-49`

```
请求 → Gateway JwtAuthGlobalFilter (Order=-5)
  │ JWT 验签 → 解析 subject/role
  │ ServerHttpRequest.mutate().header(...)
  └─ headers: X-User-Id, X-User-Role, X-Device-Id

→ 路由转发 → lb://vtsl-xxx

→ 下游 Servlet 服务
  └─ UserContextFilter.doFilter()
      ├─ request.getHeader("X-User-Id") → userId
      ├─ request.getHeader("X-User-Role") → role
      ├─ request.getHeader("X-Device-Id") → deviceId
      └─ ScopedValue.where(USER_ID, userId)
                    .where(ROLE, role)
                    .where(DEVICE_ID, deviceId)
                    .run(chain::doFilter)          // 整个请求生命周期内可用

→ AuthInterceptor.preHandle()                     // 二次校验
  ├─ @PublicApi → 放行
  ├─ @RequireAuth + userId==null → 401
  └─ @RequireRole → 角色校验

→ Controller → UserContext.currentUserId()         // ScopedValue.get()
```

### 使用范围

| Controller | 取用户方式 | 文件 |
|-----------|-----------|------|
| UserController | `UserContext.currentUserId()` | `vtsl-user/.../controller/UserController.java:31` |
| ProductController | `UserContext.currentUserId()` | `vtsl-seckill/.../product/controller/ProductController.java:33` |
| ShortLinkController | `UserContext.currentUserId()` | `vtsl-shortlink/.../controller/ShortLinkController.java:96` |
| LiveRoomController | `UserContext.currentUserId()` | `vtsl-websocket/.../controller/LiveRoomController.java:40` |
| SeckillController | `@RequestHeader("X-User-Id")` | `vtsl-seckill/.../controller/SeckillController.java:74` |
| LeaderboardController | `@PathVariable/@RequestParam` | `vtsl-leaderboard/.../controller/LeaderboardController.java:24` |

---

## 四、弹幕与送礼

### 弹幕发送

```
用户(WS) → LiveWebSocket.onMessage("BARRAGE")
  └─ broadcastToRoom() → 广播给同房间所有 WS 连接
```

### 送礼 + 排行榜

```
用户(WS) → LiveWebSocket.onMessage("GIFT")
  ├─ broadcastToRoom() → 广播送礼特效
  └─ Thread.startVirtualThread() → Dubbo LeaderboardService.addScore()
       └─ Redis ZINCRBY leaderboard:{roomId} → 实时积分
```
