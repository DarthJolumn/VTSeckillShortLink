# LiveMall JMeter 压测计划

> 目标：验证功能正确性 + 获取简历量化数据（P99 延迟、QPS、错误率）
> JMeter 需安装插件：`jmeter-websocket-samplers-1.2.8.jar`（Peter Doornbosch 维护版）

---

## 一、测试环境

| 组件 | 地址 | 说明 |
|---|---|---|
| Gateway | localhost:8080 | 统一入口 |
| WebSocket | ws://localhost:8083/ws/live/{roomId} | 直连，不经过 Gateway |
| Nacos | 192.168.147.132:8848 | 已有 |
| Redis | 192.168.147.132:6379 | 已有 |
| MySQL | 192.168.147.132:3306 | 已有 |

启动顺序：
```bash
# 1. 确认中间件已启动
curl http://192.168.147.132:8848/nacos/v1/console/health/readiness

# 2. 启动服务（按依赖顺序）
mvn spring-boot:run -pl livemall-gateway &
mvn spring-boot:run -pl livemall-user &
mvn spring-boot:run -pl livemall-websocket &
mvn spring-boot:run -pl livemall-seckill &
mvn spring-boot:run -pl livemall-leaderboard &
```

---

## 二、测试数据

### 2.1 注册用户（CSV: users.csv）

```csv
username,password,phone
testuser1,Test1234,13800000001
testuser2,Test1234,13800000002
...
testuser50,Test1234,13800000050
```

### 2.2 弹幕语料（CSV: barrage.csv）

```csv
content
666
主播好厉害
冲冲冲
已下单
这个价格绝了
再来一个
蹲点抢购
库存还有吗
气氛组就位
火钳刘明
感谢主播
比双十一还便宜
冲了冲了
求上架
太顶了
抢到啦
没抢到QAQ
下一场几点
买它买它
送火箭啦
```

### 2.3 礼物列表（CSV: gifts.csv）

```csv
giftId,giftName,price
1,玫瑰花,9
2,跑车,120
3,火箭,666
4,皇冠,188
```

---

## 三、JMeter 测试场景

### 场景 A：基础功能冒烟（单线程验证）

**目标：** 确认所有接口正常工作

```
Thread Group: 1 user, 1 loop

1. POST /auth/register   {"username":"perftest","password":"Test1234"}
2. POST /auth/login      {"username":"perftest","password":"Test1234"}
   → 提取 accessToken、refreshToken
3. POST /auth/refresh    {"refreshToken":"${refreshToken}"}
4. GET  /user/profile    Header: X-User-Id={从JWT解析}
5. GET  /live/rooms      公开接口，无需认证
6. POST /live/room/start Header: X-User-Id + X-User-Role
   → 提取 roomId
7. POST /auth/logout     {"refreshToken":"${refreshToken}"}
```

### 场景 B：直播并发压测（WebSocket 长连接）

**目标：** 验证 500 连接 + 弹幕广播延迟

```
架构: setUp Thread Group → 普通 Thread Group → tearDown Thread Group

┌─ setUp Thread Group (1 user, 1 loop) ──────────────────┐
│  1. POST /auth/login → 提取 accessToken                  │
│  2. GET /live/rooms → 提取 roomId                        │
│  3. WebSocket Open Connection                            │
│     ws://localhost:8083/ws/live/${roomId}?token=${token} │
│     ☑ Streaming Connection                               │
│     → 提取 ws connection id                              │
└─────────────────────────────────────────────────────────┘

┌─ 普通 Thread Group (500 users, Ramp-Up 60s, Loop 20) ───┐
│  ┌─ Loop Controller (20) ──────────────────────────────┐ │
│  │  1. WebSocket Request (PING)                         │ │
│  │     {"type":"PING"}                                   │ │
│  │     Use existing connection                           │ │
│  │                                                       │ │
│  │  2. Uniform Random Timer (2000~5000ms)               │ │
│  │                                                       │ │
│  │  3. WebSocket Request (BARRAGE)                       │ │
│  │     {"type":"BARRAGE","data":{"content":"${barrage}"}}│ │
│  │     Use existing connection                           │ │
│  │                                                       │ │
│  │  4. Uniform Random Timer (5000~15000ms)              │ │
│  │                                                       │ │
│  │  5. WebSocket Request (GIFT)                          │ │
│  │     {"type":"GIFT","data":{"giftId":${giftId},"quantity":1}}│
│  │     Use existing connection                           │ │
│  │                                                       │ │
│  │  6. Uniform Random Timer (10000~30000ms)             │ │
│  └──────────────────────────────────────────────────────┘ │
│  CSV: barrage.csv + gifts.csv (Recycle on EOF)           │
└─────────────────────────────────────────────────────────┘

┌─ tearDown Thread Group ─────────────────────────────────┐
│  WebSocket Close Connection                              │
└─────────────────────────────────────────────────────────┘

关键配置:
  - Streaming Connection ☑（同一线程复用长连接）
  - Response Timeout: 3000ms
  - Ping Interval: 15000ms（服务端 30s PONG）
```

