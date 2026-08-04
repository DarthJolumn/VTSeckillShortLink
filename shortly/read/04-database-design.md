# 04 · 数据库设计（PostgreSQL + MongoDB）

> 本文给出与 GORM AutoMigrate 产物逐列对齐的 Flyway DDL、MongoDB 集合设计，以及从 Go 版存量库迁移数据的方案。
> 表结构必须与 Go 版 GORM 生成结果一致（存量数据可直接复用）；Java 实体映射见 §4。

## 1. PostgreSQL 表结构（Flyway V1__init.sql）

### 1.1 命名与类型规则（对齐 GORM/postgres）

- 表名：GORM 默认复数蛇形 —— `users`、`urls`、`analytics`；列名：Go 字段蛇形 —— `original_url`、`short_key`、`user_id` 等。
- `gorm.Model` 四列：`id BIGSERIAL PK`、`created_at TIMESTAMPTZ`、`updated_at TIMESTAMPTZ`、`deleted_at TIMESTAMPTZ NULL`（软删除，查询自动过滤 `deleted_at IS NULL`）。
- `id` 为 BIGSERIAL（GORM 在 PostgreSQL 用 `bigserial`）；Go 侧 `uint`。
- **列类型规则（关键）**：GORM/PostgreSQL 中**未指定 `size` 的 string 字段一律生成 `text`**；只有带 `size:N` 标签的才生成 `varchar(N)`。因此下列字段为 `text`：`users.username/email/password`、`urls.original_url/user_id`、`analytics.url_id/ip_address/user_agent`；`varchar` 仅限 `urls.short_key(50)/title(255)`、`analytics.referrer(255)/country(100)/device(50)/browser(50)/os(50)`。DDL 必须按此书写，否则与 Go 存量库不一致。
- GORM 会为唯一索引/普通索引自动命名（`idx_<table>_<col>`），Java 版索引名可自定义，但**唯一性约束必须存在**。

### 1.2 V1__init.sql（完整）

```sql
-- users 表
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    username    TEXT NOT NULL,
    email       TEXT NOT NULL,
    password    TEXT NOT NULL
);
CREATE UNIQUE INDEX uq_users_email ON users (email);          -- GORM uniqueIndex
CREATE INDEX idx_users_deleted_at ON users (deleted_at);      -- GORM 软删除辅助索引（可选）

-- urls 表
CREATE TABLE urls (
    id            BIGSERIAL PRIMARY KEY,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,
    original_url  TEXT NOT NULL,
    short_key     VARCHAR(50) NOT NULL,
    title         VARCHAR(255),
    user_id       TEXT,                  -- ★ 字符串类型（Go *string，无 size → text），保持兼容
    clicks        INTEGER DEFAULT 0
);
CREATE UNIQUE INDEX uq_urls_short_key ON urls (short_key);    -- GORM uniqueIndex
CREATE INDEX idx_urls_user_id ON urls (user_id);              -- GORM index
CREATE INDEX idx_urls_deleted_at ON urls (deleted_at);

-- analytics 表
CREATE TABLE analytics (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    url_id      TEXT NOT NULL,           -- ★ 字符串类型（Go string，无 size → text），保持兼容
    clicked_at  TIMESTAMPTZ,
    ip_address  TEXT NOT NULL,           -- 无 size → text
    user_agent  TEXT NOT NULL,
    referrer    VARCHAR(255),
    country     VARCHAR(100),
    device      VARCHAR(50),
    browser     VARCHAR(50),
    os          VARCHAR(50)
);
CREATE INDEX idx_analytics_url_id ON analytics (url_id);      -- GORM index
CREATE INDEX idx_analytics_deleted_at ON analytics (deleted_at);
```

> **说明**：
> 1. `user_id`/`url_id` 保持字符串列（Go 版为 `*string`/`string`）。Java 实体对应 `String` 字段，应用层用 `Long` 转换。
> 2. `clicks INTEGER DEFAULT 0` 为 Go 侧 `default:0`。
> 3. 索引命名带 `uq_`/`idx_` 前缀仅为本工程约定，与 GORM 无强绑定。

### 1.3 其他 Flyway 脚本约定

- `V1__init.sql` 为基线；后续演进 `V2__xxx.sql`…。`flyway.baseline-on-migrate=true`（若对接存量库）。
- 不建外键（与 Go 版一致，关联仅 ORM 层）。

## 2. MongoDB 设计

### 2.1 shortkeys 集合

```jsonc
{
  "_id": ObjectId,
  "key": "abc123",            // 6 位 Base62
  "status": "available",      // "available" | "used"
  "createdAt": ISODate        // Go bson 字段名 createdAt
}
```

### 2.2 索引（修复项 3）

```js
db.shortkeys.createIndex({ key: 1 }, { unique: true });
db.shortkeys.createIndex({ status: 1 });
```

唯一索引导致重复插入时报 DuplicateKey —— 生成器需捕获并重试（见 06 §4）。

### 2.3 Java 实体映射

