# Shortly 改造计划（Go → Java）— 文档导航

> 目标：将现有的 Go 短链接平台（`shortly-api-service` + `shortly-kgs-service` 双微服务）完整、逐模块地改造为 Java 项目。
> 本目录下的文档是**给实施 agent 的完整规格说明**：按编号顺序阅读并执行，即可产出与 Go 版功能、接口契约、数据语义完全对齐的 Java 实现。

## 文档清单与依赖顺序

| 编号 | 文档 | 用途 | 实施阶段 |
|---|---|---|---|
| 00 | `00-README.md`（本文档） | 总览、技术选型、阅读指引 | — |
| 01 | `01-architecture-analysis.md` | 原项目架构与代码拆解（服务、数据层、流程、已知缺陷） | 阅读理解，改造依据 |
| 02 | `02-api-contract.md` | 全部 HTTP 端点的请求/响应/错误/认证/限流规格 | P1–P4 实现的验收基准 |
| 03 | `03-project-structure.md` | 目标 Maven 多模块工程结构、pom 配置、application.yml 模板 | P0 骨架 |
| 04 | `04-database-design.md` | PostgreSQL Flyway DDL、MongoDB 设计、数据迁移方案 | P0/P5 |
| 05 | `05-api-service-implementation.md` | API 服务逐模块实现规格（Auth/Profile/Url/Analytics/客户端） | P1–P4 |
| 06 | `06-kgs-service-implementation.md` | KGS 服务 + gRPC 实现规格（key 生成、队列、补货） | P3 |
| 07 | `07-cross-cutting.md` | 横切关注点：限流、JWT、缓存、异步、异常、日志、配置 | 随各阶段 |
| 08 | `08-migration-checklist.md` | Go→Java 代码映射表、实施里程碑、逐项验收清单 | 全程对照 |

## 技术选型（已定，实施时不得偏离）

| 层 | 选型 | 对应 Go 组件 |
|---|---|---|
| 语言/运行时 | Java 21 (LTS) | Go 1.24 |
| 构建 | Maven 3.9+，父 POM 多模块 | go.mod 多 module |
| 框架 | Spring Boot 3.3.x（Web MVC） | Gin |
| 服务架构 | 保留双服务微服务：`shortly-api-service`、`shortly-kgs-service` 两个 Maven 模块 | 两个 Go 服务 |
| ORM | Spring Data JPA (Hibernate 6) | GORM |
| DB 迁移 | Flyway（手写 SQL，对齐 GORM AutoMigrate 产物） | AutoMigrate |
| 认证 | jjwt 0.12.x + `BCryptPasswordEncoder`（仅引入 spring-security-crypto，不启用完整 Spring Security 过滤链） | golang-jwt/v5 + bcrypt |
| 服务间通信 | grpc-java + protobuf-maven-plugin，`key.proto` 原样复用 | grpc-go |
| 缓存 | `StringRedisTemplate`，JSON 序列化，key 命名与 Go 版一致 | go-redis v9 |
| 限流 | 自定义 Redis Lua 固定窗口拦截器（对齐 ulule `N-M` 语义 = N 次/分钟） | ulule/limiter |
| UA 解析 | `ua-parser-java`（`ua_parser.web` 包） | mssola/user_agent |
| IP 定位 | 保留 ipapi.co HTTP 调用 + Redis 缓存 24h | lib/analytics.go |
| 异步 | `@Async` + 专用线程池（对齐 goroutine 语义） | goroutine |
| 参数校验 | jakarta.validation + hibernate-validator + 自定义注解 | go-playground/validator |
| JSON | Jackson（snake_case 字段名与 Go 完全一致） | encoding/json |
| 日志 | SLF4J + Logback | slog |
| 测试 | JUnit 5 + Testcontainers（postgres/redis/mongo） | — |

## 实施顺序（agent 执行路线）

```
P0  骨架：03 → 04（建库脚本）→ 两模块可启动、/health 通过
P1  Auth + Profile：05 第 3、4 节 + 07 的 JWT
P2  Url 模块（CRUD/redirect/缓存/异步 analytics）：05 第 5 节
P3  KGS 服务 + gRPC 联通：06 + 05 第 6 节
P4  限流完善 + Analytics 查询 + 全局异常：07 + 05 第 7 节
P5  数据迁移 + 集成测试 + 对照验收：08 的验收清单
```

## 强制约定（所有文档通用）

1. **响应契约逐字段对齐**：字段名（snake_case）、HTTP 状态码、`{success, data, message}` / `{success, validation_error}` 结构不得改变（详见 02）。
2. **缓存 key 前缀不变**：`url:{shortKey}`（24h）、`user:profile:{email}`（30min）、`ip-country:{ip}`（24h）。
3. **Redis 逻辑库不变**：DB0=KGS 队列、DB1=API 缓存、DB2=限流。
4. **JWT 兼容**：HS256，claims `{user_id, email, exp}`（exp=24h），可读取 Go 版签发的 token（双跑过渡期）。
5. **修复清单**（Go 版缺陷，Java 版必须修复，见 01 第 6 节）：
   - analytics/url 详情增加 user_id 归属校验；
   - KGS 的 MongoDB `key` 加唯一索引 + 碰撞重试；
   - 限流使用单例 Redis 连接（不每路由新建）。
6. 禁止引入文档未列出的框架；确有需要时先记录偏差并说明理由。

## 原项目代码位置（只读参考）

```
D:\workspace\PROJECTS\shortly\services\shortly-api-service\   （API 服务）
D:\workspace\PROJECTS\shortly\services\shortly-kgs-service\   （KGS 服务）
D:\workspace\PROJECTS\shortly\proto\key.proto                  （gRPC 契约）
D:\workspace\PROJECTS\shortly\docker-compose.yaml              （postgres+redis，需补 mongo）
```
