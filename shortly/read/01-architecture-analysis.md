# 01 · 原项目架构与代码拆解（改造依据）

> 本部分完整拆解 Go 版 Shortly 的架构、数据层、业务流程与已知缺陷。实施 agent 在动手前必须通读，
> 后续文档（02–07）中的实现规格均以此为准。本文只做"是什么"，"怎么做"见 05/06/07。

---

## 1. 系统总览

纯 Go 微服务架构，仓库顶层有 3 个 Go module（`shortly-proto` 通过 `go.mod replace` 被两个服务引用）。

```
                        ┌─────────────────────────────────┐
                        │        PostgreSQL 16             │  users / urls / analytics
                        └───────────────▲─────────────────┘
                                        │ GORM
┌────────────┐  HTTP/JSON   ┌───────────┴───────────────┐   gRPC    ┌───────────────────────┐
│  客户端     ├────────────► │  shortly-api-service      │◄─────────►│  shortly-kgs-service   │
│ (302重定向) │              │  Gin REST（端口 .env）      │  GetKey   │  gRPC Server（端口 .env）│
└────────────┘              └───────────────────────────┘           └──────────┬────────────┘
                                        │ Redis DB1（缓存）                    │ Redis DB0（key 队列）
                                        ▼                                      ▼
                                 ┌─────────────┐                      ┌─────────────┐   MongoDB
                                 │   Redis     │◄────────────────────►│   Redis     │  shortkeys
                                 └─────────────┘ DB2=限流             └─────────────┘
```

一句话职责：
- **API 服务**：所有 HTTP 接口、认证、URL 生命周期、重定向、analytics 采集、缓存、限流；通过 gRPC 向 KGS 要 key。
- **KGS 服务**：预先生成 6 位 Base62 短 key（1000 个/批），MongoDB 持久化 + Redis DB0 队列，按需自动补货。

---

## 2. API 服务逐文件拆解

源码根：`services/shortly-api-service/`

### 2.1 入口与装配 `cmd/main.go`

启动顺序（Java 版用 Spring Boot 自动装配替代，但依赖顺序语义需保留）：
1. `utils.InitLogger()` — slog 全局日志；
2. `config.Init()` — godotenv 读 `.env`，9 个变量缺失即 panic；
3. `database.ConnectDB()` — GORM 连 PostgreSQL（连接池：MaxOpen 25 / MaxIdle 15 / 生命周期 5min）；
4. `redis.ConnectRedis()` — go-redis 连 **Redis DB1**；
5. `gin.Default()` + CORS（AllowOrigins `*`，方法 GET/POST/PUT/DELETE，头 Origin/Content-Type/Authorization，AllowCredentials true，MaxAge 12h）；
6. `clients.InitKGSClient()` — grpc 客户端（**insecure 明文**）连 `KGS_GRPC_ADDRESS`，全局 stub；
7. 路由分组：所有端点挂 `/api/v1`，注册 `UrlRouter / AuthRouter / HealthRouter / ProfileRouter / AnalyticsRouter`；
8. `server.Run(":" + PORT)`。

### 2.2 配置 `config/config.go`

| 环境变量 | 用途 |
|---|---|
| `PORT` | HTTP 监听端口 |
| `DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME` | PostgreSQL（DSN：`host=.. user=.. password=.. dbname=.. port=.. sslmode=disable`） |
| `JWT_SECRET` | HS256 签名密钥 |
| `REDIS_ADDR` | Redis 地址 |
| `KGS_GRPC_ADDRESS` | KGS gRPC 地址（如 `localhost:50051`） |

`.env.example` 中还出现 `POSTGRES_USER/POSTGRES_DB/POSTGRES_PASSWORD`（仅 docker-compose 用，Go 代码不读）。

### 2.3 路由 `internal/routes/*.go`（端点总表见 02）

- 每个路由函数接收 `*gin.RouterGroup`，返回 void —— Java 对应 Controller 类。
- 限流参数格式：`"50-m"`=50 次/分钟、`"20-M"`=20 次/分钟、`"5-M"`=5 次/分钟、`"10-m"`=10 次/分钟（ulule 大小写不敏感）。
- 认证：`middlewares.AuthMiddleware()` 为路由组级中间件。

### 2.4 处理器 `internal/handlers/*.go`

**关键事实：Go 版没有 Service/Repository 层**，handler 直接操作包级全局变量 `database.DB` 与 `redis.RedisClient`。Java 版需拆出 Service/Repository 层，但**行为必须逐条对齐**（见 05）。

各 handler 行为要点：

