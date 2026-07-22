# LiveMall JMeter 测试包

> 单接口极限测试 + G1 vs 分代 ZGC 对比压测

---

## 目录结构

```
jmeter测试包/
├── csv/                          # 测试数据
│   ├── users-1000.csv            # 1000 测试用户
│   ├── users-5000.csv            # 5000 测试用户
│   ├── seckill-users.csv         # 2000 秒杀用户
│   ├── barrage.csv               # 30 条弹幕语料
│   └── gifts.csv                 # 4 种礼物
├── jmx/                          # JMeter 测试计划
│   ├── 00-smoke-test.jmx         # 冒烟测试（1用户验证全接口）
│   ├── 01-auth-register.jmx      # 注册极限（200并发）
│   ├── 02-auth-login.jmx         # 登录极限（500并发）
│   ├── 03-auth-refresh.jmx       # Token刷新极限（300并发）
│   ├── 04-user-profile.jmx       # 用户信息极限（500并发）
│   ├── 05-user-devices.jmx       # 设备管理极限（300并发）
│   ├── 06-live-rooms.jmx         # 房间列表极限（1000并发）
│   ├── 07-live-room-start.jmx    # 开播极限（100并发）
│   ├── 08-seckill-order.jmx      # 秒杀极限（2000人集合点）⭐核心
│   ├── 09-seckill-activity.jmx   # 活动查询极限（500并发）
│   ├── 10-leaderboard-top.jmx    # 排行榜查询极限（1000并发）
│   ├── 11-ws-barrage.jmx         # 弹幕广播极限（1000 WS连接）
│   ├── 12-ws-gift.jmx            # 礼物广播极限（500 WS连接）
│   ├── 13-mixed-load.jmx         # 混合负载（30分钟）
│   └── 14-full-stress.jmx        # 全链路压力（递增100→2000）
├── scripts/                      # 执行脚本
│   ├── run-all.sh                # 一键执行全部测试
│   ├── run-single.sh             # 执行单个测试
│   ├── gc-analyze.sh             # GC 日志分析
│   └── jtl-parse.sh              # JTL 结果解析
└── reports/                      # 测试报告输出目录
```

---

## 快速开始

### 前置条件

1. JMeter 5.6+ 已安装并加入 PATH
2. WebSocket 插件已安装：`jmeter-websocket-samplers-1.2.8.jar`
3. 服务已启动（G1 或 ZGC 模式）
4. 测试数据已导入（用户、活动等）

### 执行全部测试（推荐）

```bash
# G1 GC 模式
cd docs/jmeter测试包/scripts
chmod +x run-all.sh gc-analyze.sh jtl-parse.sh
./run-all.sh g1

# ZGC 模式（需重启服务）
./run-all.sh zgc
```

### 执行单个测试

```bash
# 执行秒杀极限测试
./run-single.sh 08-seckill-order g1

# 执行排行榜查询测试
./run-single.sh 10-leaderboard-top zgc
```

### 查看结果

```bash
# 解析所有 JTL 文件
./jtl-parse.sh all

# 分析 GC 日志
./gc-analyze.sh g1
./gc-analyze.sh zgc
```

---

## 各接口极限测试参数

| # | 接口 | 并发数 | Ramp-Up | 持续时间 | 核心观测指标 |
|---|------|--------|---------|---------|-------------|
| 01 | POST /auth/register | 200 | 30s | 120s | QPS、注册成功率 |
| 02 | POST /auth/login | 500 | 30s | 120s | P99、Token签发延迟 |
| 03 | POST /auth/refresh | 300 | 30s | 120s | 刷新成功率 |
| 04 | GET /user/profile | 500 | 30s | 120s | P99、DB查询延迟 |
| 05 | GET /user/devices | 300 | 30s | 120s | Redis SMEMBERS延迟 |
| 06 | GET /live/rooms | 1000 | 30s | 120s | **最高流量**、JPA查询P99 |
| 07 | POST /live/room/start | 100 | 30s | 120s | 开播成功率 |
| 08 | POST /seckill/order | 2000 | 5s | 单次 | **核心**、Redis Lua P99、超卖数 |
| 09 | GET /seckill/activity/list | 500 | 30s | 120s | Caffeine缓存命中率 |
| 10 | GET /leaderboard/top | 1000 | 30s | 120s | ZSet查询P99、Caffeine缓存 |
| 11 | WS BARRAGE | 1000连接 | 60s | 300s | 广播延迟、虚拟线程数 |
| 12 | WS GIFT | 500连接 | 30s | 300s | Dubbo RPC延迟 |
| 13 | 混合负载 | 550 | 60s | 1800s | 全链路P99、GC行为 |
| 14 | 全链路压力 | 递增 | - | 600s | 100→500→1000→2000 |

---

## G1 vs ZGC 对比流程

```
Phase 1: G1 基线
  启动参数:
    java -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -Xms4g -Xmx4g \
         -Xlog:gc*:file=logs/gc-g1.log:time,uptime,level,tags \
         -jar livemall-*.jar
  
  执行: ./run-all.sh g1

Phase 2: ZGC 基线
  停服 → 清理 Redis → 重启:
    java -XX:+UseZGC -XX:+ZGenerational -Xms4g -Xmx4g \
         -Xlog:gc*:file=logs/gc-zgc.log:time,uptime,level,tags \
         -jar livemall-*.jar
  
  执行: ./run-all.sh zgc

Phase 3: 对比分析
  ./jtl-parse.sh all    → 各接口 P99/QPS 对比
  ./gc-analyze.sh g1    → G1 停顿统计
  ./gc-analyze.sh zgc   → ZGC 停顿统计
```

---

## GC 选型决策矩阵

| 指标 | 权重 | G1 实测值 | ZGC 实测值 | 优胜方 |
|------|------|----------|-----------|--------|
| 秒杀 P99 延迟 | 30% | — ms | — ms | |
| GC 总停顿时间/分钟 | 25% | — ms | — ms | |
| Max 单次停顿 | 20% | — ms | — ms | |
| 吞吐量 QPS | 15% | — | — | |
| GC 线程 CPU 占用 | 5% | —% | —% | |
| 调参复杂度 | 5% | 高 | 低 | |
| **综合评分** | 100% | /100 | /100 | |

---

## 注意事项

1. **WebSocket 测试**需要安装 `jmeter-websocket-samplers` 插件
2. **秒杀测试**需要先创建活动并设置库存
3. **长时间测试**（混合负载 30 分钟）确保机器资源充足
4. 每个场景间有 **30s 冷却期**让 GC 稳定
5. JTL 文件较大，测试完成后及时清理
