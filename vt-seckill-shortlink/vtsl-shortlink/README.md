# livemall-shortlink — 短链系统

## 业务定位

商品上线时自动生成短链，用户点击短链跳转到商品详情页。

**核心指标**：日读 100 万 / 日写 10 万 / P99 < 50ms

---

## 架构设计

### 四层架构

```
接入层：Nginx 限流 + Sentinel 热点熔断
   ↓
缓存层：Caffeine L1 (TTL=3s) → Redis Master (TTL=30天)
   ↓
存储层：MySQL 映射表 + Kafka 异步统计
   ↓
高可用层：Redis Sentinel (1主2从，Slave 仅热备)
```

### 核心设计

| 设计点 | 实现 | 理由 |
|--------|------|------|
| **ID 生成** | Redis INCR + Base62 | 简单、无时钟风险、性能余量充足 |
| **跳转读** | 只走 Redis Master | 保证强一致，避免主从延迟导致 404 |
| **防穿透** | 空值缓存 (TTL=30s) | 拦截非法短码，保护 DB |
| **去重** | MD5(original_url) 反向索引 | 相同 URL 不重复生成短链 |
| **统计** | Kafka 异步 → ClickHouse | 百万级写转化为数百级顺序写 |

---

## API 接口

### 1. 创建短链

```http
POST /s/create
Content-Type: application/json

{
  "productId": 1001,
  "originalUrl": "https://livemall.com/product/1001"
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "shortCode": "aB3xYz",
    "shortUrl": "https://s.livemall.com/aB3xYz"
  }
}
```

### 2. 短链跳转

```http
GET /s/{shortCode}
```

**行为**：
- 短码存在 → 302 重定向到原始 URL
- 短码不存在 → 404 Not Found
- 短码已过期 → 404 Not Found

---

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.1.x |
| JDK | OpenJDK | 25 (ZGC) |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis + Caffeine | - |
| 消息队列 | Kafka | - |
| 注册中心 | Nacos | - |
| 限流 | Sentinel | - |

---

## 配置说明

```yaml
shortlink:
  code-length: 6              # 短码长度
  code-alphabet: 2345...      # Base62 字符集（剔除 0/O/1/I/l）
  default-expire-days: 30     # 默认过期时间
  cache:
    max-size: 100000          # Caffeine 最大容量
    ttl-seconds: 3            # 本地缓存 TTL
  stats-flush-interval: 5     # 统计刷盘间隔（秒）
  stats-flush-size: 100       # 统计刷盘条数
```

---

## 部署

```bash
# 1. 执行数据库脚本
mysql -u root -p livemall < src/main/resources/schema.sql

# 2. 启动服务
mvn spring-boot:run

# 3. 访问 Swagger（可选）
http://localhost:8091/swagger-ui.html
```

---

## 监控指标

| 指标 | 说明 |
|------|------|
| `shortlink_create_total` | 创建短链总数 |
| `shortlink_redirect_total` | 跳转总数 |
| `shortlink_cache_hit_ratio` | 缓存命中率 |
| `shortlink_p99_latency_ms` | P99 延迟 |

---

## 演进路径

| 阶段 | 日读 | 方案 |
|------|------|------|
| **V1.0** | 100 万 | 当前架构（Caffeine + Redis Master） |
| **V2.0** | 1000 万 | Redis 读写分离扩容（1 主 6 从） |
| **V3.0** | 1 亿 | 多 IDC 双活 + CDN 边缘缓存 |
