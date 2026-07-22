# JMeter 线程组设计方案

## 方案一：单线程组 + 权重（推荐，简单）

一个 Thread Group 里用 **Throughput Controller** 按比例分配：

```
Thread Group (总线程数 = N)
├── Throughput Controller (40%) → 秒杀抢购
│   └── HTTP Request: POST /seckill/order
├── Throughput Controller (20%) → 排行榜加分
│   └── HTTP Request: POST /leaderboard/score
├── Throughput Controller (15%) → 排行榜查询
│   └── HTTP Request: GET /leaderboard/top
├── Throughput Controller (10%) → 直播间列表
│   └── HTTP Request: GET /live/rooms
├── Throughput Controller (10%) → 登录
│   └── HTTP Request: POST /auth/login
└── Throughput Controller (5%)  → 注册
    └── HTTP Request: POST /auth/register
```

**优点**：线程数统一控制，简单直观
**缺点**：所有接口共享同一个 ramp-up，不够灵活

---

## 方案二：多线程组 + CSV 参数化（推荐，灵活）

每个接口一个 Thread Group，独立控制线程数和节奏：

```
Test Plan
├── Thread Group: 秒杀抢购 (40% 线程)
│   ├── CSV Data Set Config: seckill-users.csv (userId, token)
│   ├── HTTP Request: POST /seckill/order
│   └── Constant Timer: 100ms (思考时间)
│
├── Thread Group: 排行榜加分 (20% 线程)
│   ├── CSV Data Set Config: lb-users.csv (userId, token)
│   ├── HTTP Request: POST /leaderboard/score
│   └── Constant Timer: 200ms
│
├── Thread Group: 排行榜查询 (15% 线程)
│   ├── HTTP Request: GET /leaderboard/top
│   └── Constant Timer: 500ms (读操作可以慢点)
│
├── Thread Group: 直播间列表 (10% 线程)
│   ├── HTTP Request: GET /live/rooms
│   └── Constant Timer: 1000ms
│
├── Thread Group: 登录 (10% 线程)
│   ├── CSV Data Set Config: reg-users.csv (username, password, deviceId)
│   ├── HTTP Request: POST /auth/login
│   └── JSON Extractor: 提取 token
│
└── Thread Group: 注册 (5% 线程)
    ├── CSV Data Set Config: new-users.csv (username, password)
    └── HTTP Request: POST /auth/register
```

**优点**：每个接口独立 ramp-up、独立节奏，更接近真实场景
**缺点**：配置稍复杂

---

## 方案三：登录前置 + 业务分离（最真实）

先登录获取 token，再用 token 压业务接口：

```
Test Plan
├── setUp Thread Group: 登录预热
│   ├── CSV Data Set Config: reg-users.csv
│   ├── HTTP Request: POST /auth/login
│   ├── JSON Extractor: 提取 token
│   └── CSV Data Set Config: 写入 token 到文件 (seckill-users.csv, lb-users.csv)
│
├── Thread Group: 秒杀抢购 (40%)
│   ├── CSV Data Set Config: seckill-users.csv (userId, token)
│   ├── HTTP Header Manager: Authorization: Bearer ${token}
│   ├── HTTP Request: POST /seckill/order
│   └── Uniform Random Timer: 50-200ms
│
├── Thread Group: 排行榜加分 (20%)
│   ├── CSV Data Set Config: lb-users.csv
│   ├── HTTP Header Manager: Authorization: Bearer ${token}
│   ├── HTTP Request: POST /leaderboard/score
│   └── Uniform Random Timer: 100-300ms
│
├── Thread Group: 排行榜查询 (15%)
│   ├── HTTP Request: GET /leaderboard/top?activityId=1
│   └── Uniform Random Timer: 200-500ms
│
├── Thread Group: 直播间列表 (10%)
│   ├── HTTP Request: GET /live/rooms
│   └── Uniform Random Timer: 500-1000ms
│
└── Thread Group: 登录 (10%)
    ├── CSV Data Set Config: reg-users.csv
    ├── HTTP Request: POST /auth/login
    └── Constant Timer: 1000ms (登录频率低)
```

**优点**：token 复用，避免每次请求都登录（bcrypt 太重）
**缺点**：需要 setUp 线程组预处理

---

## 线程数配置表

### Phase 2 混合流量（按方案二）