#### auth.go
- **Signup**：绑定 `{email, username, password}` → email 转小写+去空格、username 去空格 → 校验（email 必填+合法、username 2–15 字符、password ≥6）→ 按 email 查重（存在→409 `User already exists`）→ bcrypt 哈希（DefaultCost=10）→ 插入 → **201** 返回 `{success, data:{id,email,username,created}, message:"User registered successfully"}`。
- **Signin**：绑定 `{email, password}` → 校验 → 按 email 查（不存在→404 `User not found`）→ bcrypt 校验（失败→401 `Password is not valid`）→ 生成 JWT（claims `user_id/email/exp(24h)`）→ `SetCookie("token", token, 86400, "/", "", true, true)`（HttpOnly+Secure，maxAge 86400s）→ **200** 返回 `{success, token, data, message:"Login successful"}`。
- **Logout**：`SetCookie("token", "", -1, ...)` 清 cookie → 200 `{success, message:"Logout successfully"}`。**无 token 校验**。

#### profile.go
- **GetUserProfile**：从 context 取 `email` → 读缓存 `user:profile:{email}`（命中→200 直接返回缓存）→ 查库 → 组装 `UserDTO{id,email,username,created}` → 写缓存 **30min** → 200 `{success, data, message:"Profile retrieved successfully"}`。
- **UpdateUserProfile**：context 取 `email` → 绑定 `{username}`（必填 3–30）→ 查库 → `user.Username = trim(username)`，空→400 → Save → 更新缓存 30min → 200 `{success, data, message:"Profile updated successfully"}`。

#### url.go（核心）
- **CreateUrl**：
  1. context 取 `id`（int），缺失→401；
  2. 绑定 `{original_url, short_key?, title?}`，校验：original_url 必填+URL 合法、short_key 可选 2–50 且匹配 `^[a-zA-Z0-9_-]+$`、title 可选 ≤255；
  3. `WHERE original_url = ? AND user_id = ?` 查重 → 命中→**409** `This URL has already been shortened.`；
  4. `short_key` 为空 → `KGSClient.GetKey()` → 失败→500 `Failed to generate short key`；
  5. `WHERE short_key = ?` 查重 → 命中→**409** `The generated ShortKey already exists, please try again`；
  6. 插入 `Url{OriginalURL, ShortKey, Title, UserID:*string(id 转字符串)}` → 失败→500；
  7. goroutine：`SET url:{shortKey} <json(Url)> EX 86400`；
  8. **200**（注意不是 201）返回 `{success, data:{id, original_url, short_url, title}, message:"URL successfully created"}`。
- **GetAllUrls**：context 取 id → `WHERE user_id = ?` Find 全部 → 200 `{success, data:[UrlDetail...], message:"URLs retrieved successfully"}`（含 clicks、created_at）。
- **GetUrlDetails**：取 `shortKey` 路径参数（空→400）→ `WHERE short_key = ?` First（无→**404**）→ 200 返回详情。**无归属校验（缺陷 2）**。
- **RedirectToOriginalUrl**（无认证）：
  1. `shortKey` 空→400；
  2. 读缓存 `url:{shortKey}`：命中且反序列化成功 → 异步 `incrementClickCount(id)` + 异步 `storeAnalytics(...)` → **302 Found** 跳原 URL；
  3. 未命中 → `WHERE short_key = ?`（无→**404** JSON）→ goroutine 回填缓存 24h → 异步点击数+1、异步写 analytics → **302**。
- **incrementClickCount**：`UPDATE urls SET clicks = clicks + 1 WHERE id = ?`（注意：**不带 deleted_at 过滤**，GORM 普通 Update 语句）；
- **storeAnalytics**：采集 `ClientIP`、`User-Agent` 头、`Referer` 头 → `GetCountryFromIP(ip)`（ipapi.co，超时 2s，失败返回 `"Unknown"`，本机 `"Localhost"`）→ `ParseUserAgent(ua)` 得 device/browser/os → 插入 Analytics{UrlID: id 转字符串, ClickedAt: now, IPAddress, UserAgent, Referrer, Country, Device, Browser, OS}。
- **UpdateUrl**：
  1. 取 shortKey → `WHERE short_key = ?` First（无→404）；
  2. 绑定 `{short_url?, title?}`（注意 **JSON 字段名是 `short_url` 不是 `short_key`**，校验同上）；
  3. 若新 `short_url` 非空且不等于原值 → 查重，命中→409；
  4. `UPDATE urls SET short_key=?, title=? WHERE id=?`（仅更新非空字段；Go 版用 `Select("ShortKey","Title").Updates(map)`）；
  5. goroutine 删缓存 `url:{旧 shortKey}`；
  6. 200 返回 `{success, data:{id, original_url, short_url:新值, title:新值(可能空), clicks, updated_at}, message:"URL updated successfully"}` —— **未改字段返回空串（缺陷 4）**。
