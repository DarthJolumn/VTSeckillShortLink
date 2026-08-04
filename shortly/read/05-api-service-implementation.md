# 05 · shortly-api-service 实现规格

> 本文规定 API 服务的**类级实现规格**：每个 Controller/Service 的方法签名、行为步骤、与 Go 版 handler 的对应关系。
> 契约细节（状态码/文案/字段名）一律以 02 为准，冲突时以 02 为准；横切实现（JWT/限流/异步/异常/校验）见 07。

## 1. 主入口与装配

`ShortlyApiApplication.java`：
- `@SpringBootApplication` + `@EnableAsync`（线程池定义见 07 §5）。
- 启动后必须完成与 Go 版 main.go 等价的**连接预检**：DataSource ping（`JdbcTemplate` 或 `PersistenceExceptionTranslation` 兜底）、`StringRedisTemplate` ping、gRPC 客户端建连（惰性）。任一失败 → 启动失败（对齐 Go 版 `os.Exit(1)` 语义，用 `ApplicationContext` 关闭即可）。
- CORS 用 `CorsConfig`（属性见 03 §6），等价 Go 版 gin-contrib/cors 配置。

## 2. 包结构与职责划分

```
controller  → 只做：参数绑定、调用 service、返回 ApiResponse / 抛 ApiException
service     → 业务逻辑 + 事务 + 缓存 + 异步触发（对应 Go handler 主体）
repository  → 数据访问（04 §4）
```

## 3. Auth 模块

### AuthController
```
POST /api/v1/auth/signup   → AuthService.signup(SignupRequest) → 201 ApiResponse<UserResponse>
POST /api/v1/auth/signin   → AuthService.signin(SigninRequest) → 200 {success, token, data}
POST /api/v1/auth/logout   → 清 cookie → 200 {success, message}
```
- Signin 的 token 与 cookie 由 Controller 组装：`JwtUtil` 签发 → `ResponseCookie`（name `token`，maxAge 86400s，path `/`，HttpOnly，Secure）→ `Set-Cookie`；响应体 `token` 字段同时返回。
- Logout：`ResponseCookie` maxAge 0（或 -1）同名清除，返回固定 JSON。

### AuthService
```java
public UserResponse signup(SignupRequest req) {
    String email = req.email().trim().toLowerCase(Locale.ROOT);   // 对齐 Go：TrimSpace + ToLower
    String username = req.username().trim();
    if (validationErrors 非空) throw new ValidationException(errors);      // 400 validation_error
    if (userRepository.existsByEmail(email)) throw new ApiException(409, "User already exists");
    String hash = passwordEncoder.encode(req.password());                   // bcrypt cost 10
    User user = new User(email, username, hash);
    userRepository.save(user);
    return UserResponse.of(user);                                          // {id,email,username,created}
}
public SigninResult signin(SigninRequest req) {
    String email = req.email().trim().toLowerCase(Locale.ROOT);
    if (validationErrors 非空) throw new ValidationException(errors);
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new ApiException(404, "User not found"));
    if (!passwordEncoder.matches(req.password(), user.getPassword()))
        throw new ApiException(401, "Password is not valid");
    String token = jwtUtil.generate(user.getId(), user.getEmail());
    return new SigninResult(token, UserResponse.of(user));
}
```
- 校验字段名：`Email`、`Password`、`Username`（见 02 §6、07 §4）。
- 错误文案严格按 02 §4.2/4.3。

## 4. Profile 模块

### ProfileController
```
GET   /api/v1/profile/       → ProfileService.getProfile()    → 200 ApiResponse<UserResponse>
PATCH /api/v1/profile/update → ProfileService.updateProfile(UpdateProfileRequest)
```
- userId/email 从 `CurrentUserHolder` 取（07 §2）。

