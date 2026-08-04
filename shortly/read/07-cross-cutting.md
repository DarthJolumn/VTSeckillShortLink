# 07 · 横切关注点实现规格（JWT / 限流 / 缓存 / 异步 / 异常 / 校验 / 日志）

> 本文是所有横切能力的 Java 实现规定：JWT 兼容、认证拦截器、Redis 固定窗口限流、缓存序列化、异步线程池、全局异常、自定义校验、日志。
> 与 Go 版对应组件逐一对齐，并落实修复项 4/5/7。

## 1. JWT（对齐 utils/jwt.go）

### JwtUtil
```java
@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationHours;   // 24
    private final String cookieName;      // "token"

    JwtUtil(@Value("${shortly.jwt.secret}") String secret,
            @Value("${shortly.jwt.expiration-hours:24}") long hours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = hours;
    }

    public String generate(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .claim("user_id", userId)                     // Go: claims["user_id"] (数字)
            .claim("email", email)
            .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
            .signWith(key, Jwts.SIG.HS256)                // 与 Go SigningMethodHS256 一致
            .compact();
    }

    /** 返回 LoggedIn{userId, email}；签名/结构/过期均抛 JwtException。 */
    public LoggedIn verify(String token) {
        Claims c = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
        Object uid = c.get("user_id");
        if (!(uid instanceof Number n) || c.get("email") == null) throw new JwtException("bad claims");
        return new LoggedIn(n.longValue(), c.get("email", String.class));
    }
}
```
- **与 Go 版互认的关键**：claims 名 `user_id`/`email`/`exp`、HS256、同一 `JWT_SECRET` 字符串。Go 侧 `user_id` 写入为 `uint`，JSON 序列化后是数字；jjwt 解析回 `Number`（Long）。Go 的 `exp` 为秒级 Unix 时间戳，jjwt 的 `expiration()` 默认对 `exp`（数值）解析为 Date（秒），兼容。
- 过期判定由 jjwt 自动完成（Go 版手写 exp 检查，行为等价）。
- `JWT_SECRET` 长度须 ≥32 字节（HS256 安全要求）；若 Go 版 secret 过短，jjwt `Keys.hmacShaKeyFor` 会抛错——实施时若原 secret <32B，改用 `new SecretKeySpec(bytes, "HmacSHA256")`（不强校验长度，保持与 Go 兼容）。

### CurrentUserHolder（替代 Gin context.Set）
```java
public final class CurrentUserHolder {
    private static final ThreadLocal<LoggedIn> HOLDER = new ThreadLocal<>();
    public static void set(LoggedIn u) { HOLDER.set(u); }
    public static LoggedIn get() { return HOLDER.get(); }
    public static Long userId() { LoggedIn u = HOLDER.get(); return u == null ? null : u.userId(); }
    public static String email() { LoggedIn u = HOLDER.get(); return u == null ? null : u.email(); }
    public static void clear() { HOLDER.remove(); }
}
```
> 注意异步线程不会继承 ThreadLocal，异步任务读取用户信息必须在提交前取出参数传入（05 §5 中 create/record 已按此设计）。

### JwtAuthInterceptor（对齐 auth.middleware.go）
```java
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) {
        String token = cookie(req, "token");
        if (token == null) {                       // Cookie 缺失 → 尝试 Bearer
            String hdr = req.getHeader("Authorization");
            if (hdr != null && hdr.startsWith("Bearer ")) token = hdr.substring(7);
        }
        if (token == null || token.isBlank())
            return abort(res, 401, "Unauthorized: No token provided");
        try {
            LoggedIn u = jwtUtil.verify(token);
            CurrentUserHolder.set(u);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return abort(res, 401, "Unauthorized: Invalid token");
        }
    }
    @Override public void afterCompletion(...) { CurrentUserHolder.clear(); }
}
```
- 注册（WebMvcConfig）时：health、url/redirect **不拦截**；其余全拦截。
- claims 缺失 user_id/email 的 401 文案（`Unauthorized: Invalid user ID` / `Unauthorized: Invalid email`）：与 Go 版仅在解析失败时分流；jjwt 缺失 claim 时 `get("user_id")` 为 null → 归入 `Unauthorized: Invalid token` 即可（差异可接受，但**推荐**按 02 §2 精确实现：null user_id → `Unauthorized: Invalid user ID`、null email → `Unauthorized: Invalid email`）。