```java
@Document(collection = "shortkeys")
public class ShortKey {
    @Id private String id;                 // ObjectId 字符串
    @Indexed(unique = true) private String key;
    private String status;                 // ShortKeyStatus.AVAILABLE / USED
    @Field("createdAt") private Instant createdAt;
}
```

## 3. Java 实体（PostgreSQL）映射要点

### User
```java
@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "created_at") private Instant createdAt;   // @PrePersist 赋值
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;   // 软删除
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false) private String username;  // text 列
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false) private String email;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false) private String password;
}
```
> **⚠️ text 列映射**：DDL 中为 `text` 的字段（username/email/password/original_url/user_id/url_id/ip_address/user_agent），Hibernate 6 默认按 `varchar(255)` 映射，`ddl-auto: validate` 会因列类型不一致报错。统一加 `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`（import `org.hibernate.annotations.JdbcTypeCode` 与 `org.hibernate.type.SqlTypes`）即可通过校验。也可整体改用 `ddl-auto: none`（表结构完全由 Flyway 负责），二选一。
- 软删除策略：**推荐 JPA `@SQLDelete` + `@SQLRestriction`**（对齐 GORM 行为）：
  - `@SQLDelete(sql = "UPDATE users SET deleted_at = now() WHERE id = ?")`
  - `@SQLRestriction("deleted_at IS NULL")`
- `created_at/updated_at` 用 `@PrePersist/@PreUpdate` 或 Hibernate `@CreationTimestamp/@UpdateTimestamp`（对齐 GORM 的 autoCreateTime/autoUpdateTime）。

### Url
```java
@Entity @Table(name = "urls")
public class Url {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Instant createdAt, updatedAt, deletedAt;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "original_url", nullable = false) private String originalUrl;
    @Column(name = "short_key", nullable = false, length = 50) private String shortKey;
    private String title;                       // length 255
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "user_id") private String userId;    // ★ 字符串
    private int clicks;                         // default 0
}
```
- 软删除注解同上（table urls）。
- **不映射关联**（Go 版 User/Url/Analytics 有 ORM 关联但无外键；Java 版避免 `@ManyToOne`，按 ID 查询即可，行为等价且更简单）。

### Analytics
```java
@Entity @Table(name = "analytics")
public class Analytics {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Instant createdAt, updatedAt, deletedAt;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "url_id", nullable = false) private String urlId;   // ★ 字符串
    @Column(name = "clicked_at") private Instant clickedAt;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "ip_address", nullable = false) private String ipAddress;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name = "user_agent", nullable = false) private String userAgent;
    private String referrer;
    private String country;
    private String device;
    private String browser;
    private String os;
}
```
- 软删除注解同上（table analytics）。

## 4. Repository 规格（接口签名级）

### UserRepository
```java
Optional<User> findByEmail(String email);
boolean existsByEmail(String email);
```

### UrlRepository
```java
Optional<Url> findByShortKey(String shortKey);
List<Url> findByUserId(String userId);
boolean existsByOriginalUrlAndUserId(String originalUrl, String userId);
boolean existsByShortKey(String shortKey);
// 点击计数（修复项 7：合并为单条原子更新）
@Modifying @Query("update Url u set u.clicks = u.clicks + 1 where u.id = :id")
int incrementClicks(@Param("id") Long id);
```

### AnalyticsRepository
```java
List<Analytics> findByUrlIdOrderByClickedAtDesc(String urlId);
```
> 查询均自动带 `@SQLRestriction` 的软删除过滤（与 GORM `First/Find` 行为一致）。

## 5. 存量数据迁移方案（P5）

### 5.1 数据准备（原 Go 库 → 新库）

| 源 | 目标 | 方式 |
|---|---|---|
| Go 版 PostgreSQL（users/urls/analytics） | 新 PostgreSQL | `pg_dump --data-only` + `pg_restore` 或 `INSERT ... SELECT`（跨实例时用 `COPY`）；表结构已被 Flyway 建好，只需搬数据 |
| Go 版 MongoDB shortkeys | 新 MongoDB | `mongodump`/`mongorestore`；**注意**：加唯一索引前先 `db.shortkeys.aggregate` 查重，删除重复 key 记录（Go 版无索引，可能存在重复） |
| Redis 缓存 | 无 | 不需要迁移（缓存自愈，TTL 短） |

### 5.2 双跑过渡（可选）

- 新旧 API 同时运行：JWT 同 secret 互认（见 07 §2）；缓存 key 前缀相同，可共享 Redis DB1；**写入方冲突风险**——建议过渡期只让新 API 写库。
- KGS 队列可共享（同一 Redis DB0 队列名），但新旧两套生成器同时补货需谨慎，过渡期建议只启用一套。

### 5.3 校验清单（迁移后）

1. `users` 行数一致；抽查 email 唯一性。
2. `urls.short_key` 唯一约束可成功建立（无重复）。
3. 迁移后跑 02 的冒烟用例：旧账号登录 → 旧短链 302 跳转 → 点击后 analytics 新增记录。
4. `deleted_at` 非空的行：新库查询结果应与旧库一致（软删数据保留）。
