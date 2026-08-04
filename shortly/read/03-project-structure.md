# 03 · 目标工程结构（Maven 多模块）与构建配置

> 本文定义 Java 版的**目录树、Maven 配置、application.yml 模板、docker-compose 与 Makefile**。实施 agent 按此搭建 P0 骨架。
> 建议新工程位于仓库根下 `java/` 目录（与原 Go 代码并存），或独立新仓库——实施时以实际约定为准，本文按 `java/` 目录给出。

## 1. 目录树（完整目标形态）

```
java/
├── pom.xml                                    # parent POM（Spring Boot 3.3.x）
├── Makefile                                   # build / run / migrate 便捷命令（可选）
├── docker-compose.yaml                        # postgres + redis + mongo
├── shortly-proto/                             # 共享模块：key.proto + 生成的 Java gRPC stub
│   ├── pom.xml
│   └── src/main/proto/key.proto               # 从 ../../proto/key.proto 复制（内容一字不改）
├── shortly-api-service/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/shortly/api/
│       │   ├── ShortlyApiApplication.java
│       │   ├── config/
│       │   │   ├── CorsConfig.java
│       │   │   ├── RedisConfig.java           # StringRedisTemplate 限定 DB1 + Jackson 序列化
│       │   │   ├── RateLimitRedisConfig.java  # DB2 连接（单例）
│       │   │   ├── AsyncConfig.java           # @EnableAsync + 业务线程池
│       │   │   ├── GrpcClientConfig.java      # KeyServiceStub 单例
│       │   │   └── WebMvcConfig.java          # 注册拦截器（Auth/RateLimit）顺序
│       │   ├── controller/
│       │   │   ├── HealthController.java
│       │   │   ├── AuthController.java
│       │   │   ├── ProfileController.java
│       │   │   ├── UrlController.java
│       │   │   └── AnalyticsController.java
│       │   ├── service/
│       │   │   ├── AuthService.java
│       │   │   ├── ProfileService.java
│       │   │   ├── UrlService.java
│       │   │   ├── AnalyticsService.java
│       │   │   ├── KeyServiceClient.java     # 封装 gRPC GetKey
│       │   │   ├── GeoIpService.java         # ipapi.co + 缓存
│       │   │   └── UserAgentParser.java      # ua-parser-java 封装
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   ├── UrlRepository.java
│       │   │   └── AnalyticsRepository.java
│       │   ├── entity/
│       │   │   ├── User.java
│       │   │   ├── Url.java
│       │   │   └── Analytics.java
│       │   ├── dto/
│       │   │   ├── request/  SignupRequest, SigninRequest, CreateUrlRequest,
│       │   │   │             UpdateUrlRequest, UpdateProfileRequest
│       │   │   ├── response/ ApiResponse<T>, UserResponse, UrlResponse,
│       │   │   │             UrlDetailResponse, UrlUpdateResponse, AnalyticsItem,
│       │   │   │             ValidationErrorResponse
│       │   ├── security/
│       │   │   ├── JwtUtil.java
│       │   │   ├── JwtAuthInterceptor.java
│       │   │   └── CurrentUser.java / CurrentUserHolder.java   # ThreadLocal
│       │   ├── ratelimit/
│       │   │   ├── RateLimit.java            # 注解：limit + window
│       │   │   ├── RateLimitInterceptor.java
│       │   │   └── RedisFixedWindowLimiter.java
│       │   ├── validation/
│       │   │   ├── ShortKeyChars.java        # 自定义校验注解
│       │   │   ├── ShortKeyCharsValidator.java
│       │   │   └── ValidationErrorBuilder.java  # 产出 {Go字段名: "Invalid Go字段名"}
│       │   └── exception/
│       │       ├── ApiException.java         # code + message + httpStatus
│       │       ├── GlobalExceptionHandler.java
│       │       └── ErrorCode.java
│       └── main/resources/
│           ├── application.yml
│           ├── application-local.yml（可选）
│           └── db/migration/V1__init.sql     # 见 04
│       └── test/java/com/shortly/api/        # JUnit5 + Testcontainers
└── shortly-kgs-service/
    ├── pom.xml
    └── src/
        ├── main/java/com/shortly/kgs/
        │   ├── ShortlyKgsApplication.java
        │   ├── config/
        │   │   ├── MongoConfig.java          # MongoClient 单例
        │   │   ├── RedisConfig.java          # StringRedisTemplate 限定 DB0
        │   │   └── GrpcServerConfig.java     # gRPC Server 启动（非 web 端口）
        │   ├── grpc/KeyGrpcService.java      # extends KeyServiceGrpc.KeyServiceImplBase
        │   ├── service/KeyService.java       # GetKey 业务（队列+补货+标记）
        │   ├── service/KeyGenerator.java     # 批量生成 + 落库/入队
        │   ├── repository/ShortKeyRepository.java
        │   ├── entity/ShortKey.java          # Mongo 文档
        │   ├── constant/Constants.java       # QUEUE_NAME/QUEUE_THRESHOLD/BATCH_SIZE
        │   └── util/Base62.java              # SecureRandom Base62
        └── src/main/resources/
            ├── application.yml
            └── (可选) logback-spring.xml
```

