# 02 · API 契约规格（HTTP）

> 本文是 API 服务对外契约的唯一基准。所有端点、状态码、JSON 字段名、错误结构、Cookie 行为必须与下表**逐字节一致**（键顺序不要求一致，键名与值格式必须一致）。实施中以此文做对照验收。

## 1. 通用约定

- 统一前缀：`/api/v1`。
- 时间格式（响应）：`created_at`/`updated_at` → **RFC3339 格式**（Go `time.Time` 默认序列化为 `2025-01-01T20:00:00.123456+08:00` 之类，含时区偏移与纳秒）。Java 端须按 **07 §6** 配置，输出相同时区与精度的 RFC3339 字符串，验收时字符串逐字节对齐；**例外**：analytics 的 `clickedAt` 为字符串 `"yyyy-MM-dd HH:mm:ss"`。
- 成功响应结构：`{"success": true, "data": <object|array>, "message": "<文案>"}`。
- 校验失败响应：`{"success": false, "validation_error": {"<字段名>": "Invalid <字段名>"}}`，HTTP **400**。
  - Go 版字段名为 struct 首字母大写名（如 `Email`、`Password`、`OriginalURL`、`ShortKey`、`Title`、`Username`）。**Java 版沿用这些名字**（即校验错误 key 为 `Email`、`Password` 等，非 camelCase/snake_case；UpdateUrl 的字段虽 json tag 为 `short_url`、Go 字段名仍是 `ShortKey`），字段对应表见 §6。
- 普通错误响应：`{"success": false, "error": "<文案>"}`。
- 限流响应：HTTP **429**，body `{"error": "Too Many Requests"}`。
- CORS：允许所有来源，方法 `GET, POST, PUT, DELETE`，允许头 `Origin, Content-Type, Authorization`，AllowCredentials true，预检缓存 12h。

## 2. 端点总表

| 方法 | 路径 | 认证 | 限流 | Handler | 说明 |
|---|---|---|---|---|---|
| GET | `/api/v1/health/` | 无 | 无 | HealthCheck | 存活检查 |
| POST | `/api/v1/auth/signup` | 无 | 5-M | Signup | 注册 |
| POST | `/api/v1/auth/signin` | 无 | 10-M | Signin | 登录 |
| POST | `/api/v1/auth/logout` | 无 | 10-M | Logout | 登出（清 cookie） |
| GET | `/api/v1/profile/` | JWT | 20-M | GetUserProfile | 我的资料 |
| PATCH | `/api/v1/profile/update` | JWT | 5-M | UpdateUserProfile | 改用户名 |
| GET | `/api/v1/url/redirect/:shortKey` | **无** | 50-m | RedirectToOriginalUrl | 302 重定向 |
| GET | `/api/v1/url/` | JWT | 20-M | GetAllUrls | 我的 URL 列表 |
| POST | `/api/v1/url/shorten` | JWT | 5-M | CreateUrl | 创建短链 |
| GET | `/api/v1/url/:shortKey` | JWT | 20-M | GetUrlDetails | 短链详情 |
| PATCH | `/api/v1/url/:shortKey` | JWT | 5-M | UpdateUrl | 更新短链 |
| DELETE | `/api/v1/url/:shortKey` | JWT | 5-M | DeleteUrl | 删除短链 |
| GET | `/api/v1/analytics/:urlId` | JWT | 10-m | GetAnalytics | URL 点击明细 |

> `:shortKey` 为路径参数，大小写敏感。注意路由注册顺序：`/url/redirect/:shortKey` 必须**先于** `/url/:shortKey` 注册（Gin 通配匹配），Spring MVC 天然精确匹配不受影响，但保持先注册更稳。

## 3. 认证细节

- Token：JWT HS256，claims 必须为 `{"user_id": <long>, "email": "<email>", "exp": <unix 秒>}`，有效期 **24 小时**。
- 传递方式（优先级）：① Cookie `token`（HttpOnly、Secure、path `/`、maxAge 86400s）；② 请求头 `Authorization: Bearer <token>`。
- 中间件行为：
  - 两种都没有 → 401 `{"success": false, "error": "Unauthorized: No token provided"}`；
  - 验签/解析失败 → 401 `{"success": false, "error": "Unauthorized: Invalid token"}`；
  - claims 缺 `user_id`/`email` → 401 `Unauthorized: Invalid user ID` / `Unauthorized: Invalid email`；
  - 通过后将 `userId`（Long）、`email`（String）放入请求上下文（ThreadLocal 或 request attribute，见 07）。
- 登录响应体同时包含 `token` 字段 + 种 Cookie；登出仅清 Cookie（过期时间为 -1）。
- 过期时间判定以 `exp` 为准；`JWT_SECRET` 与 Go 版同值时可互相验证（双跑过渡期）。

## 4. 端点详细规格

### 4.1 GET /api/v1/health/
```
200 {"success": true, "message": "Server is up and running"}
```

