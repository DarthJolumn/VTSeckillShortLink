# 06 · shortly-kgs-service 实现规格（gRPC + Key 生成）

> 本文规定 KGS 服务（Key Generation Service）的 Java 实现：gRPC 服务、key 生成算法、Redis 队列与 MongoDB 一致性逻辑、健康检查。
> 行为基准为 Go 版 `service/key_service.go` 与 `kgs/generator.go`（见 01 §3），并落实修复项 3（唯一索引 + 冲突重试）。

## 1. 职责与端口

| 端口 | 用途 | 配置项 |
|---|---|---|
| gRPC :50051（默认） | `KeyService.GetKey` | `shortly.kgs.grpc-port` |
| HTTP :8081（默认） | `GET /api/v1/health` | `server.port`（Spring Boot） |

- gRPC Server 独立于 Tomcat：用 `io.grpc.ServerBuilder` 在 gRPC 端口启动，Spring Boot 仅承载健康检查 HTTP。
- 健康检查响应（对齐 Go 硬编码输出）：
```json
{"success": true, "message": "KGS server is up and running"}
```

## 2. 启动装配（ShortlyKgsApplication）

1. `@SpringBootApplication`；
2. 启动期预检：MongoDB ping（20s 超时）、Redis DB0 ping（失败即退出，对齐 Go `os.Exit(1)`）；
3. 注册 `KeyServiceGrpc` 服务端并 `start()`；`@PreDestroy` 时 `shutdown()`；
4. 为 MongoDB `shortkeys.key` 创建唯一索引（程序启动时 `ensureIndex`，等价手动执行，见 04 §2.2；用 `@Indexed(unique=true)` + `MongoTemplate.indexOps().ensureIndex()`）。

## 3. 常量（对齐 Go constants.go）

```java
public final class Constants {
    public static final String QUEUE_NAME = "shortly-kgs-redis-queue";
    public static final String COUNTER = "shortly-kgs-queue-counter"; // Go 版定义未使用，保留
    public static final long QUEUE_THRESHOLD = 200;                   // QueueLength
    public static final int BATCH_SIZE = 1000;                        // KeyCount
    public static final int KEY_LENGTH = 6;
    public static final int MAX_GENERATE_ATTEMPTS = 3;                // Base62 冲突重试上限
    public static final String STATUS_AVAILABLE = "available";
    public static final String STATUS_USED = "used";
}
```

## 4. Base62 生成器（对齐 utils/base62.go + 修复项 3）

```java
public final class Base62 {
    public static final String CHARSET =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 生成 [0, 62^len) 随机数并按 Base62 编码，不足 len 位前补 '0'。 */
    public static String randomKey(int length) {
        BigInteger max = BigInteger.valueOf(62).pow(length);
        BigInteger n = new BigInteger(max.bitLength(), RANDOM);  // [0, 2^bitLength)
        n = n.mod(max);                                          // [0, max)
        StringBuilder sb = new StringBuilder();
        while (n.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] div = n.divideAndRemainder(BigInteger.valueOf(62));
            sb.append(CHARSET.charAt(div[1].intValue()));
            n = div[0];
        }
        while (sb.length() < length) sb.append(CHARSET.charAt(0));  // 前补 '0'
        return sb.reverse().toString();
    }
}
```
- **有意偏离 Go 实现的说明**：Go 版 `rand.Int(rand.Reader, big.NewInt(62))` 只生成 `[0,62)` 单值，实际仅产出 63 种 key（见 01 §3.4），属严重缺陷。Java 版按**正确语义**实现 62^6 全空间，配合 Mongo 唯一索引去重（修复项 3）。
- `new BigInteger(bitLength, rnd)` 生成 `[0, 2^bitLength)` 均匀分布，`bitLength` 取 `max.bitLength()`（62^6 ≈ 2^35.7 → bitLength=36）后 `mod max` 的截断偏差 < 2^-35，可忽略。
- 结果：6 位，字符集 `0-9a-zA-Z`，首字符可为 `0`（与 Go 补零行为一致）。

## 5. KeyGenerator（对齐 kgs/generator.go + 冲突重试）

```java
@Component
public class KeyGenerator {
    private final MongoTemplate mongo;
    private final StringRedisTemplate redis;   // DB0

    /** 生成 count 个 key：Mongo InsertMany 成功后 LPUSH 入队。 */
    public void generateBatch(int count) {
        List<String> keys = new ArrayList<>(count);
        List<ShortKey> docs = new ArrayList<>(count);
        // 重试窗口：单文档冲突 → 整体重试；最多 MAX_GENERATE_ATTEMPTS 次（修复项 3）
        for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
            keys.clear(); docs.clear();
            for (int i = 0; i < count; i++) {
                String key = Base62.randomKey(KEY_LENGTH);
                keys.add(key);
                docs.add(ShortKey.of(key, STATUS_AVAILABLE, Instant.now()));
            }
            try {
                mongo.insert(docs, "shortkeys");       // 唯一索引冲突抛 DuplicateKeyException
                break;
            } catch (DuplicateKeyException e) {
                log.warn("Duplicate keys in batch, retrying (attempt {})", attempt + 1);
                if (attempt == MAX_ATTEMPTS - 1) throw new KgsException("duplicate key generation", e);
            }
        }
        redis.opsForList().leftPushAll(QUEUE_NAME, keys.toArray(new String[0]));  // 对齐 LPUSH 批量
    }
}
```

