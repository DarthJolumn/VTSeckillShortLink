# 08 · 实施里程碑、Go→Java 映射表与验收清单

> 本文是把整套方案落地的执行地图：Go 文件 → Java 类映射、实施里程碑（P0–P5）、每阶段验收标准、切换上线检查项。
> 实施 agent 按本文 + 对应编号文档逐步完成，每阶段完成即按验收标准自测。

## 1. Go → Java 代码映射表（全量）

### 1.1 shortly-api-service

| Go 源文件 | Java 目标 | 文档 |
|---|---|---|
| `cmd/main.go` | `ShortlyApiApplication` + `config/` 装配 | 03, 05 §1 |
| `config/config.go` | `application.yml` + `@ConfigurationProperties` | 03 §6 |
| `internal/routes/*.go` | `controller/*Controller` + 方法注解 | 02, 05 |
| `internal/handlers/auth.go` | `AuthController` + `AuthService` | 05 §3 |
| `internal/handlers/profile.go` | `ProfileController` + `ProfileService` | 05 §4 |
| `internal/handlers/url.go` | `UrlController` + `UrlService` | 05 §5 |
| `internal/handlers/analytics.go` | `AnalyticsController` + `AnalyticsService` | 05 §7 |
| `internal/handlers/health.go` | `HealthController` | 02 §4.1 |
| `internal/models/user.go` | `entity/User.java` | 04 §3 |
| `internal/models/url.go` | `entity/Url.java` | 04 §3 |
| `internal/models/analytics.go` | `entity/Analytics.java` | 04 §3 |
| `internal/dto/*.go` | `dto/request/*`、`dto/response/*` | 02 §4 |
| `internal/middlewares/auth.middleware.go` | `security/JwtAuthInterceptor` + `CurrentUserHolder` | 07 §1 |
| `internal/middlewares/limiter.go` | `ratelimit/*`（注解+拦截器+Lua） | 07 §2 |
| `internal/utils/jwt.go` | `security/JwtUtil` | 07 §1 |
| `internal/utils/password.go` | `BCryptPasswordEncoder`（spring-security-crypto） | 05 §3 |
| `internal/utils/logger.go` | logback | 07 §9 |
| `internal/validators/*.go` | `validation/*` 自定义注解 + 校验规则 | 07 §4 |
| `internal/redis/redis.go` | `StringRedisTemplate`（DB1） | 03 §6, 07 §3 |
| `internal/database/database.go` | DataSource + HikariCP 配置 | 03 §6 |
| `internal/clients/kgs_client.go` | `KeyServiceClient` | 05 §6 |
| `internal/lib/analytics.go` | `GeoIpService` + `UserAgentParser` | 05 §8 |
| —（新增分层） | `repository/*`, `service/*`, `exception/*`, `config/*` | 03 §1 |

### 1.2 shortly-kgs-service

| Go 源文件 | Java 目标 | 文档 |
|---|---|---|
| `cmd/main.go` | `ShortlyKgsApplication` + gRPC/Health 装配 | 06 §2 |
| `config/config.go` | `application.yml` | 03 §6 |
| `internal/service/key_service.go` | `KeyService` | 06 §6 |
| `internal/kgs/generator.go` | `KeyGenerator` | 06 §5 |
| `internal/utils/base62.go` | `util/Base62` | 06 §4 |
| `internal/models/shortkey.go` | `entity/ShortKey` | 04 §2 |
| `internal/redis/redis.go` | `StringRedisTemplate`（DB0） | 03 §6 |
| `internal/database/database.go` | MongoTemplate / MongoClient | 04 §2 |
| `internal/constants/constants.go` | `constant/Constants` | 06 §3 |
| proto `key.proto` → `KeyServiceGrpc` | 共享模块 `shortly-proto` | 03 §3 |

## 2. 实施里程碑

### P0 · 骨架与基建（文档 03, 04）
- [ ] 创建 `java/` 父 POM + 三个模块（proto/api/kgs）编译通过
- [ ] `java/docker-compose.yaml`：postgres + redis + mongo 可起
- [ ] Flyway `V1__init.sql` 落库成功；`ddl-auto: validate` 与实体一致
- [ ] MongoDB 唯一索引创建成功
- [ ] 两个服务能启动，健康检查可访问：
  - `GET :8080/api/v1/health/` → `{"success": true, ...}`
  - `GET :8081/api/v1/health` → `{"success": true, "message": "KGS server is up and running"}`
- [ ] gRPC 客户端/服务端连通（KGS `getKey` 可被调用）
- **验收**：两服务 Docker 化或本地启动无错；Flyway 无失败记录。

### P1 · Auth + Profile（文档 05 §3/§4, 07 §1）
- [ ] signup/signin/logout 按 02 §4.2/4.3/4.4 逐字段一致
  - 400 校验错误 key 为 `Email/Password/Username`
  - signin 种 Cookie `token`（HttpOnly+Secure）+ 响应体 `token`
  - 409 重复邮箱、404 用户不存在、401 密码错误
- [ ] JWT：用 Go 版 `JWT_SECRET` 生成 token；解析 Go 版 token 通过（手动验证）
- [ ] get/update profile + Redis 缓存（30min）
- **验收**：用 curl/Postman 对照 02 §4 全字段 diff；缓存命中返回相同 DTO。