### 场景 C：秒杀压测（集合点 + HTTP）

**目标：** 1000 人同时抢购 100 件商品

```
前提准备（手动执行一次）：
  POST /live/room/start  → 开播，获取 roomId
  POST /seckill/activity  → 创建活动(库存100, 进行中)
    {"title":"压测秒杀","productId":1,"seckillPrice":99,
     "originalPrice":199,"totalStock":100,
     "startTime":"2026-07-19T10:00:00","endTime":"2026-07-19T22:00:00"}

Thread Group: 1000 users, Ramp-Up 30s, Loop 1

  1. POST /auth/login → 提取 accessToken（CSV: 1000 个用户）
  2. Synchronizing Timer
     - Number of Simulated Users to Group by: 1000
     - Timeout: 30000ms
  3. POST /seckill/order
     {"activityId": 1}
     Header: Authorization=Bearer ${token}
  4. 查看结果树（仅调试时启用）

关键指标:
  - 平均响应时间（期望 < 200ms）
  - 错误率（期望 < 5%，售罄不算错误）
  - 吞吐量 QPS
  - 库存扣减正确性（最终售出数 = 100，不超卖）
```

### 场景 D：混合负载全链路（直播 + HTTP + 秒杀）

**目标：** 模拟真实场景，获取全站 QPS 数据

```
Thread Group A (200 users, Loop Forever, Duration 300s):
  弹幕 + 礼物（同场景 B 的 Loop Controller）

Thread Group B (100 users, Loop Forever, Duration 300s):
  HTTP 请求：GET /live/rooms, GET /leaderboard/top?activityId=1&n=100
  
Thread Group C (50 users, Ramp-Up 10s, Loop 1):
  Synchronizing Timer(50) → POST /seckill/order（秒杀爆发）
```

---

## 四、JMeter 关键配置

### 4.1 线程组通用设置

| 参数 | 推荐值 | 说明 |
|---|---|---|
| Action to be taken after a Sampler error | Continue | 一个请求失败不影响其他 |
| 循环次数 | 按场景设定 | 压力测试用 Forever + Duration |

### 4.2 HTTP Request Defaults（一次配置）

```
Protocol: http
Server Name: localhost
Port: 8080
Content-Type: application/json
```

### 4.3 JVM 参数（施压机）

```bash
# jmeter.bat / jmeter.sh
HEAP="-Xms2g -Xmx4g -XX:MaxDirectMemorySize=2g"
```

### 4.4 监听器（按需启用）

| 监听器 | 用途 | 注意 |
|---|---|---|
| 聚合报告 | 看平均值/中位数/P99/吞吐量 | ✅ 压测时保留 |
| 活动线程数 | 看并发度是否达到预期 | ✅ 保留 |
| 查看结果树 | 调试单个请求 | ❌ 压测时禁用（吃内存） |
| 后端监听器 | 对接 Prometheus/Grafana | 可选 |

---

## 五、压测执行步骤

```bash
# 1. 冒烟：1 用户 1 循环
jmeter -n -t smoke-test.jmx -l smoke-result.jtl

# 2. 直播：500 用户 60s 加载 20 循环
jmeter -n -t live-test.jmx -l live-result.jtl

# 3. 秒杀：1000 用户 集合点
jmeter -n -t seckill-test.jmx -l seckill-result.jtl

# 4. 混合：全链路 5 分钟
jmeter -n -t full-load.jmx -l full-result.jtl -Dduration=300
```

---

## 六、预期量化数据（填简历用）

| 指标 | 目标值 | 实际值 |
|---|---|---|
| Gateway 入口 QPS | — | （压测填入） |
| 秒杀抢购 P99 延迟 | < 200ms | |
| 秒杀错卖/超卖 | 0 | |
| 弹幕广播 P99 延迟 | < 50ms | |
| 500 连接内存占用 | < 500MB | |
| 错误率 | < 0.5% | |
| 全站混合负载 QPS | — | |