### ProfileService
```java
private static final String CACHE_KEY_PREFIX = "user:profile:";

public UserResponse getProfile(String email) {
    String cacheKey = CACHE_KEY_PREFIX + email;
    String cached = redisTemplate.opsForValue().get(cacheKey);              // DB1
    if (cached != null) return objectMapper.readValue(cached, UserResponse.class);
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new ApiException(404, "User not found"));
    UserResponse resp = UserResponse.of(user);
    redisTemplate.opsForValue().set(cacheKey, toJson(resp),
        Duration.ofMinutes(profileTtlMinutes));                             // 30min
    return resp;
}

public UserResponse updateProfile(String email, String username) {
    if (username.trim().isEmpty()) throw new ApiException(400, "Username cannot be empty");
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new ApiException(404, "User not found"));
    user.setUsername(username.trim());
    userRepository.save(user);                                             // GORM Save = 全字段更新
    UserResponse resp = UserResponse.of(user);
    redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + email, toJson(resp),
        Duration.ofMinutes(profileTtlMinutes));
    return resp;
}
```
- 缓存序列化用 Jackson（配置见 07 §6），内容为 UserResponse JSON（Go 版缓存的就是 UserDTO）。

## 5. Url 模块（核心）

### UrlController
```
GET    /api/v1/url/redirect/:shortKey   → UrlService.redirect(shortKey, request) → ResponseEntity 302
GET    /api/v1/url/                     → UrlService.listMine(userId)
POST   /api/v1/url/shorten              → UrlService.create(userId, CreateUrlRequest)
GET    /api/v1/url/:shortKey            → UrlService.details(userId, shortKey)
PATCH  /api/v1/url/:shortKey            → UrlService.update(userId, shortKey, UpdateUrlRequest)
DELETE /api/v1/url/:shortKey            → UrlService.delete(userId, shortKey)
```
- redirect 端点返回 `ResponseEntity.status(302).location(URI.create(url)).build()`（无 body）。
- 路径参数校验：shortKey 空 → 400（但 Spring 路由不会匹配空串，Go 版判空分支保留为防御）。

### UrlService

```java
// create：对齐 Go CreateUrl（02 §4.10）
public CreateUrlResult create(Long userId, CreateUrlRequest req) {
    String userIdStr = String.valueOf(userId);                       // Go: id 转字符串存 user_id
    if (urlRepository.existsByOriginalUrlAndUserId(req.originalUrl(), userIdStr))
        throw new ApiException(409, "This URL has already been shortened.");
    String shortKey = req.shortKey();
    if (shortKey == null || shortKey.isBlank()) {
        shortKey = keyServiceClient.getKey();                        // gRPC GetKey，超时/异常 → 500
    }
    if (urlRepository.existsByShortKey(shortKey))
        throw new ApiException(409, "The generated ShortKey already exists, please try again");
    Url url = new Url(req.originalUrl(), shortKey, req.title(), userIdStr);
    urlRepository.save(url);
    asyncCacheService.cacheUrl(url);                                 // 异步写缓存 24h（07 §5）
    return CreateUrlResult.of(url);
}

// redirect：对齐 Go RedirectToOriginalUrl（02 §4.8）
public String redirect(String shortKey) {
    String cacheKey = "url:" + shortKey;
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        Url url = parseFromCache(cached);                            // 解析失败按未命中处理
        if (url != null) { asyncClickService.record(url); return url.getOriginalUrl(); }
    }
    Url url = urlRepository.findByShortKey(shortKey)
        .orElseThrow(() -> new ApiException(404, "URL not found"));
    asyncCacheService.cacheUrl(url);                                 // 回填 24h
    asyncClickService.record(url);                                   // 点击数+1 + analytics 落库
    return url.getOriginalUrl();
}

// details：修复项 2 —— 归属校验
public UrlDetailResponse details(Long userId, String shortKey) {
    Url url = urlRepository.findByShortKey(shortKey)
        .orElseThrow(() -> new ApiException(404, "URL not found"));
    if (!String.valueOf(userId).equals(url.getUserId()))
        throw new ApiException(404, "URL not found");               // 归属校验，非本人等同不存在
    return UrlDetailResponse.of(url);
}

// list：对齐 GetAllUrls
public List<UrlDetailResponse> listMine(Long userId) {
    return urlRepository.findByUserId(String.valueOf(userId))
        .stream().map(UrlDetailResponse::of).toList();
}

// update：对齐 UpdateUrl + 修复项 4
public UrlUpdateResponse update(Long userId, String shortKey, UpdateUrlRequest req) {
    Url url = urlRepository.findByShortKey(shortKey)
        .orElseThrow(() -> new ApiException(404, "URL not found"));
    if (!String.valueOf(userId).equals(url.getUserId()))
        throw new ApiException(404, "URL not found");
    boolean keyChanged = false;
    if (req.shortUrl() != null && !req.shortUrl().isBlank() && !req.shortUrl().equals(shortKey)) {
        if (urlRepository.existsByShortKey(req.shortUrl()))
            throw new ApiException(409, "This short key already exists.");
        url.setShortKey(req.shortUrl()); keyChanged = true;
    }
    if (req.title() != null && !req.title().isBlank()) url.setTitle(req.title());
    urlRepository.save(url);                                        // 更新 updated_at
    final String oldKey = shortKey;
    if (keyChanged) asyncCacheService.evict("url:" + oldKey); else asyncCacheService.evict("url:" + shortKey);
    return UrlUpdateResponse.of(url);                               // 回填 DB 现值（修复项 4）
}

// delete：对齐 DeleteUrl
public void delete(Long userId, String shortKey) {
    Url url = urlRepository.findByShortKey(shortKey)
        .orElseThrow(() -> new ApiException(404, "URL not found"));
    if (!String.valueOf(userId).equals(url.getUserId()))
        throw new ApiException(404, "URL not found");               // 补充：Go 版也未校验，属安全增强
    urlRepository.delete(url);                                      // 软删除 @SQLDelete
    asyncCacheService.evict("url:" + shortKey);
}
```