## 2. Parent POM 要点

```xml
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.shortly</groupId>
  <artifactId>shortly-parent</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>

  <modules>
    <module>shortly-proto</module>
    <module>shortly-api-service</module>
    <module>shortly-kgs-service</module>
  </modules>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <grpc.version>1.68.1</grpc.version>
    <protobuf.version>4.28.3</protobuf.version>     <!-- grpc-java 兼容的 protobuf-java -->
    <jjwt.version>0.12.6</jjwt.version>
    <ua-parser.version>1.4.4</ua-parser.version>
    <testcontainers.version>1.20.4</testcontainers.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- grpc-java BOM -->
      <dependency>
        <groupId>io.grpc</groupId><artifactId>grpc-bom</artifactId>
        <version>${grpc.version}</version><type>pom</type><scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.testcontainers</groupId><artifactId>testcontainers-bom</artifactId>
        <version>${testcontainers.version}</version><type>pom</type><scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

## 3. shortly-proto 模块（gRPC 契约）

`key.proto` 原样复制自 `D:\workspace\PROJECTS\shortly\proto\key.proto`（`package key;`），生成 Java 代码时 `option java_package` 无需改（grpc-java 默认用 proto package）。

```xml
<project>
  <parent>...parent...</parent>
  <artifactId>shortly-proto</artifactId>
  <dependencies>
    <dependency><groupId>io.grpc</groupId><artifactId>grpc-stub</artifactId></dependency>
    <dependency><groupId>io.grpc</groupId><artifactId>grpc-protobuf</artifactId></dependency>
    <dependency><groupId>javax.annotation</groupId><artifactId>javax.annotation-api</artifactId><version>1.3.2</version></dependency>
  </dependencies>
  <build>
    <extensions>
      <extension>
        <groupId>kr.motd.maven</groupId>
        <artifactId>os-maven-plugin</artifactId>
        <version>1.7.1</version>
      </extension>
    </extensions>
    <plugins>
      <plugin>
        <groupId>org.xolstice.maven.plugins</groupId>
        <artifactId>protobuf-maven-plugin</artifactId>
        <version>0.6.1</version>
        <configuration>
          <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
          <pluginId>grpc-java</pluginId>
          <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
        </configuration>
        <executions>
          <execution><goals><goal>compile</goal><goal>compile-custom</goal></goals></execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

生成产物：`KeyServiceGrpc`（stub：`newBlockingStub(channel)`）、`KeyOuterClass`（`Empty`、`KeyResponse`，`getKey()` 取 key）。

## 4. shortly-api-service POM 要点

依赖（全部必须，不得多引）：
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-data-redis
- spring-boot-starter-validation
- org.flywaydb:flyway-core + flyway-database-postgresql（Spring Boot 3.3 需显式 postgres 模块）
- org.postgresql:postgresql（runtime）
- spring-security-crypto（仅 BCrypt，不引 starter-security）
- io.jsonwebtoken:jjwt-api / jjwt-impl(runtime) / jjwt-jackson(runtime)
- shortly-proto（module 依赖）
- io.grpc:grpc-netty-shaded、io.grpc:grpc-stub、io.grpc:grpc-protobuf
- com.github.ua-parser:ua-parser:${ua-parser.version}（含 `ua_parser` 资源，类路径含 `UAParser` 时无需额外拷贝 uap-core 数据）
- spring-boot-starter-test + testcontainers（test scope）