> 对齐细节：
> - Go 版顺序是「InsertMany 成功 → LPUSH」；任一步失败整体返回错误，已插入的 Mongo 文档不回收（Java 版同理，Mongo 中有少量 available 冗余可接受）。
> - LPUSH 批量顺序：`LPUSH key v1 v2 ... vN` 后队列头部是 vN；RPOP 取出的是最后 push 的元素。两端实现相同即行为一致，Java `leftPushAll(collection, values...)` 与 Go `LPush(ctx, name, redisKeys...)` 参数顺序一致。
> - `MAX_ATTEMPTS` 建议 3（修复项 3；Go 版无重试，直接失败）。

## 6. KeyService（对齐 service/key_service.go GetKey）

```java
@Service
public class KeyService {
    private final StringRedisTemplate redis;          // DB0
    private final MongoTemplate mongo;
    private final KeyGenerator generator;

    public String getKey() {
        Long len = redis.opsForList().size(QUEUE_NAME);
        if (len == null) throw new KgsException("redis unavailable");
        if (len < QUEUE_THRESHOLD) generator.generateBatch(BATCH_SIZE);   // 补货

        String key = redis.opsForList().rightPop(QUEUE_NAME);             // RPOP
        if (key == null) throw new KgsException("queue empty");           // Go: RPOP err

        UpdateResult res = mongo.updateFirst(
            Query.query(Criteria.where("key").is(key)),
            Update.update("status", STATUS_USED),
            "shortkeys");
        if (res.getModifiedCount() == 0) {                                // 对齐 ModifiedCount==0
            redis.opsForList().leftPush(QUEUE_NAME, key);                 // 回滚放回队首
            throw new KgsException("failed to update key status in DB, pushed key " + key + " back");
        }
        return key;
    }
}
```

- 并发注意：`LLEN → 补货 → RPOP` 非原子（Go 版同样非原子），多实例 KGS 会重复补货/多取 key，但 MongoDB 唯一索引保证 key 全局唯一，`used` 标记幂等可重入（重复标记只是 modifiedCount=0 回滚，API 侧查到重复 short_key 时 409 重试）。Java 版可加 `synchronized` 或分布式锁（可选增强，默认不引入额外依赖）。

## 7. gRPC 服务端（KeyGrpcService）

```java
@Component
public class KeyGrpcService extends KeyServiceGrpc.KeyServiceImplBase {
    private final KeyService keyService;

    @Override
    public void getKey(KeyOuterClass.Empty request,
                       StreamObserver<KeyOuterClass.KeyResponse> responseObserver) {
        try {
            String key = keyService.getKey();
            responseObserver.onNext(KeyOuterClass.KeyResponse.newBuilder().setKey(key).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetKey failed", e);
            responseObserver.onError(Status.INTERNAL.withDescription("key generation failed").asRuntimeException());
        }
    }
}
```

## 8. gRPC Server 装配（GrpcServerConfig）

```java
@Configuration
public class GrpcServerConfig {
    @Bean(destroyMethod = "shutdown")
    public Server grpcServer(KeyGrpcService keyGrpcService,
                             @Value("${shortly.kgs.grpc-port:50051}") int port) {
        return ServerBuilder.forPort(port)
            .addService(keyGrpcService)
            .build();
    }
}
```
- 启动：`ApplicationRunner` 中调用 `server.start()`，并 `log.info("KGS gRPC server running on {}", port)`；`@PreDestroy` 前 `server.awaitTermination()` 由 destroyMethod 处理。
- 与 Go 版等价：Go 是 `grpcServer.Serve(listener)` 阻塞主线程；Java 中 Tomcat 线程为主，gRPC server 作为后台守护即可。

## 9. 健康检查（对齐 Go :8081）

- `HealthController`（web 模块）：`GET /api/v1/health` → 200 固定 JSON（§1）。
- 响应体 `{"success": true, "message": "KGS server is up and running"}`。

## 10. 配置清单（application.yml 见 03 §6）

| 项 | 默认 | 说明 |
|---|---|---|
| `spring.data.mongodb.uri/database` | `mongodb://localhost:27017` / `shortly` | 对齐 `MONGO_URI/MONGO_DB_NAME` |
| `spring.data.redis.database` | 0 | KGS 队列库 |
| `shortly.kgs.grpc-port` | 50051 | gRPC 端口（Go 版用 `PORT` env） |
| `server.port` | 8081 | 健康检查 HTTP |

## 11. 测试要点

- 单元：Base62（长度=6、字符集、首字符可为 0）、KeyGenerator 冲突重试（mock DuplicateKeyException）。
- 集成（Testcontainers mongo+redis）：GetKey 在队列空时触发补货；队列低于 200 时补货；Mongo 标记 used；modifiedCount=0 回滚后 key 重新入队。