### Cookie 工具（Signin/Logout，对齐 SetCookie）
```java
ResponseCookie makeTokenCookie(String value, long maxAgeSec) {
    return ResponseCookie.from(cookieName, value)
        .httpOnly(true).secure(true).path("/")
        .maxAge(maxAgeSec == -1 ? Duration.ZERO : Duration.ofSeconds(maxAgeSec))
        .sameSite("Lax")          // Go 未设置 SameSite；留 Lax 更安全。如需严格对齐去掉。
        .build();
}
```
- Signin：`maxAge=86400`；Logout：`maxAge=-1`（过期），值清空。
- `Secure=true` 在本地 HTTP 下浏览器不保存 cookie——与原 Go 版行为一致（`ctx.SetCookie("token",...,true,true)`），联调时可临时关掉（config 开关）。

## 2. 限流（对齐 middlewares/limiter.go + 修复项 5）

### 注解
```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int limit();           // 次数
    String window() default "MINUTE";   // MINUTE / HOUR（Go 格式 "N-M"/"N-H"）
}
```

### RedisFixedWindowLimiter（Lua，语义 = ulule 固定窗口）
```java
@Component
public class RedisFixedWindowLimiter {
    // key = rate_limit:{ip}; 固定窗口：INCR 后在窗口内首次设置 EXPIRE
    private static final String LUA = """
        local c = redis.call('INCR', KEYS[1])
        if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return c
        """;
    private final StringRedisTemplate rateRedis;   // 独立连接，DB2（修复项 5：单例）

    public boolean tryAcquire(String ip, int limit, long windowSeconds) {
        Long count = rateRedis.execute(
            new DefaultRedisScript<>(LUA, Long.class),
            List.of("rate_limit:" + ip),
            String.valueOf(windowSeconds));
        return count == null || count <= limit;     // count>limit → 拒绝
    }
}
```

### RateLimitInterceptor
```java
public class RateLimitInterceptor implements HandlerInterceptor {
    // 方法上有 @RateLimit 才触发
    public boolean preHandle(...) {
        RateLimit rl = 方法或类上的注解;
        if (rl == null) return true;
        long window = switch (rl.window()) { case "HOUR" -> 3600L; default -> 60L; };
        String ip = clientIp(req);
        if (limiter.tryAcquire(ip, rl.limit(), window)) return true;
        res.setStatus(429);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"Too Many Requests\"}");
        return false;
    }
}
```
- 限流 key = 客户端 IP（对齐 Go `c.ClientIP()`）；如需更细粒度可按 user 计数，但默认与 Go 一致用 IP。
- 内部 Redis 异常 → Go 版返回 500 `{"error":"Rate limiter internal error"}`；Java 捕获 Redis 异常时执行相同响应（不抛 500 给全局异常，或交由全局异常映射为 500）。
- 参数对照表见 02 §5，直接用注解标注到 Controller 方法。

## 3. 缓存（对齐 Redis DB1 各 key）

| 前缀 | 内容 | TTL | 写入点 | 删除点 |
|---|---|---|---|---|
| `url:{shortKey}` | Url JSON（全字段） | 24h | create / redirect 回填 | update/delete |
| `user:profile:{email}` | UserResponse JSON | 30min | getProfile / updateProfile | （无显式删除） |
| `ip-country:{ip}` | 国家名 | 24h | GeoIpService | — |

- 序列化：Jackson `ObjectMapper`（`writeValueAsString`/`readValue`），与 Go `encoding/json` 字段名一致（DTO 上加 `@JsonProperty("short_url")` 等，见 05 DTO）。
- `StringRedisTemplate` 单 bean 指向 DB1（`spring.data.redis.database:1`）。
- 写缓存均为**异步**（05 §5 的 AsyncCacheService），失败仅日志（对齐 Go goroutine 中 log.Error 后继续）。

## 4. 参数校验（对齐 validators.go + go-playground/validator 语义）