> 归属校验说明：Go 版 update/delete **没有**归属校验（只有 Go 版的缺陷 1/2 涉及 analytics 与详情）。Java 版为一致性和安全，update/delete 同样加校验；若需严格 1:1 复刻行为可移除，但**推荐保留**（文档默认保留）。

### UrlService 依赖（构造注入）
- `UrlRepository`、`KeyServiceClient`、`StringRedisTemplate`（DB1）、`AsyncClickService`、`AsyncCacheService`、`ObjectMapper`。
- 事务：create 可加 `@Transactional`；update/delete 加 `@Transactional`（软删+缓存清除顺序：先 DB 后缓存，缓存失败仅日志）。

## 6. gRPC 客户端：KeyServiceClient

```java
@Component
public class KeyServiceClient {
    private final KeyServiceGrpc.KeyServiceBlockingStub stub;
    private final Duration timeout;                 // 建议 2s，超时/异常 → ApiException 500

    KeyServiceClient(GrpcClientConfig cfg) {
        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
            .usePlaintext()                                        // Go 版 insecure
            .keepAliveTime(30, SECONDS)
            .build();
        this.stub = KeyServiceGrpc.newBlockingStub(channel);
    }

    public String getKey() {
        try {
            KeyOuterClass.KeyResponse resp =
                stub.withDeadlineAfter(timeout.toMillis(), MILLISECONDS)
                    .getKey(KeyOuterClass.Empty.getDefaultInstance());
            return resp.getKey();
        } catch (StatusRuntimeException e) {
            log.error("KGS GetKey failed", e);
            throw new ApiException(500, "Failed to generate short key");
        }
    }
}
```
- 单例（Spring bean 生命周期管理 channel，`@PreDestroy` shutdown）。
- 端点语义对齐 Go：KGS 内部错误 → API 返回 500 `Failed to generate short key`。

## 7. Analytics 模块

### AnalyticsController
```
GET /api/v1/analytics/:urlId → AnalyticsService.getAnalytics(userId, urlId)
```

### AnalyticsService
```java
public List<AnalyticsItem> getAnalytics(Long userId, String urlId) {
    if (urlId == null || urlId.isBlank())
        throw new ApiException(400, "Missing urlId in path");
    // 修复项 1：归属校验
    Long idLong;
    try {
        idLong = Long.valueOf(urlId);
    } catch (NumberFormatException e) {
        throw new ApiException(404, "No analytics found for this URL");  // 非数字 urlId 等同不存在
    }
    Url url = urlRepository.findById(idLong)
        .filter(u -> String.valueOf(userId).equals(u.getUserId()))
        .orElseThrow(() -> new ApiException(404, "No analytics found for this URL"));
    List<Analytics> list = analyticsRepository.findByUrlIdOrderByClickedAtDesc(urlId);
    if (list.isEmpty()) throw new ApiException(404, "No analytics found for this URL");
    return list.stream().map(AnalyticsItem::of).toList();
}
```
- `AnalyticsItem` 序列化：`clickedAt` 必须为 `"yyyy-MM-dd HH:mm:ss"`（用 `DateTimeFormatter` 格式化 Instant → 本地时区或 UTC？Go 版 `ClickedAt.Format("2006-01-02 15:04:05")` 用 **服务器本地时区**。Java 用系统默认时区格式化 `Instant`，保持等价；建议统一 `ZoneId.systemDefault()` 并在测试中验证）。

