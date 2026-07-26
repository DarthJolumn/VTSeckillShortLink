# 公共模块 (common)

> 不可执行 jar，被所有业务模块依赖

## 内容

```
├── api/                  # Dubbo 接口定义
│   ├── LeaderboardService.java   # 排行榜
│   ├── UserDubboApi.java         # 用户（踢下线）
│   └── WsPushService.java        # WS 推送
├── dto/                  # 通用 DTO
│   ├── Result.java               # 统一返回 {code,message,data,timestamp}
│   └── RankEntry.java            # 排行条目
├── util/                 # 工具类
│   ├── JwtUtil.java              # JWT 签发/验签（HS384/HMAC-SHA384）
│   ├── SnowflakeIdGenerator.java # 分布式 ID（ReentrantLock 保护）
│   └── UserContext.java          # ScopedValue 上下文
├── exception/            # 异常体系
│   ├── BizException.java         # 业务异常(code/msg)
│   ├── GlobalExceptionHandler.java  # MVC 异常处理
│   └── GlobalErrorWebExceptionHandler.java  # WebFlux 异常处理
├── constant/             # 常量
└── grpc/                 # Protobuf + gRPC 定义（seckill-single 推送用）
```

## 关键组件

### JwtUtil

- 算法：HMAC-SHA384（HS384）
- Secret：Base64 解码，配置在 `jwt.secret`
- Claims：`sub`=userId, `role`=用户角色, `iat`/`exp`
- JJWT（0.12.6）+ **Gson**（非 Jackson，避免 Jackson 2/3 冲突）

### SnowflakeIdGenerator

- 机器位：配置文件 `worker.id`
- 场景：秒杀订单号 `orderNo`
- 并发保护：`ReentrantLock`（JDK 25 JEP 491 已修复 synchronized pinning）

### UserContext

```java
public static final ScopedValue<Long> USER_ID = ScopedValue.newInstance();
public static final ScopedValue<Integer> ROLE = ScopedValue.newInstance();
public static final ScopedValue<String> DEVICE_ID = ScopedValue.newInstance();
```

- 替代 `ThreadLocal`，VT 兼容
- 由 `AuthInterceptor` 在请求入口处 `ScopedValue.where(...).run()` 绑定
- Gateway 的 WebFlux 模式通过 `exchange.getAttributes()` 传递

### Result

```json
{"code":200,"message":"ok","data":null,"timestamp":1721800000000}
```

所有控制器统一返回 `Result<T>`，异常由全局处理器统一包装。