### 自定义注解
```java
@Constraint(validatedBy = ShortKeyCharsValidator.class)
@Target({FIELD, PARAMETER}) @Retention(RUNTIME)
public @interface ShortKeyChars {
    String message() default "Invalid ShortKey";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
public class ShortKeyCharsValidator implements ConstraintValidator<ShortKeyChars, String> {
    private static final Pattern P = Pattern.compile("^[a-zA-Z0-9_-]+$");
    public boolean isValid(String v, ConstraintValidatorContext c) { return v == null || P.matcher(v).matches(); }
}
```

### 校验错误产出（对齐 Go MapClaims 语义）
Go 版返回 `{"Email":"Invalid Email","Password":"Invalid Password",...}`（key=Go 字段名）。Java 实现：
```java
// 用 Hibernate Validator 拿到所有 MethodValidationViolation，按"字段 → 文案"组装
Map<String, String> errors = new LinkedHashMap<>();
for (FieldError fe : bindingResult.getFieldErrors())
    errors.put(goFieldName(fe.getField()), "Invalid " + goFieldName(fe.getField()));
```
- `goFieldName` 映射表：`originalUrl→OriginalURL`、`shortKey→ShortKey`、`shortUrl→ShortKey`（Go 字段名是 `ShortKey`，JSON 名是 `short_url`，错误 key 用字段名）、`username→Username`、`email→Email`、`password→Password`、`title→Title`。可直接用常量映射，或按 02 §6 硬编码。
- 或更简单：**在 DTO 上用 `@JsonProperty` 无法改动错误 key**，因此建议 service 层手动校验（05 §3 已示范：`validationErrors 非空 → throw ValidationException(errors)`），用静态映射表保证 key 精确。
- 校验规则汇总（02 §4）：
  - email：`@NotBlank @Email`
  - username：注册 `@Size(min=2,max=15)`；更新 profile `@Size(min=3,max=30)`
  - password：`@Size(min=6)`
  - original_url：`@NotBlank @URL`
  - short_key/short_url：`@Size(min=2,max=50) @ShortKeyChars`（可空）
  - title：`@Size(max=255)`（可空）
  - 更新 URL 的 title 可空但非空才更新（05 §5）。

## 5. 异步（对齐 goroutine + 修复项 7）

### AsyncConfig
```java
@Configuration @EnableAsync
public class AsyncConfig {
    // 业务线程池：URL 缓存回填 + 点击/analytics 采集
    @Bean(name = "shortlyAsyncExecutor")
    public Executor shortlyAsyncExecutor(@Value("${shortly.async.core-pool-size:8}") int core, ...) {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(core); e.setMaxPoolSize(max);
        e.setQueueCapacity(queue); e.setThreadNamePrefix("shortly-async-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 满时同步执行，防丢任务
        e.initialize();
        return e;
    }
}
```

### AsyncClickService（合并 clicks+1 与 analytics 写入）
```java
@Service
public class AsyncClickService {
    @Async("shortlyAsyncExecutor")
    public void record(Url url) {
        try {
            urlRepository.incrementClicks(url.getId());                    // UPDATE clicks=clicks+1
            String ip = /* 需由调用方传入：ThreadLocal 在异步线程不可用 */;
            ...
            analyticsRepository.save(toAnalytics(url, ip, ua, referrer));
        } catch (Exception e) { log.error("async click/analytics failed", e); }
    }
}
```
- **重要**：request 上下文（IP、UA、Referer）必须在**提交异步任务前**捕获传入（`record(url, ip, userAgent, referrer)`），不能读 ThreadLocal。
- 修复项 7：Go 版是 2 个 goroutine（分别更新 clicks、插 analytics）；Java 合并为 1 个异步任务内顺序执行，外部观察语义一致。

### AsyncCacheService
```java
@Async("shortlyAsyncExecutor")
public void cacheUrl(Url url) { redis.set("url:"+url.getShortKey(), toJson(url), Duration.ofHours(24)); }

@Async("shortlyAsyncExecutor")
public void evict(String cacheKey) { redis.delete(cacheKey); }
```

## 6. JSON 序列化（对齐 Go 响应）

