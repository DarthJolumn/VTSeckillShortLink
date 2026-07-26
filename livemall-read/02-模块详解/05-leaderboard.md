# 排行榜服务 (leaderboard)

> 端口 **8084** · Spring MVC + VT · Dubbo 端口 **20884** · Dubbo **Provider**

## 职责

互动加分 / 实时 TopN / 个人排名 / 定时快照 / 历史排行

## 实现

```mermaid
WebSocket (送礼/点赞)
    │
    │ Dubbo addScore()
    ▼
LeaderboardServiceImpl
    │
    ├─ Redis ZINCRBY leaderboard:{roomId} ← O(log N)
    ├─ ZREVRANGE → TopN ← O(log N + K)
    └─ ZSCORE + ZREVRANK → 个人排名 ← O(log N)
```

## 类结构

```
service/
├── LeaderboardServiceImpl.java  # Dubbo 服务实现（增删查）
├── SnapshotTask.java            # @Scheduled(5min) 快照到 MySQL
└── LeaderboardController.java   # HTTP 查询接口
```

## Redis 数据结构

```
leaderboard:{roomId}   → ZSet   ← 用户互动积分（ZINCRBY）
```

## API

| Method | Path | 说明 | 鉴权 |
|--------|------|------|------|
| GET | `/leaderboard/top` | 实时 TopN | 公开 |
| GET | `/leaderboard/rank/{userId}` | 个人排名 | 公开 |
| GET | `/leaderboard/history` | 历史排行 | JWT |

## 权重体系

| 事件类型 | 权值 |
|---------|------|
| WATCH | 0.3 |
| LIKE | 0.5 |
| COMMENT | 1.0 |
| SHARE | 2.0 |
| GIFT | 5.0 |

## 定时快照

```yaml
leaderboard.snapshot.cron: "0 */5 * * * ?"    # 每 5 分钟
leaderboard.snapshot.top-n: 100                # 存 Top 100
leaderboard.snapshot.batch-size: 500           # 批量插入
```
