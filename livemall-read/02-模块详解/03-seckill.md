# 秒杀服务 (seckill)

> 两个部署变体：
> - **集群** `livemall-seckill` :8090（多实例，Redis 集群）
> - **单实例** `livemall-seckill-single` :8082（独立部署，更大连接池）
>
> Spring MVC + VT · Dubbo 端口 **20882** · Kafka 生产 + 消费 · Sentinel 限流

## 职责

创建活动 / 上架下架 / 抢购(Redis Lua) / 库存分片 / 异步创建订单 / 超时取消 / 用户取消 / 退款 / 订单查询

## 类结构

```
controller/
└── SeckillController.java      # REST 接口
service/
├── SeckillService.java         # 业务核心（下单、取消、退款）
├── StockService.java           # Redis Lua 库存扣减/回补
├── ActivityCacheService.java   # Caffeine L1 活动缓存
└── ActivityBloomFilter.java    # ⚠️ 已废弃（多实例 BF 不一致，Caffeine 替代）
consumer/
├── SeckillOrderConsumer.java   # Kafka 消费端（Semaphore 背压）
scheduler/
├── TimeoutCancelScheduler.java # @Scheduled 超时取消订单
└── ReconciliationScheduler.java# 5min 对账补偿
config/
├── KafkaConfig.java            # Kafka 配置
├── GrpcClientConfig.java       # gRPC 客户端（seckill-single 推送用）
└── RedisScriptWarmup.java      # Lua 脚本预热
entity/
├── SeckillActivity.java        # 活动实体
└── SeckillOrder.java           # 订单实体
repository/
├── SeckillActivityRepository.java
└── SeckillOrderRepository.java
warmup/
└── SeckillDataWarmup.java      # 启动预热
```

## 数据模型

**seckill_activity** — 秒杀活动

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 |
| title | VARCHAR(100) | 活动标题 |
| seckill_price | DECIMAL(10,2) | 秒杀价 |
| orig_price | DECIMAL(10,2) | 原价 |
| total_stock | INT | 总库存 |
| start_time | DATETIME INDEX | 开始时间 |
| end_time | DATETIME | 结束时间 |
| status | TINYINT INDEX | 0待开始/1进行中/2已结束/3已取消 |
| room_id | BIGINT | 关联直播间 |
| product_id | BIGINT | 商品 ID |
| created_at | DATETIME | |
| version | INT | 乐观锁 |

**seckill_order** — 秒杀订单

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 |
| order_no | VARCHAR(32) UNIQUE | 订单号（Snowflake） |
| activity_id | BIGINT INDEX | 活动 ID |
| user_id | BIGINT INDEX | 用户 |
| product_id | BIGINT | |
| seckill_price | DECIMAL(10,2) | 成交价 |
| status | TINYINT | 0待支付/1已支付/2已取消/3已退款 |
| created_at | DATETIME | |
| paid_at | DATETIME | |
| cancelled_at | DATETIME | |
| version | INT | 乐观锁 |

## API

| Method | Path | 说明 | 鉴权 |
|--------|------|------|------|
| POST | `/seckill/activity` | 创建活动 | JWT(ADMIN) |
| PUT | `/seckill/activity/{id}/status` | 上下架 | JWT(ADMIN) |
| GET | `/seckill/activity/{id}` | 活动详情 | JWT |
| GET | `/seckill/activity/list` | 活动列表(roomId) | JWT/GET公开 |
| POST | `/seckill/order` | 抢购 | JWT |
| GET | `/seckill/order/list` | 订单列表 | JWT |
| GET | `/seckill/order/{orderNo}` | 订单详情 | JWT |
| PUT | `/seckill/order/{orderNo}/cancel` | 取消订单 | JWT |
| PUT | `/seckill/order/{orderNo}/refund` | 退款 | JWT(ADMIN) |

## 抢购链路

```
placeOrder()
  ├─ Caffeine.getActivity()        ← L1 缓存命中，不查 DB
  ├─ 校验活动状态/时间
  ├─ stockService.deduct()
  │   └─ Lua: DECR stock → -1/2 → INCR back → SETEX ordered
  │       returns: 200=成功 / -1=已参与 / -2=售罄
  └─ Kafka send("seckill-order")  ← send().get(3s) 超时则 refund + 503

SeckillOrderConsumer.onMessage()
  ├─ SEM.acquire()                 ← 信号量 30 = HikariCP 池大小
  ├─ Thread.startVirtualThread()
  ├─ cacheService.getActivity()    ← 缓存
  ├─ seckillService.createOrder()  ← @Transactional INSERT
  │   └─ DuplicateKeyException → 幂等 ACK
  ├─ Redis Pub/Sub / gRPC 推送结果
  └─ ack.acknowledge()            ← 手动 ACK
```

## 库存回补

```
cancelOrder()
  ├─ DB UPDATE order SET status=2  ← @Version CAS 防并发
  └─ stockService.refund()
      └─ Lua: EXISTS ordered + INCR stock + DEL ordered  ← 幂等
```

## 分布式配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `seckill.consumer-max-concurrency` | 30 | Semaphore 背压上限（= HikariCP 大小） |
| `seckill.timeout-scan-ms` | 15000 | 超时扫描间隔 |
| `seckill.timeout-scan-batch` | 500 | 每批处理订单数 |
| `seckill.order-timeout-minutes` | 15 | 订单超时分钟数 |
| `seckill.activity-cache.ttl-seconds` | 5 | Caffeine 刷新间隔 |
| `seckill.dedup-enabled` | true | 是否开启 Lua 查重 |
