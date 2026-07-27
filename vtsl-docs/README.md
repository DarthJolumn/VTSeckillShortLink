# VTSeckillShortLink 文档

> 基于 JDK 25 (ZGC + Virtual Threads) + Spring Boot 4.1 的高并发直播秒杀与短链系统。

## 目录结构

```
vtsl-docs/
├── README.md                        # 本文档 — 总索引
│
├── architecture/                    # 架构设计
│   ├── 01-overview.md              # 系统概览 — 架构图、技术栈、路由
│   ├── 02-modules.md               # 模块详解 — 7 个模块职责与核心类
│   ├── 03-data-flow.md             # 核心数据流 — 秒杀/短链/弹幕/排行榜
│   └── 04-key-decisions.md         # 关键技术决策 — GC/线程/缓存/事务
│
├── performance/                    # 压测结果与分析
│   ├── 01-vt-vs-platform.md       # 虚拟线程 vs 平台线程 — 600 并发实测
│   ├── 02-g1-vs-zgc.md            # G1 vs 分代 ZGC — GC 耗时与应用性能
│   └── 03-async-shave.md          # 异步削峰 — Kafka + Redis 优化前后对比
│
└── raw-data/                       # 原始压测 CSV 数据
    ├── vt-vs-platform/             # 虚拟线程/平台线程对比
    ├── g1/                         # G1 压测各阶段数据
    └── zgc/                        # ZGC 压测各阶段数据
```

## 环境

| 项目 | 规格 |
|------|------|
| 应用服务器 | 8vCPU / 16GB RAM |
| 中间件 VM | 192.168.147.132 (Redis / MySQL / Nacos / Kafka / Sentinel) |
| JDK | OpenJDK 25.0.3 (ZGC + Virtual Threads) |
| 框架 | Spring Boot 4.1.0 / Spring Cloud 2025.1.2 |
