# LiveMall 教学包 — 秋招面试学习路线图

> ⚠️ **注意**: 本项目已升级到 **JDK 25 + Spring Boot 4.1**，文中提及"Java 21"处请替换为 JDK 25。
> 新版文档索引见 `docs/README.md`。

> **目标：** 用这个项目在秋招面试中打动面试官。不只背答案，更理解每个决策背后的 trade-off。

## 这个项目是什么

高并发直播电商秒杀系统，**10w QPS 目标**。模拟直播间场景：看直播、发弹幕、送礼、抢秒杀、看排行榜。

**一句话电梯演讲：** 5 个微服务 + Gateway 统一入口，Java 21 虚拟线程 + Dubbo RPC + Redis Lua 原子扣库存 + Kafka 异步削峰 + WebSocket 实时推送。

## 面试官会怎么看这个项目

| 面试官关注点 | 项目的回答 |
|-------------|-----------|
| 技术深度 | Dubbo SPI、ScopedValue vs ThreadLocal、Kafka 零拷贝、Redis 跳表 |
| 架构能力 | 5 服务拆分依据、双 Token 设计、多级缓存、降级链路 |
| 高并发 | Sentinel 滑动窗口、库存分片、Kafka 削峰、Caffeine 前置过滤 |
| 代码质量 | VT 纪律、Lua 原子性、乐观锁 CAS、幂等设计 |
| 学习能力 | Java 21 新特性、自研 Snowflake、DFA 热更新 |

## 学习路线图（按优先级）

### 第一层：能说清楚项目（必学，2-3 天）

```
00-快速参考卡.md        ← 面试前 30 分钟必看
01-业务全景.md          ← 理解"做什么"，能用 30 秒介绍项目
02-架构深度.md          ← 理解"怎么做"，能画出架构图并解释
03-技术选型攻防.md       ← 每个选型都能说出 3 个理由 + 2 个对比
```

### 第二层：能深入技术细节（核心，3-5 天）

```
04-秒杀链路详解.md       ← 最核心！从网关到数据库的全链路
05-WebSocket实时通信.md   ← 连接管理、心跳、跨节点路由
06-双Token认证体系.md     ← Access/Refresh、并发刷新、ScopedValue
07-排行榜与缓存策略.md    ← ZSet跳表、Caffeine多级缓存、缓存三大问题
```

### 第三层：能应对追问（进阶，1-2 天）

```
08-虚拟线程深度解析.md    ← VT原理、pinning、与WebFlux对比
09-面试模拟问答.md        ← 30+ 真实面试问题与回答策略
10-故障场景与降级.md      ← Redis挂了/Kafka堆积/时钟回拨等场景
```

## 学习建议

1. **不要死记硬背**：每个知识点问自己"为什么这么设计？不这么设计会怎样？"
2. **画图**：架构图、秒杀流程图、Token 刷新时序图，面试时边画边讲
3. **看代码**：每个设计文档都有对应代码，`codegraph_explore` 是最好入口
4. **模拟面试**：对着 `09-面试模拟问答.md` 自问自答，录下来听
5. **准备"递话头"**：在回答中有意提到面试官可能会追问的点（如 Dubbo SPI、Kafka 零拷贝）

## 文档地图

```
md/
├── teach/                          ← ★ 你在这里（教学包）
│   ├── README.md                   ← 学习路线图（本文件）
│   ├── 00-快速参考卡.md
│   ├── 01-业务全景.md
│   ├── 02-架构深度.md
│   ├── 03-技术选型攻防.md
│   ├── 04-秒杀链路详解.md
│   ├── 05-WebSocket实时通信.md
│   ├── 06-双Token认证体系.md
│   ├── 07-排行榜与缓存策略.md
│   ├── 08-虚拟线程深度解析.md
│   ├── 09-面试模拟问答.md
│   └── 10-故障场景与降级.md
│
├── docs/                           ← 原始设计文档（深入学习用）
│   ├── 1-架构设计/                 ← 项目概览、技术选型、功能清单、约束
│   ├── 2-模块详细设计/             ← 每个模块的类/方法/数据流设计
│   ├── 3-功能实现细节/             ← 单功能的 I/O、流程、边界
│   ├── 4-面试准备/                 ← 原始的面试 Q&A
│   └── 文档地图.md
│
└── 代码                            ← 配合文档阅读
    ├── LiveMallBk/livemall-common/  ← 工具类、DTO、异常
    ├── LiveMallBk/livemall-gateway/ ← 网关：路由、鉴权、限流
    ├── LiveMallBk/livemall-user/    ← 用户：注册登录、Token
    ├── LiveMallBk/livemall-seckill/ ← 秒杀：Lua、Kafka、订单
    ├── LiveMallBk/livemall-websocket/← WebSocket：连接、弹幕、推送
    ├── LiveMallBk/livemall-leaderboard/← 排行榜：ZSet、快照
    └── livemall-ui-vue/            ← 前端：Vue 3 + Pinia + WS 客户端
```

## 如何使用 CodeGraph 学习代码

```bash
# 理解一个类的实现
codegraph explore "LiveWebSocket onOpen onMessage"

# 追踪调用链
codegraph explore "SeckillController 到 Redis Lua 到 Kafka"

# 看某个功能的所有相关代码
codegraph explore "JWT 鉴权 gateway filter"
```

## 关键面试数据速记

| 指标 | 数值 |
|------|------|
| 目标 QPS | 10w |
| 服务数 | 5 个微服务 + 1 个网关 |
| Access Token TTL | 15 分钟 |
| Refresh Token TTL | 7 天 |
| 库存分片数 | 4 |
| Redis 单分片 QPS | ~8w |
| 心跳间隔 | 30s PING, 60s 超时 |
| 排行榜快照间隔 | 5 分钟 |
| 订单超时取消 | 15 分钟 |
| Snowflake epoch | 2025-01-01 |
| 虚拟线程 vs 平台线程创建成本 | 低 2 个数量级 |
| 双 Token vs Session IO 差距 | ~900 倍 |