### 4.2 POST /api/v1/auth/signup
请求体：`{"email": string, "username": string, "password": string}`

处理步骤（按序，命中即返回）：
1. email → trim + 小写；username → trim。
2. 校验（失败 → 400 validation_error，字段名见 §6）：
   - email：必填、合法邮箱；
   - username：必填、长度 2–15；
   - password：必填、长度 ≥6。
3. 邮箱已存在 → **409** `{"success": false, "error": "User already exists"}`。
4. bcrypt 哈希（cost 10）→ 插入。
5. **201** `{"success": true, "data": {"id": long, "email": string, "username": string, "created": <ISO时间>}, "message": "User registered successfully"}`

### 4.3 POST /api/v1/auth/signin
请求体：`{"email": string, "password": string}`

1. email → trim + 小写；校验同 signup（email 必填+合法、password ≥6）→ 400。
2. 邮箱不存在 → **404** `{"success": false, "error": "User not found"}`。
3. 密码错误 → **401** `{"success": false, "error": "Password is not valid"}`。
4. 签发 token → 种 Cookie `token`（HttpOnly+Secure，maxAge 86400）。
5. **200** `{"success": true, "token": "<jwt>", "data": {"id","email","username","created"}, "message": "Login successful"}`

### 4.4 POST /api/v1/auth/logout
1. 清 Cookie `token`（maxAge -1，同名同 path）。
2. **200** `{"success": true, "message": "Logout successfully"}`

### 4.5 GET /api/v1/profile/（JWT）
1. 从上下文取 email（缺失 → 401 `Unauthorized: Email missing`）。
2. 读缓存 `user:profile:{email}`：命中 → **200** 直接返回缓存 DTO（同 §4.7 结构，message `"Profile retrieved successfully"`）。
3. 查库：无 → **404** `{"success": false, "error": "User not found"}`。
4. 回填缓存 **30min**。
5. **200** `{"success": true, "data": {UserDTO}, "message": "Profile retrieved successfully"}`

### 4.6 PATCH /api/v1/profile/update（JWT）
请求体：`{"username": string}`（必填 3–30）

1. 上下文取 email（缺失 → 401）。
2. username trim 后为空 → **400** `{"success": false, "error": "Username cannot be empty"}`。
3. 查库：无 → 404。
4. 更新 username → 刷新缓存 30min。
5. **200** `{"success": true, "data": {UserDTO}, "message": "Profile updated successfully"}`

### 4.7 UserDTO（复用于 4.5/4.6 的 data）
```json
{"id": <long>, "email": "<string>", "username": "<string>", "created": "<ISO时间>"}
```

### 4.8 GET /api/v1/url/redirect/:shortKey（无认证）
1. shortKey 为空 → **400** `{"success": false, "error": "Short key is required"}`。
2. 缓存 `url:{shortKey}` 命中（且可反序列化）→ 异步（clicks+1、写 analytics）→ **302** `Location: originalUrl`。
3. 未命中 → 查库（`short_key` + 软删除过滤）→ 无 → **404** `{"success": false, "error": "URL not found"}`。
4. 异步回填缓存 24h + 异步（clicks+1、写 analytics）→ **302**。
> 302 用 `Found`；响应无 body。

### 4.9 GET /api/v1/url/（JWT）
1. 上下文取 userId（缺失 → 401 `Unauthorized: User ID missing`）。
2. 查 `user_id = userId` 全部（软删除过滤）。
3. **200** `{"success": true, "data": [UrlDetail...], "message": "URLs retrieved successfully"}`

### 4.10 POST /api/v1/url/shorten（JWT）
请求体：`{"original_url": string, "short_key"?: string, "title"?: string}`

1. 上下文取 userId（缺失 → 401 `Unauthorized: Id is missing from context`）。
2. 校验（→ 400 validation_error）：
   - original_url：必填、合法 URL；
   - short_key：可选，长度 2–50，匹配 `^[a-zA-Z0-9_-]+$`；
   - title：可选，≤255。
3. `original_url + user_id` 已存在 → **409** `{"success": false, "error": "This URL has already been shortened."}`。
4. short_key 为空 → gRPC `GetKey` → 失败 → **500** `{"success": false, "error": "Failed to generate short key"}`。
5. `short_key` 已存在（全部用户范围）→ **409** `{"success": false, "error": "The generated ShortKey already exists, please try again"}`。
6. 插入 → 异步写缓存 `url:{shortKey}` 24h（JSON 序列化 Url 全字段）。
7. **200** `{"success": true, "data": {"id": long, "original_url": string, "short_url": string, "title": string}, "message": "URL successfully created"}`
   - 注意：成功码是 200 不是 201；响应字段是 `short_url`（下划线）。

### 4.11 GET /api/v1/url/:shortKey（JWT）
1. shortKey 空 → 400 `Short key is required`。
2. 查库 → 无 → **404** `{"success": false, "error": "URL not found"}`。
3. **修复项 2**：url.userId ≠ 当前 userId → 404（同文案）。
4. **200** `{"success": true, "data": {UrlDetail}, "message": "URL details retrieved successfully"}`