### 异步采集（对应 Go storeAnalytics + incrementClickCount）
见 07 §5。要点：
- 由 `AsyncClickService.record(url)` 一次性完成两件事：`urlRepository.incrementClicks(id)` + 写 Analytics（含 GeoIp + UA 解析），减少 DB 往返（修复项 7 合并，行为等价：Go 是两个 goroutine，各自独立；Java 合并为单线程内顺序执行，语义一致）。
- Analytics 字段：`urlId = String.valueOf(url.getId())`、`clickedAt = Instant.now()`、`ipAddress`（客户端 IP，见 07 §7）、`userAgent`、`referrer`（`Referer` 头）、`country`（GeoIpService，失败 "Unknown"，本机 "Localhost"）、`device/browser/os`（UserAgentParser）。
- 采集失败仅日志，不得影响 302 响应。

## 8. GeoIpService 与 UserAgentParser

### GeoIpService（对齐 lib/analytics.go GetCountryFromIP）
```java
public String countryOf(String ip) {
    String cacheKey = "ip-country:" + ip;
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return cached;
    if ("127.0.0.1".equals(ip) || "::1".equals(ip)) return "Localhost";
    try {
        String json = restClient.get()
            .uri(baseUrl + "/{ip}/json/", ip).retrieve().body(String.class);
        String country = parseCountryName(json);
        if (country != null && !country.isBlank()) {
            redisTemplate.opsForValue().set(cacheKey, country, Duration.ofHours(24));
            return country;
        }
    } catch (Exception e) { log.warn("ipapi.co failed for {}", ip, e); }
    return "Unknown";
}
```
- HTTP 客户端：`RestClient` 或 `RestTemplate`，超时 2s；解析字段 `country_name`。

### UserAgentParser（对齐 lib/analytics.go ParseUserAgent）
```java
public UaInfo parse(String ua) {
    if (ua == null || ua.isBlank()) return new UaInfo("Desktop", "", "");
    Client client = Client.parse(ua);                    // ua_parser.web
    // 对齐 Go mssola 三态：Mobile()→"Mobile"，Bot()→"Bot"，其余"Desktop"
    String device = client.isMobile() ? "Mobile"
                  : client.isSpider() ? "Bot"
                  : "Desktop";
    // browser = "<family> <major>"（Go: name + " " + version）
    String browser = (client.userAgent.family == null ? "" : client.userAgent.family)
                   + (client.userAgent.major == null ? "" : " " + client.userAgent.major);
    String os = client.os.family == null ? "" : client.os.family;
    return new UaInfo(device, browser.trim(), os);
}
```
> 说明：`ua_parser.web` 的 `Client` 提供 `isMobile()`/`isSpider()`（基于内置 regexes），语义对应 mssola 的 `ua.Mobile()`/`ua.Bot()`；不要用 `device.family` 字符串判断（uap 对移动设备可能返回具体型号，三态归一化是必须的）。浏览器输出格式 `<family> <major>`（如 `Chrome 120`）；OS 取 `client.os.family`（与 Go `ua.OS()` 均为操作系统名，可接受细微差异）。

## 9. 依赖注入与配置类清单（API 模块）

| Bean | 说明 |
|---|---|
| `JwtUtil` | 07 §2 |
| `JwtAuthInterceptor` | 07 §2 |
| `RateLimitInterceptor` + `@RateLimit` | 07 §3 |
| `AsyncConfig` 线程池 | 07 §5 |
| `RedisConfig`（DB1 `StringRedisTemplate`） | 03 §6 |
| `RateLimitRedisConfig`（DB2 独立连接，单例） | 07 §3（修复项 5） |
| `GrpcClientConfig` | 本文件 §6 |
| `GeoIpService` | 本文件 §8 |
| `UserAgentParser` | 本文件 §8 |
| `GlobalExceptionHandler` | 07 §8 |
| `ValidationErrorBuilder` | 07 §4 |