- **DeleteUrl**：取 shortKey → 查（无→404）→ 软删除（GORM Delete 置 deleted_at）→ goroutine 删缓存 → 200 `{success, message:"URL deleted successfully"}`。

#### analytics.go
- **GetAnalytics**：context 取 id → 路径参数 `urlId`（空→400）→ `WHERE url_id = ? ORDER BY clicked_at DESC` Find → 空→**404** `No analytics found for this URL` → 200 `{success, data:[{ipAddress, os, device, browser, userAgent, clickedAt, referrer, country}], message}`。
  - clickedAt 格式：`"2006-01-02 15:04:05"`（即 `yyyy-MM-dd HH:mm:ss`）。
  - **缺陷 1**：`database.DB.Where("id = ?", idStr).Error` 缺少模型对象，该行实际无效；无 URL 归属校验，任何登录用户可查任意 urlId。

#### health.go
- `GET /health/` → 200 `{success: true, message: "Server is up and running"}`（无认证无限流）。

### 2.5 中间件

#### auth.middleware.go
1. 优先读 Cookie `token`；无则读 `Authorization: Bearer <token>`；
2. 都无 → 401 `Unauthorized: No token provided`；
3. `VerifyToken` 失败 → 401 `Unauthorized: Invalid token`；
4. claims 解析：`user_id`（JWT 数字为 float64，转 int）、`email`（string），缺失→401；
5. `ctx.Set("id", int)`, `ctx.Set("email", email)`，放行。

#### limiter.go
- `RateLimiter("N-M")` 返回 handler 中间件；ulule 解析格式 `<count>-<period>`（period: S/M/H）。
- 限流 key = 客户端 IP；超过 → **429** `{error: "Too Many Requests"}`；内部错误 → 500 `{error: "Rate limiter internal error"}`。
- 存储：Redis **DB2**，key 前缀 `rate_limit`，MaxRetry 3。
- **缺陷 5**：每个路由注册处 `new` 一个 Redis 客户端（无复用）。

### 2.6 模型 `internal/models/*.go`

所有表都嵌入 `gorm.Model` → 公共列：`id BIGSERIAL`、`created_at`、`updated_at`、`deleted_at`（软删除，GORM 查询自动加 `WHERE deleted_at IS NULL`）。

```go
type User struct {
    gorm.Model
    Username string `gorm:"not null"`
    Email    string `gorm:"uniqueIndex;not null"`
    Password string `gorm:"not null"`
    Urls     []Url  `gorm:"foreignKey:UserID"`
}
type Url struct {
    gorm.Model
    OriginalURL string      `gorm:"not null"`            // TEXT
    ShortKey    string      `gorm:"size:50;uniqueIndex;not null"`
    Title       string      `gorm:"size:255"`
    UserID      *string     `gorm:"index"`               // ★ 字符串指针，非外键约束
    User        *User       `gorm:"foreignKey:UserID"`
    Clicks      int         `gorm:"default:0"`
    Analytics   []Analytics `gorm:"foreignKey:UrlID"`
}
type Analytics struct {
    gorm.Model
    UrlID     string    `gorm:"index;not null"`          // ★ 字符串
    Url       Url       `gorm:"foreignKey:UrlID"`
    ClickedAt time.Time `gorm:"autoCreateTime"`
    IPAddress string    `gorm:"not null"`
    UserAgent string    `gorm:"not null"`
    Referrer  string    `gorm:"size:255"`
    Country   string    `gorm:"size:100"`
    Device    string    `gorm:"size:50"`
    Browser   string    `gorm:"size:50"`
    OS        string    `gorm:"size:50"`
}
```

> ★ = 类型语义怪异点：`users.id` 是数字，但 `urls.user_id` 与 `analytics.url_id` 是字符串列，关联关系仅存在于 ORM 层，数据库无外键。Java 版**保留字符串列**以兼容存量数据（详见 04）。

### 2.7 DTO（响应字段即 JSON 契约，见 02）