UrlDetail：
```json
{"id": long, "original_url": string, "short_url": string, "title": string,
 "clicks": int, "created_at": "<ISO时间>"}
```

### 4.12 PATCH /api/v1/url/:shortKey（JWT）
请求体：`{"short_url"?: string, "title"?: string}` —— **注意是 `short_url`，不是 `short_key`**。

1. shortKey 空 → 400。
2. 查库 → 无 → 404。
3. 校验（→ 400 validation_error）：short_url 可选 2–50 且匹配 shortkeychars；title 可选 ≤255。
4. 新 short_url 非空且 ≠ 原值 → 查重，命中 → **409** `{"success": false, "error": "This short key already exists."}`。
5. 更新（仅非空字段）：short_key、title → 删缓存 `url:{旧shortKey}`（异步）。
6. **200** `{"success": true, "data": {"id","original_url","short_url","title","clicks","updated_at"}, "message": "URL updated successfully"}`
   - **修复项 4**：title/short_url 取 DB 现值回填（Go 版空值 bug 不再复刻）。

### 4.13 DELETE /api/v1/url/:shortKey（JWT）
1. shortKey 空 → 400。
2. 查库 → 无 → 404。
3. 软删除（置 deleted_at）→ 删缓存（异步）。
4. **200** `{"success": true, "message": "URL deleted successfully"}`（无 data）

### 4.14 GET /api/v1/analytics/:urlId（JWT）
`urlId` 为数字字符串（Url.id 转字符串）。

1. 上下文取 userId（缺失 → 401 `Unauthorized: Id is missing from context`）。
2. urlId 空 → **400** `{"success": false, "error": "Missing urlId in path"}`。
3. **修复项 1**：校验 urlId 对应的 url 存在且属于当前用户（url 不存在或不属于当前用户均 → **404** `{"success": false, "error": "No analytics found for this URL"}`）。Go 版的无效行 `Where("id = ?", idStr)` 不校验，仅依赖 analytics 查询结果——Java 版归属校验使范围语义更安全，文案保持一致。
4. 查 `url_id = urlId` 按 `clicked_at DESC` → 空 → **404** `{"success": false, "error": "No analytics found for this URL"}`。
5. **200** `{"success": true, "data": [AnalyticsItem...], "message": "Analytics retrieved successfully"}`

AnalyticsItem：
```json
{"ipAddress": string, "os": string, "device": string, "browser": string,
 "userAgent": string, "clickedAt": "yyyy-MM-dd HH:mm:ss", "referrer": string, "country": string}
```

## 5. 限流参数速查

| 端点 | 限额 |
|---|---|
| auth/signup | 5/分钟 |
| auth/signin、auth/logout | 10/分钟 |
| profile/ | 20/分钟 |
| profile/update | 5/分钟 |
| url/redirect/:shortKey | 50/分钟 |
| url/（列表） | 20/分钟 |
| url/shorten | 5/分钟 |
| url/:shortKey（详情） | 20/分钟 |
| url/:shortKey（PATCH/DELETE） | 5/分钟 |
| analytics/:urlId | 10/分钟 |

## 6. 校验错误字段名对照（validation_error 的 key）

| 请求体字段（JSON） | validation_error key（Go 字段名） |
|---|---|
| email | `Email` |
| username | `Username` |
| password | `Password` |
| original_url | `OriginalURL` |
| short_key（CreateUrl） | `ShortKey` |
| short_url（UpdateUrl） | `ShortKey`（Go struct 字段名仍是 `ShortKey`，仅 json tag 是 `short_url`） |
| title | `Title` |

> 即：Go 版 `validateStruct` 返回 `{Field(): "Invalid " + Field()}`，其中 Field 为 struct 字段名。Java 版自定义校验器需产出相同 key（详见 07 §4）。

## 7. 错误码汇总（全局）

| HTTP | body.error 文案（按端点不同） |
|---|---|
| 400 | `Invalid request format` / `Invalid input data` / `Short key is required` / `Missing urlId in path` / `Username cannot be empty` / `Invalid input: <msg>` |
| 401 | `Unauthorized: No token provided` / `Unauthorized: Invalid token` / `Password is not valid` / `Unauthorized: Id is missing from context` / `Unauthorized: User ID missing` / `Unauthorized: Email missing` |
| 404 | `User not found` / `URL not found` / `No analytics found for this URL` |
| 409 | `User already exists` / `This URL has already been shortened.` / `The generated ShortKey already exists, please try again` / `This short key already exists.` |
| 429 | `Too Many Requests` |
| 500 | `Failed to generate short key` / `Failed to create URL` / `Failed to retrieve URLs` / `Database error` / `Internal server error` / `Rate limiter internal error`（后者 body 是 `{error}`） |

> Go 版部分 500 分支（如 `Failed to create URL`）在真实场景中可能以其他文案出现，Java 版保持文案即可，不追求穷举。