构建插件：spring-boot-maven-plugin（repackage）；**服务端口用 `server.port`，gRPC 客户端地址用自定义属性**（见 §6）。

## 5. shortly-kgs-service POM 要点

依赖：
- spring-boot-starter（**非 web**；健康检查用内嵌 `HttpServer` 或 `spring-boot-starter-web` 二选一——推荐引入 web 依赖并用独立 Controller 监听 8081，简单且可测试）
- spring-boot-starter-data-redis
- spring-boot-starter-data-mongodb
- shortly-proto、grpc-netty-shaded、grpc-stub、grpc-protobuf
- 测试同上

> gRPC Server 不依赖 spring-boot-starter-web 的 servlet 容器：用 `io.grpc.Server` 在独立端口启动（见 06）。若引 web 依赖仅为健康检查，则健康端点端口与 gRPC 端口需分开配置。

## 6. application.yml 模板

### shortly-api-service/src/main/resources/application.yml
```yaml
server:
  port: ${PORT:8080}

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:shortly}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      maximum-pool-size: 25
      minimum-idle: 15
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate        # 表结构由 Flyway 管理，实体只做校验
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: 1               # API 缓存库
      timeout: 2s

shortly:
  jwt:
    secret: ${JWT_SECRET:change-me-please}
    expiration-hours: 24
    cookie-name: token
  kgs:
    grpc-address: ${KGS_GRPC_ADDRESS:localhost:50051}
  rate-limit:
    redis-db: 2                 # 限流独立库
  cache:
    url-ttl-hours: 24
    profile-ttl-minutes: 30
    ip-country-ttl-hours: 24
  cors:
    allow-origins: "*"
    allow-methods: GET,POST,PUT,DELETE
    allow-headers: Origin,Content-Type,Authorization
    allow-credentials: true
    max-age-hours: 12
  async:
    core-pool-size: 8
    max-pool-size: 32
    queue-capacity: 2000
  geoip:
    base-url: https://ipapi.co
    timeout-seconds: 2
```

### shortly-kgs-service/src/main/resources/application.yml
```yaml
server:
  port: ${KGS_HEALTH_PORT:8081}    # 健康检查 HTTP

spring:
  data:
    mongodb:
      uri: ${MONGO_URI:mongodb://localhost:27017}
      database: ${MONGO_DB_NAME:shortly}
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: 0                 # KGS 队列库

shortly:
  kgs:
    grpc-port: ${KGS_GRPC_PORT:50051}
    queue-name: shortly-kgs-redis-queue
    queue-threshold: 200
    batch-size: 1000
```

> 环境变量名与 Go 版 `.env.example` 保持一致（`PORT/DB_HOST/.../MONGO_URI/MONGO_DB_NAME/REDIS_ADDR/KGS_GRPC_ADDRESS/JWT_SECRET`），以 `REDIS_ADDR` 兼容为例：Java 里可用占位 `${REDIS_ADDR:localhost:6379}` 拆分 host/port，或直接解析 `host:port`。

## 7. docker-compose.yaml（Java 版，在 java/ 目录）

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: shortly-java-db
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: shortly
    ports: ["5433:5432"]
    volumes: [shortly-java-pg:/var/lib/postgresql/data]

  redis:
    image: redis:alpine
    container_name: shortly-java-redis
    ports: ["6380:6379"]

  mongo:
    image: mongo:7
    container_name: shortly-java-mongo
    ports: ["27018:27017"]
    volumes: [shortly-java-mongo:/data/db]

volumes:
  shortly-java-pg:
  shortly-java-mongo:
```

## 8. Makefile（可选，对齐 Go 版习惯）

```make
.PHONY: build run api kgs clean
build:
	mvn -f java/pom.xml clean package
run-api:
	mvn -f java/pom.xml -pl shortly-api-service spring-boot:run
run-kgs:
	mvn -f java/pom.xml -pl shortly-kgs-service spring-boot:run
clean:
	mvn -f java/pom.xml clean
```

## 9. 启动顺序（联调）

1. `docker compose up -d`（java/docker-compose.yaml）；
2. 启动 shortly-kgs-service（gRPC :50051 + health :8081）；
3. 启动 shortly-api-service（:8080）；
4. 验证：`GET localhost:8080/api/v1/health/` → `{"success": true, ...}`；`GET localhost:8081/api/v1/health` → KGS 健康。