| Go struct | JSON 字段 |
|---|---|
| UserDTO | `id, email, username, created` |
| CreateUrlResponseDTO | `id, original_url, short_url, title` |
| GetUrlResponseDTO | `id, original_url, short_url, title, clicks, created_at` |
| UpdateUrlResponseDTO | `id, original_url, short_url, title, clicks, updated_at` |
| AnalyticsResponse | `ipAddress, os, device, browser, userAgent, clickedAt, referrer, country` |

### 2.8 工具

- `utils/jwt.go`：`GenerateToken(userID uint, email string)` → HS256，claims `{user_id, email, exp: now+24h}`；`VerifyToken` 校验签名方法必须为 HMAC、解析 exp（过期→error）。
- `utils/password.go`：bcrypt `GenerateFromPassword`（cost 10）/ `CompareHashAndPassword`。
- `utils/logger.go`：slog JSON 输出。
- `validators/validators.go`：注册自定义校验 `shortkeychars` = `^[a-zA-Z0-9_-]+$`；校验失败返回 map `{字段名: "Invalid 字段名"}`（首字母大写：`OriginalURL`、`ShortKey`…——注意是 **Go 字段名首字母大写**，Java 版按 02 定死字段名）。
- `lib/analytics.go`：
  - `ParseUserAgent(ua)`：mssola/user_agent → mobile→`"Mobile"`、bot→`"Bot"`、否则 `"Desktop"`；browser=`"<name> <version>"`；os=`ua.OS()`。
  - `GetCountryFromIP(ip)`：先查缓存 `ip-country:{ip}` → `127.0.0.1`/`::1`→`"Localhost"` → `GET https://ipapi.co/{ip}/json/`（2s 超时，失败→`"Unknown"`）→ 取 `country_name` → 回填缓存 24h → 返回。

### 2.9 gRPC 客户端 `clients/kgs_client.go`

包级全局 `key.KeyServiceClient`；`InitKGSClient()` 用 insecure 凭据建连接。`CreateUrl` 中调用：`GetKey(ctx, &key.Empty{})` → `KeyResponse{Key}`。

---

## 3. KGS 服务逐文件拆解

源码根：`services/shortly-kgs-service/`

### 3.1 入口 `cmd/main.go`

1. 日志 → env（`PORT / MONGO_URI / MONGO_DB_NAME / REDIS_ADDR`）；
2. MongoDB 连接（20s 超时，退出前 Disconnect）；
3. Redis **DB0** 连接；
4. `grpc.NewServer()` + `RegisterKeyServiceServer(service.NewKeyServiceServer())`，监听 `:PORT`；
5. 另起 goroutine 起 **HTTP :8081** `GET /api/v1/health` → `{"success": true, "message": "KGS server is up and running"}`（硬编码端口 8081）。

### 3.2 gRPC 服务 `internal/service/key_service.go` — `GetKey`

```
1. LLEN shortly-kgs-redis-queue
2. if len < 200 → kgs.GenerateKeys(1000)；失败 → 返回错误
3. RPOP 队列 → 空/失败 → 返回错误
4. Mongo shortkeys 集合：updateOne({key: keyVal}, {$set: {status: "used"}})
5. ModifiedCount == 0 → LPUSH 回滚 → 返回错误 "failed to update key status in DB, pushed key ... back"
6. 返回 KeyResponse{key}
```

> 注意并发语义：RPOP 已从队列取出，若 Mongo 更新失败则放回队首（LPUSH），下次重试。无重试上限。

### 3.3 生成器 `internal/kgs/generator.go` — `GenerateKeys(count)`

```
for i in 0..count:
    key = GenerateRandomKey(6)          // 见 3.4
    keys 追加 {key, status:"available", createdAt: now}
    redisKeys 追加 key
Mongo InsertMany(keys)                  // 失败→整体报错
Redis LPUSH queue redisKeys...(批量)     // 失败→整体报错
```

### 3.4 Base62 `internal/utils/base62.go`（⚠️ 含严重缺陷，Java 版必须修复）

- `charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"`；
- 源码（`services/shortly-kgs-service/internal/utils/base62.go`）：
  ```go
  randNum, err := rand.Int(rand.Reader, big.NewInt(base))   // base = 62
  for randNum.Cmp(big.NewInt(0)) == 1 {                     // while randNum > 0
      remainder := randNum.Int64() % base
      result.WriteByte(charset[remainder])
      randNum.Div(randNum, big.NewInt(base))
  }
  ```