### P2 · Url 模块（文档 05 §5, 07 §3/§5）
- [ ] create/list/details/update/delete 五接口 + 302 redirect
- [ ] 归属校验（修复项 1/2）生效
- [ ] 缓存：create 写缓存(24h)、redirect 命中/回填、update/delete 清除
- [ ] 异步：点击+1 与 analytics 落库（修复项 7）
- **验收**：创建→重定向→删除全链路；重复 original_url 409；short_key 冲突 409；缓存命中路径日志 `URL served from Redis cache`。

### P3 · KGS + gRPC + 限流（文档 06, 07 §2）
- [ ] KGS GetKey：队列 <200 补货 1000、RPOP、Mongo 标 used、失败回滚
- [ ] Base62 生成器为 62^6 全空间（修复项 3：Go 版仅 63 种 key）+ Mongo 唯一索引 + 冲突重试
- [ ] 单批次 1000 个 key 无重复（测试断言 Set size == 1000）
- [ ] API 端 gRPC 异常 → 500 `Failed to generate short key`
- [ ] `@RateLimit` 注解按 02 §5 全端点配置；429 响应 `{"error":"Too Many Requests"}`
- **验收**：压测并发创建短链不产生重复 short_key；连打 > limit 次得到 429。

### P4 · Analytics + 异常收口（文档 05 §7, 07 §8）
- [ ] `GET /analytics/:urlId` 归属校验 + 排序 + countedAt 格式
- [ ] 全局异常：400/401/404/409/429/500 文案全对齐
- [ ] GeoIpService 与 UserAgentParser 单元测试
- **验收**：跨用户查询他人 analytics → 404；`clickedAt` 格式 `yyyy-MM-dd HH:mm:ss`。

### P5 · 数据迁移与集成测试（文档 04 §5, 08 §4）
- [ ] pg_dump/mongorestore 数据迁移脚本
- [ ] Mongo 重复 key 清理后再建唯一索引
- [ ] JUnit5 + Testcontainers 集成测试（Auth/Url/Redirect/Analytics/KGS）
- [ ] 旧数据登录、旧短链 302、点击新增 analytics 全通
- **验收**：见「上线切换检查单」。

## 3. 修复项落地核对（01 §6）

| # | 修复 | 落地位置 | 完成 |
|---|---|---|---|
| 1 | analytics 归属校验 | `AnalyticsService.getAnalytics`（05 §7） | ☐ |
| 2 | 详情归属校验 | `UrlService.details`（05 §5） | ☐ |
| 3 | **Base62 全空间修复**：Go 版仅 63 种 key → Java 实现 62^6 全空间；Mongo 唯一索引 + 冲突重试 | `util/Base62`/`KeyGenerator`/Mongo index（04 §2.2, 06 §4/5） | ☐ |
| 4 | 更新响应回填现值 | `UrlService.update`（05 §5） | ☐ |
| 5 | 限流连接单例 | `RateLimitRedisConfig`（07 §2） | ☐ |
| 6 | user_id 字符串列保持 | Entity/DB（04 §1.2） | ☐ |
| 7 | 异步合并 | `AsyncClickService`（07 §5） | ☐ |

## 4. 上线切换检查单

- [ ] **契约**：用 Go 版线上环境采集的真实响应与 Java 版逐对 diff（02 §4 全端点）
- [ ] **数据**：`users/urls/analytics` 行数一致；`short_key` 无重复（唯一索引建成功）
- [ ] **兼容**：旧短链全部可 302（迁移后点测）；旧账号可登录（bcrypt 同格式）
- [ ] **双跑**（可选）：同 `JWT_SECRET` 互验 token；共享 Redis DB1 缓存前缀；只有一套系统写库
- [ ] **KGS**：队列长度与 Mongo used/available 计数合理；补货日志正常
- [ ] **性能冒烟**：redirect 从缓存直出（无 DB）；限流在阈值生效
- [ ] **回滚预案**：保留 Go 版部署与数据快照；Java 失败可切回

## 5. 已知边界与决策记录

| 决策 | 说明 |
|---|---|
| 双服务架构保留 | 尊重原 KGS 设计，不合并单体 |
| gRPC 保留 | 不换 REST（性能与架构一致性） |
| 校验错误 key 用 Go 字段名 | 兼容旧客户端（UpdateUrl 的 key 也是 `ShortKey`，Go 字段名即 `ShortKey`）；Java 内部 DTO 字段不因此改名 |
| Base62 生成范围以修复为准 | Go 版实现缺陷只产 63 种 key（01 §3.4），不照搬；Java 实现 62^6 全空间并用唯一索引保证去重 |
| 归属校验为增强项 | Go 版无、Java 版补上；如需严格 1:1 行为可关闭（默认开启） |
| text 列用 @JdbcTypeCode(SqlTypes.LONGVARCHAR) | 对齐 GORM text 产物并通过 `ddl-auto: validate`（04 §3） |
| analytics clickedAt 时区 | 用服务器默认时区（对齐 Go `Format`），测试固定时区下断言 |
| ASYNC 拒绝策略 CallerRunsPolicy | 防任务丢弃，队列满时同步执行 |
| 单实例 KGS 无分布式锁 | 唯一索引兜底；多实例如需强一致再加分布式锁（后续演进） |

## 6. 最终交付物清单

- [ ] `java/` 工程（parent + 3 模块）可 `mvn clean package` 成功
- [ ] `docker-compose.yaml` 一健起中间件
- [ ] Flyway 脚本 + 数据迁移脚本
- [ ] 全部文档（本目录 9 篇，00–08）+ 与 02 契约对照的接口测试报告
- [ ] 修复项核对完成（§3 全 ☑）