- 全局 ObjectMapper 配置：字段名按 DTO 的 `@JsonProperty`（蛇形/驼峰按 02 §4 表）；`snake_case` 仅 DTO 显式标注，不全局改策略（避免影响内部字段）。
- **时间格式对齐（关键，直接影响 02 验收 diff）**：
  - Go `time.Time` 默认按 **RFC3339Nano** 序列化（如 `2025-01-01T20:00:00.123456+08:00`，含时区与纳秒，纳秒为 0 时省略小数点）；
  - Java `Instant` 默认 Jackson 序列化为 ISO-8601 UTC（如 `2025-01-01T12:00:00.000Z`），字符串与 Go 版**不相等**，验收 diff 会失败。
  - **Java 端统一处理**：
    ```java
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer timeFormatCustomizer() {
        return b -> {
            JavaTimeModule m = new JavaTimeModule();
            m.addSerializer(Instant.class, new InstantSerializer()
                .withZone(ZoneId.systemDefault())   // 用与 Go 服务器相同时区，或约定统一 UTC
                .withChronology(Chronology.ofLocale(Locale.ROOT)));
            b.modules(m);
            b.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
    ```
    或直接 `@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "Asia/Shanghai")`（与 Go 服务器本地时区对齐）。
  - **验收策略建议**：若两端服务器时区无法保证一致，验收时将时间字段解析为 `Instant` 比较**毫秒/秒级时间戳**而非字符串相等；或双跑过渡期两端强制同一时区输出。
  - analytics `clickedAt` 例外：必须为字符串 `"yyyy-MM-dd HH:mm:ss"`（05 §7），使用 `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` 显式格式化。
- gRPC/proto 与 JSON 无关。

## 7. 客户端 IP（对齐 c.ClientIP()）

```java
public static String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
    return req.getRemoteAddr();
}
```
- Go Gin `ClientIP()` 默认信任 `X-Forwarded-For`（除非 TrustedProxies 配置限制）；Java 用上述策略保持等价。限流与 analytics 均用同一取值。

## 8. 全局异常与统一响应

### ApiException
```java
public class ApiException extends RuntimeException {
    private final int status;              // HTTP 状态码
    private final String message;          // 响应 error 文案
    // 或带 validation_error map
}
```

### GlobalExceptionHandler（@RestControllerAdvice）
| 异常 | 响应 |
|---|---|
| `ApiException(status,msg)` | `status`，`{"success": false, "error": msg}` |
| `ValidationException(errors)` | 400，`{"success": false, "validation_error": {"字段":"Invalid 字段"}}` |
| `MethodArgumentNotValidException`（Bean Validation） | 400，同上（经映射表） |
| `HttpMessageNotReadableException`（JSON 解析失败） | 400，message 按端点区分：signup/signin/shorten → `Invalid request format`；update url → `Invalid input data`；update profile → `Invalid input: <异常信息>`（对齐 Go 各 handler 文案，02 §7） |
| 兜底 Exception | 500，`{"success": false, "error": "Internal server error"}` |

- 未捕获运行时异常→500 `Internal server error`（对齐 Go 部分分支文案；不打栈给客户端，服务端日志全量）。
- 404 路由未匹配：`/url/:shortKey` 等由 handler 返回 404；兜底 `NoHandlerFoundException` 可映射 JSON（可选）。

## 9. 日志（对齐 slog）

- SLF4J + Logback；建议输出 json 格式（对齐 Go slog JSON）：`{"time":..., "level":"INFO", "msg":"...", "k":"v"}`。
- 关键日志点（对齐 Go 版到处 log.Error/Info 的位置，实施时保留等价语义）：
  - 启动各阶段（env/DB/Redis/gRPC connected）；
  - CreateUrl 成功（`URL successfully created`，附 shortKey/userId）；
  - Redirect 缓存命中/未命中（`URL served from Redis cache`）；
  - 限流触发（`Rate limit exceeded`，附 ip/path）；
  - 异步采集失败（error）。

## 10. 配置项总表（API 服务，03 §6 基础上补充）

| 属性 | 默认 | 用途 |
|---|---|---|
| `shortly.jwt.cookie-name` | `token` | Cookie 名 |
| `shortly.jwt.cookie-secure` | `true` | 本地调试可设 false |
| `shortly.async.*` | 8/32/2000 | 线程池 |
| `shortly.rate-limit.redis-db` | 2 | 限流库（独立连接） |
| `shortly.cache.*` | 24h/30min/24h | 各 TTL |