| 轮次 | 总线程 | 秒杀 | 排行榜写 | 排行榜读 | 直播间 | 登录 | 注册 |
|---|---|---|---|---|---|---|---|
| 2A | 1000 | 400 | 200 | 150 | 100 | 100 | 50 |
| 2B | 3000 | 1200 | 600 | 450 | 300 | 300 | 150 |
| 2C | 5000 | 2000 | 1000 | 750 | 500 | 500 | 250 |
| 2D | 8000 | 3200 | 1600 | 1200 | 800 | 800 | 400 |

### 每个线程组的参数

```
Thread Group 配置：
├── Number of Threads: {上表数值}
├── Ramp-up period: 10s (所有线程在 10s 内启动完)
├── Loop count: ∞ (勾选 "Infinite")
└── Scheduler: 
    ├── Duration: 120s (总测试时长)
    └── Startup delay: 0s
```

---

## CSV 数据准备

### seckill-users.csv（秒杀用户）

```csv
userId,token
1001,eyJhbGciOiJIUzI1NiJ9...
1002,eyJhbGciOiJIUzI1NiJ9...
...
```

**生成方式**：
1. 用登录接口批量获取 token
2. 或者写个脚本调用 `/auth/login` 1000 次，输出到 CSV

### lb-users.csv（排行榜用户）

```csv
userId,token,eventType
1001,eyJhbGciOiJIUzI1NiJ9...,WATCH
1002,eyJhbGciOiJIUzI1NiJ9...,LIKE
1003,eyJhbGciOiJIUzI1NiJ9...,COMMENT
...
```

**eventType 随机**：WATCH / LIKE / COMMENT / SHARE / GIFT（按权重 0.3/0.5/1.0/2.0/5.0）

### reg-users.csv（注册用户）

已有 `docs/jmeter测试包/csv/reg-users.csv`（1000 个用户）

---

## 关键配置细节

### 1. HTTP Header Manager（每个需要 token 的请求）

```
Authorization: Bearer ${token}
X-Device-Id: ${deviceId}
Content-Type: application/json
```

### 2. JSON Extractor（登录接口提取 token）

```
Variable names: token
JSON Path: $.data.accessToken
Match No: 1
```

### 3. Uniform Random Timer（模拟真实用户思考）

```
Minimum Delay: 50ms
Maximum Delay: 200ms
```

**不同接口的思考时间**：
- 秒杀：50-200ms（用户抢秒杀很快）
- 排行榜写：100-300ms（送礼/点赞）
- 排行榜读：200-500ms（看榜单）
- 直播间列表：500-1000ms（浏览）
- 登录：1000ms（低频）

### 4. 断言配置

```
Response Assertion:
├── Pattern to Test: 200
├── Pattern Matching Rules: Contains
└── Test Field: Response Code
```

---

## 执行流程

```
1. 造数据：
   - 创建秒杀活动（status=1）
   - 初始化排行榜（写入 1000 个用户分数）
   - 创建 10 个直播间（status=1）
   - 生成 seckill-users.csv 和 lb-users.csv

2. 配置线程组：
   - 按方案二创建 6 个 Thread Group
   - 配置 CSV Data Set Config
   - 配置 HTTP Header Manager
   - 配置 Timer

3. 启动压测：
   - 先启动所有服务的 jstat 监控
   - 启动 JMeter 测试
   - 观察各服务 CPU、GC、错误率

4. 记录数据：
   - JMeter 聚合报告（QPS、p99、错误率）
   - jstat 输出（YGC、FGC、Old%）
   - 任务管理器（CPU 使用率）
```

---

## 常见问题

**Q: 为什么不用方案一（单线程组）？**

A: 单线程组所有接口共享 ramp-up，无法独立控制节奏。比如登录接口 bcrypt 太重，10% 的线程如果和其他接口同时 ramp-up，会抢占 CPU 导致其他接口延迟飙升。

**Q: 登录接口要不要每次都跑？**

A: 不要。登录（bcrypt）单次 ~10ms，1000 并发会占满 CPU。用 setUp 线程组预登录，把 token 写入 CSV，业务接口直接读 token。

**Q: 思考时间要不要加？**

A: 要。没有思考时间 = 用户 0 延迟连续点击，不真实。Uniform Random Timer 让流量更像真实用户。

**Q: 线程数怎么算出来的？**

A: 按流量模型比例 × 总线程数。比如总线程 1000，秒杀占 40% = 400 线程。