- **实际行为**：`rand.Int(rand.Reader, big.NewInt(62))` 生成的是 `[0, 62)` 的**单值**，循环至多执行一次（写 1 个字符后除 62 得 0 退出；randNum=0 时一个字符都不写），随后补 `'0'` 到 6 位。
- **结论**：Go 版生成的 key 只有 `"000000"` 及 `"00000X"`（X 为 62 种字符）共 63 种可能值，**并非 6 位全空间**。批量生成 1000 个必然大量重复，靠 API 侧 `short_key` 查重兜底才未崩坏。
- **Java 版修复要求**：实现语义上正确的 62^6 ≈ 568 亿全空间随机 key（`SecureRandom` + BigInteger，见 06 §4），配合 MongoDB 唯一索引彻底消除重复。

### 3.5 常量 `internal/constants/constants.go`

```go
RedisQueueName = "shortly-kgs-redis-queue"
RedisCounter   = "shortly-kgs-queue-counter"   // 目前未使用，保留定义即可
QueueLength    = 200                            // 低于此值触发补货
KeyCount       = 1000                           // 每批生成数
```

### 3.6 模型 `internal/models/shortkey.go`

```go
ShortKey { _id ObjectID, key string, status string(available/used), createdAt time }
```

---

## 4. 数据层总表

| 存储 | 用途 | 细节 |
|---|---|---|
| PostgreSQL 16 | users / urls / analytics | 软删除；表结构见 04 V1__init.sql |
| MongoDB | shortkeys 集合 | `{key, status, createdAt}`，无索引 |
| Redis DB0 | KGS key 队列 | LPUSH 生产 / RPOP 消费，key=`shortly-kgs-redis-queue` |
| Redis DB1 | API 缓存 | `url:{key}`(24h)、`user:profile:{email}`(30min)、`ip-country:{ip}`(24h) |
| Redis DB2 | 限流计数 | ulule，前缀 `rate_limit`，key=IP |

---

## 5. 关键业务流程（时序要点）

### 5.1 创建短链
```
校验 → original_url+user_id 查重(409)
→ [short_key 空] gRPC GetKey → KGS: 队列<200→补1000 → RPOP → Mongo 标 used(失败回滚)
→ short_key 查重(409) → INSERT → 异步写缓存(24h) → 200
```

### 5.2 重定向（无认证）
```
GET url:key 缓存命中 → 302 + 异步(clicks+1, analytics 落库)
缓存未命中 → DB 查(404) → 异步回填缓存 → 302 + 异步(clicks+1, analytics 落库)
analytics: IP + UA(device/browser/os) + Referer + ipapi.co 国家(缓存24h)
```

### 5.3 认证
```
Cookie token → 无则 Bearer header → 都无 401 → HS256 验签+过期 → 取 user_id/email 进上下文
```

---

## 6. 已知缺陷（Java 版必须修复）

| # | 位置 | 问题 | 修复要求 |
|---|---|---|---|
| 1 | handlers/analytics.go | `Where("id = ?", idStr)` 缺模型，无 URL 归属校验，任意登录用户可查任意 urlId analytics | 查询前校验该 url 属于当前用户（urlId 不存在→404） |
| 2 | handlers/url.go GetUrlDetails | 无归属校验 | 校验 url.userId == 当前用户，否则 404 |
| 3 | kgs/generator+base62 | **严重**：`rand.Int(rand.Reader, big.NewInt(62))` 只生成 `[0,62)` 单值，key 实际仅 63 种可能（`000000`+`00000X`），批量 1000 必然大量重复；且 Mongo 无唯一索引 | Java 用 `SecureRandom` 实现 62^6 全空间（06 §4）；Mongo 建 `key` 唯一索引；插入冲突重试；API 侧保留 short_key 查重 |
| 4 | handlers/url.go UpdateUrl | 响应中未修改字段返回空值（title/short_url） | 响应回填 DB 现值 |
| 5 | middlewares/limiter.go | 每路由新建 Redis 客户端 | 单例复用连接 |
| 6 | url.go CreateUrl | user_id 存字符串（类型混乱） | 保持字符串列（兼容数据），但 Java 内部用 Long 传递 |
| 7 | url.go RedirectToOriginalUrl | 点击计数与 analytics 各开 goroutine 直连 DB，高并发压力 | Java 用有界线程池 + 可合并为同一异步任务 |

> 除以上修复外，其余行为（状态码、字段名、响应结构、缓存 TTL）必须与 Go 版完全一致。

## 7. 技术栈清单（Go 侧，供对照）

gin v1.10、gorm v1.25 + pgx、go-redis v9、grpc-go v1.72、golang-jwt/v5、bcrypt、ulule/limiter v3、go-playground/validator v10、mssola/user_agent、slog、godotenv、mongo-driver v1.17。
