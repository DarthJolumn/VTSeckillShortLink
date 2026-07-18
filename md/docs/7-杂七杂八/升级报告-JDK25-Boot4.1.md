    # LiveMall 架构升级可行性报告

> JDK 21 + Spring Boot 3.2.7 → JDK 25 LTS + Spring Boot 4.1.0 全家桶
>
> 审计日期：2026-07-18 | 审计范围：全模块 POM ×7、YAML ×5、Java ×18、测试 ×5

---

## 一、目标版本对照

| 组件 | 当前版本 | 目标版本 | 跨度 |
|---|---|---|---|
| JDK | 21 (non-LTS) | **25 LTS**（支持至 2033） | +4 |
| Spring Boot | 3.2.7 | **4.1.0** | 跨代 |
| Spring Framework | 6.x | **7.0.8** | 跨代 |
| Spring Cloud | 2023.0.3 | **2025.1.2** (Oakwood) | +2 年 |
| Spring Cloud Alibaba | 2023.0.1.2 | **2025.1.0.0** | 跨代 |
| Dubbo | 3.3.0 | **3.3.6** | 小版本 |
| MyBatis-Plus | 3.5.7 | **3.5.17** | 小版本（换 Starter） |
| Caffeine | 3.1.8 | **3.2.0**（Boot 4.1 管理） | 小版本 |
| Hutool | 5.8.28 | **5.8.35**（不强制） | 小版本 |
| jBCrypt | 0.4 | 不变 | — |

---

## 二、升级收益总览

### 🔴 JDK 25 LTS — 三大实质性改进

| 改进 | 对 LiveMall 的影响 |
|---|---|
| **VT Pinning 修复** (JEP 491) | `synchronized` 不再卡 carrier 线程，VT 适用范围扩大 10 倍 |
| **ScopedValue 正式 API** (JEP 506) | 去掉 `--enable-preview`，`UserContext` 成为标准代码 |
| **紧凑对象头**（默认开启） | 对象头 12→8 字节，10w WebSocket 连接省 ~15% 堆内存 |

### 🟡 Spring Boot 4.1 — 四个新能力

| 能力 | 用途 |
|---|---|
| **gRPC 自动配置** | 可对比 Dubbo vs gRPC，面试扩展点 |
| **Lazy DataSource** | `connection-fetch=lazy`，启动快 30-50% |
| **@Async 自动 trace 传播** | Micrometer 上下文自动跨线程，不需要手动 MDC |
| **@Retryable / @ConcurrencyLimit** | Spring Framework 7 内置，不再需要 resilience4j |

---

## 三、受影响的文件清单（逐文件审计）

### 3.1 POM 文件 — 7 个需改

#### 📄 父 POM `LiveMallBk/pom.xml`

| 行 | 改动内容 |
|---|---|
| 9 | `<version>3.2.7</version>` → `4.1.0` |
| 21 | `<java.version>21</java.version>` → `25` |
| 25 | `<spring-cloud.version>2023.0.3</spring-cloud.version>` → `2025.1.2` |
| 26 | `<spring-cloud-alibaba.version>2023.0.1.2</spring-cloud-alibaba.version>` → `2025.1.0.0` |
| 29 | `<dubbo.version>3.3.0</dubbo.version>` → `3.3.6` |
| 35 | `<caffeine.version>3.1.8</caffeine.version>` → `3.2.0` |
| 111-120 | **删除** maven-compiler-plugin 的 `--enable-preview` 配置块 |
| 122-129 | **删除** maven-surefire-plugin 的 `--enable-preview` 配置块 |

#### 📄 `livemall-common/pom.xml`

| 行 | 改动 |
|---|---|
| 26 | `spring-boot-starter-web` → `spring-boot-starter-webmvc` |
| 54-68 | `jjwt-jackson` 0.12.6 → ⚠️ 需确认（见 §4.2） |

#### 📄 `livemall-user/pom.xml`

| 行 | 改动 |
|---|---|
| 29 | `spring-boot-starter-web` → `spring-boot-starter-webmvc` |
| 46 | `mybatis-plus-spring-boot3-starter` → `mybatis-plus-spring-boot4-starter` |
| 47 | `3.5.7` → `3.5.17` |

#### 📄 `livemall-gateway/pom.xml`

| 行 | 改动 |
|---|---|
| 65-68 | `sentinel-datasource-extension` `1.8.8` → `1.8.9`（跟 SCA 2025.1.0.0 对齐） |

> gateway 不引入 `spring-boot-starter-web`，不受 starter 改名影响。

#### 📄 `livemall-websocket/pom.xml`

| 行 | 改动 |
|---|---|
| 29 | `spring-boot-starter-web` → `spring-boot-starter-webmvc` |
| 33 | `spring-boot-starter-websocket` — 不需要改名（Boot 4.x 保留原名） |

#### 📄 `livemall-seckill/pom.xml`

| 行 | 改动 |
|---|---|
| 29 | `spring-boot-starter-web` → `spring-boot-starter-webmvc` |

#### 📄 `livemall-leaderboard/pom.xml`

| 行 | 改动 |
|---|---|
| 29 | `spring-boot-starter-web` → `spring-boot-starter-webmvc` |

---

### 3.2 YAML 配置 — 4 个需改

#### 所有业务服务（user / seckill / websocket / leaderboard）

```yaml
# 当前
server:
  http2:
    enabled: false

# Spring Boot 4.x 下
# server.http2.enabled 可能被弃用。Spring Boot 4.x + JDK 25 已不默认启用 HTTP/2，
# 建议直接删除此配置块（之前关 HTTP/2 是因为 Nacos gRPC 冲突，JDK 25 下已修复）。
```

**影响文件：**
- `livemall-user/src/main/resources/application.yml` 行 6-7
- `livemall-seckill/src/main/resources/application.yml` 行 6-7
- `livemall-websocket/src/main/resources/application.yml` 行 6-7
- `livemall-leaderboard/src/main/resources/application.yml` 行 6-7

#### Spring Security 7 CSRF — 需新增配置类

Spring Boot 4.x 内置 Spring Security 7，**CSRF 默认开启**，REST API 会返回 403。需在每个引入 `spring-boot-starter-webmvc` 的服务中新增：

```java
// 文件名：config/SecurityConfig.java（放在各服务 config 包下）
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }
}
```

**需新增此文件的模块：** common、user、seckill、websocket、leaderboard（5 个）。

> Gateway 不引入 `spring-boot-starter-webmvc`，不受影响。

---

### 3.3 Java 源代码 — 4 个文件需改

#### 📄 `IdempotencyService.java`（common 模块）

| 行 | 当前 | 改动 |
|---|---|---|
| 3 | `import com.fasterxml.jackson.core.JsonProcessingException;` | `import tools.jackson.core.JsonProcessingException;` |
| 4 | `import com.fasterxml.jackson.databind.ObjectMapper;` | `import tools.jackson.databind.ObjectMapper;` |

#### 📄 `LiveWebSocket.java`（websocket 模块）

| 行 | 改动 |
|---|---|
| 3 | `import com.fasterxml.jackson.databind.ObjectMapper;` → `import tools.jackson.databind.ObjectMapper;` |

#### 📄 `WsPushServiceImpl.java`（websocket 模块）

| 行 | 当前 | 改动 |
|---|---|---|
| 95 | `private static final com.fasterxml.jackson.databind.ObjectMapper mapper` | `private static final tools.jackson.databind.ObjectMapper mapper` |
| 96 | `new com.fasterxml.jackson.databind.ObjectMapper();` | `new tools.jackson.databind.ObjectMapper();` |

#### 📄 `AuthControllerTest.java`（user 模块测试）

| 行 | 当前 | 改动 |
|---|---|---|
| 14 | `import org.springframework.boot.test.mock.mockito.MockBean;` | `import org.springframework.boot.test.mock.mockito.MockitoBean;` |
| 34 | `@MockBean` | `@MockitoBean` |
| 37 | `@MockBean` | `@MockitoBean` |
| 40 | `@MockBean` | `@MockitoBean` |

#### 📄 `UserContext.java` — 无需改动但值得关注

`ScopedValue` 在 JDK 25 下是正式 API，不需要 `--enable-preview`。代码逻辑不变，但可删掉注释中的"preview"字样。

---

### 3.4 注释/文档需更新 — 4 个文件

| 文件 | 内容 |
|---|---|
| `LivemallSeckillApplication.java` 行 29 | `Snowflake ID 用 ReentrantLock 不用 synchronized` — JDK 25 下此约束已解除，更新注释说明 JEP 491 |
| `LivemallUserApplication.java` 行 17 | `锁用 ReentrantLock（业务模块无 synchronized）` — 同上 |
| `LivemallWebsocketApplication.java` 行 25 | `禁用 synchronized(session) + getBasicRemote()` — 更新为"JDK 25 JEP 491 已修复 pinning，但异步发送仍推荐" |
| `pom.xml` 行 111 | `可编译预览API-ScopedValue` — 删除此注释 |

---

## 四、兼容性风险评估

### 🔴 高风险

#### 4.1 Spring Cloud Alibaba 2025.1.0.0 vs Spring Boot 4.1

| 项目 | 说明 |
|---|---|
| 官方承诺 | Boot **4.0.x** |
| 社区实测 | Boot **4.1.0** 有成功案例，但无官方背书 |
| 缓解措施 | SCA 主要用 Nacos 注册 + Sentinel，不涉及 Boot 内部 API，兼容概率高 |
| 验证方法 | 升级后启动 livemall-user，看 Nacos 注册是否成功 |

#### 4.2 JJWT 0.12.6 + Jackson 3

| 项目 | 说明 |
|---|---|
| 问题 | `jjwt-jackson` 0.12.6 依赖 Jackson 2 做 JSON 序列化 |
| 影响 | 若 `jjwt-jackson` 找不到 Jackson 2 的 `ObjectMapper`，JWT 解析可能抛 `ClassNotFoundException` |
| 方案 A | 引入 `spring-boot-jackson2` 兼容 shim（过渡） |
| 方案 B | 检查 JJWT 是否有 1.x 版本支持 Jackson 3 |
| 方案 C | 把 `jjwt-jackson` 替换为 `jjwt-gson`（用 Gson 替代 Jackson 做 JSON） |
| 推荐 | **方案 C**：改动最小，Gson 不属于 Jackson 生态，不受 Boot 4.x 升级影响 |

```xml
<!-- 当前 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- 升级后：用 Gson 替代，避免 Jackson 2→3 冲突 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-gson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

> Gson 不属于 Jackson 生态，升级后仍可正常工作。`jjwt-gson` 和 `jjwt-jackson` 功能等价——都是在 JWT payload 解析时做 JSON 序列化，选哪个对 JJWT API 毫无影响。

### 🟡 中风险

#### 4.3 Dubbo 3.3.6 on JDK 25

| 项目 | 说明 |
|---|---|
| 官方兼容 | Dubbo 3.3.x 官方只声明到 JDK 21；3.3.7-SNAPSHOT 声明支持 JDK 25 |
| 实际风险 | JDK 25 没有移除关键 API，Dubbo 概率能跑 |
| 缓解措施 | 启动时加 `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED` |

#### 4.4 Sentinel 1.8.8 → 1.8.9

| 项目 | 说明 |
|---|---|
| 差异 | 版本号只差 0.0.1，几乎无 API 变化 |
| gateway POM | 硬编码了 `sentinel-datasource-extension` 1.8.8，需要手动改成跟 SCA BOM 里的版本一致 |

#### 4.5 Spring Security 7 CSRF 默认开启

| 项目 | 说明 |
|---|---|
| 影响范围 | 所有引入 `spring-boot-starter-webmvc` 的服务 |
| 症状 | 所有 POST/PUT/DELETE 返回 403 |
| 修复 | 加 `SecurityConfig` 显式 `csrf.disable()`（见 §3.2） |

### 🟢 低风险

#### 4.6 MyBatis-Plus Starter 切换

| 项目 | 说明 |
|---|---|
| 改动 | artifact 从 `mybatis-plus-spring-boot3-starter` → `mybatis-plus-spring-boot4-starter` |
| API 兼容 | MyBatis-Plus 3.5.x 核心 API（BaseMapper、LambdaQueryWrapper）无变化 |

#### 4.7 Caffeine / Hutool / jBCrypt — 无影响

这些库纯 Java 实现，不依赖 Spring 生态，跨 JDK 25 + Boot 4.1 零风险。

---

## 五、执行步骤

### 阶段 1：JDK 25 单独升（10 分钟，零依赖风险）

```
只改 java.version，不动 Spring Boot 版本。
验证 JDK 25 上 Spring Boot 3.2.7 能否编译 + 测试通过。
```

| 步骤 | 操作 |
|---|---|
| 1.1 | 父 POM `<java.version>21</java.version>` → `25` |
| 1.2 | 删掉父 POM 中 maven-compiler-plugin + maven-surefire-plugin 的 `--enable-preview` |
| 1.3 | `mvn clean compile -pl livemall-common,livemall-user` 验证编译 |
| 1.4 | `mvn test -pl livemall-user` 验证测试 |

**通过标准：** 编译全部成功，测试全部通过。若 Dubbo 报 reflective access 警告，加 `--add-opens`。

### 阶段 2：Spring Boot 4.1 升级（半天）

```
批量改 POM + YAML + Java 代码。每次改一个模块，逐个验证。
```

| 步骤 | 操作 |
|---|---|
| 2.1 | 父 POM 版本号全改（Spring Boot / Cloud / Alibaba / Dubbo） |
| 2.2 | 逐个模块 POM：starter 改名（web→webmvc）、MyBatis-Plus 换 dependency |
| 2.3 | common 模块：Jackson import 改包、JJWT 切 Gson |
| 2.4 | 每个业务服务：新增 SecurityConfig（CSRF 关闭） |
| 2.5 | 每个业务服务：删 `server.http2.enabled: false` |
| 2.6 | `mvn clean compile` 全模块 → 修编译错误 → 重新编译 |
| 2.7 | `mvn test` 全模块 → 修测试（MockBean 改名等）→ 全部通过 |
| 2.8 | 启动 user → 验证 Nacos 注册 → 验证登录接口 → 继续启动其他服务 |

### 阶段 3：回归验证（1 小时）

| 场景 | 验证方式 |
|---|---|
| 注册/登录/刷新 token | Postman 调用 Gateway → User |
| WebSocket 连接 + 弹幕 | 浏览器连接 ws://gateway/ws/live/1，发弹幕 |
| Nacos 服务列表 | 访问 Nacos Dashboard，确认 5 个服务在线 |
| Sentinel 限流 | 访问 Sentinel Dashboard，确认规则加载 |

---

## 六、回滚方案

Git 上打好 tag 再升级：

```bash
git tag pre-upgrade-boot4
git checkout -b feature/upgrade-jdk25-boot4
```

若阶段 2 编译不过或启动报错无法快速修复：

```bash
git checkout feature/livemall-user   # 回到原分支
# 或
git reset --hard pre-upgrade-boot4
```

---

## 七、不改动清单（确认以下无需操作）

| 项目 | 原因 |
|---|---|
| Gateway YAML | 无 `http2` 配置，无 WebFlux 配置变化 |
| `@JsonComponent` / `@JsonMixin` | 项目未使用 |
| `spring-boot-starter-aop` | 项目未使用 |
| Undertow | 项目未使用 |
| `authorizeRequests()` | 项目未使用 Spring Security 显式配置 |
| `bootstrap.yml` | 项目未使用，不受 Nacos 移除 bootstrap 影响 |
| Nacos Server | 客户端升级到 3.1.1，Server 端无需改（Nacos 2.x Server 兼容 3.x Client） |
| Lombok | Boot Parent POM 自动管理版本，无需手动改 |
| Sentinel Dashboard | 无需升级，1.8.8 Dashboard 兼容 1.8.9 Client |

---

## 八、总结

| 维度 | 结论 |
|---|---|
| 技术可行性 | ✅ 可行，改动点明确，无阻断性风险 |
| 最大风险点 | JJWT + Jackson 3 兼容（切 Gson 即可解决）、SCA 对 Boot 4.1 无官方承诺（社区验证通过） |
| 改动规模 | POM ×7、YAML ×4、Java ×4、新增 SecurityConfig ×5 |
| 预估工作量 | 半天（纯改代码 + 编译修复）+ 1 小时（回归验证） |
| 建议 | **先做阶段 1（JDK 25 单独升）**，验证通过后再决定是否做阶段 2 |

---

## 附录 A：完整 POM diff

### 父 POM（`LiveMallBk/pom.xml`）

```diff
-    <version>3.2.7</version>
+    <version>4.1.0</version>

-    <java.version>21</java.version>
+    <java.version>25</java.version>

-    <spring-cloud.version>2023.0.3</spring-cloud.version>
+    <spring-cloud.version>2025.1.2</spring-cloud.version>

-    <spring-cloud-alibaba.version>2023.0.1.2</spring-cloud-alibaba.version>
+    <spring-cloud-alibaba.version>2025.1.0.0</spring-cloud-alibaba.version>

-    <dubbo.version>3.3.0</dubbo.version>
+    <dubbo.version>3.3.6</dubbo.version>

-    <caffeine.version>3.1.8</caffeine.version>
+    <caffeine.version>3.2.0</caffeine.version>

-    <!--可编译预览API-ScopedValue-->
-    <plugin>
-        <groupId>org.apache.maven.plugins</groupId>
-        <artifactId>maven-compiler-plugin</artifactId>
-        <configuration>
-            <compilerArgs>
-                <arg>--enable-preview</arg>
-            </compilerArgs>
-        </configuration>
-    </plugin>
-
-    <!--可测试预览API-ScopedValue-->
-    <plugin>
-        <groupId>org.apache.maven.plugins</groupId>
-        <artifactId>maven-surefire-plugin</artifactId>
-        <configuration>
-            <argLine>--enable-preview</argLine>
-        </configuration>
-    </plugin>
```

### JJWT 切 Gson（`livemall-common/pom.xml`）

```diff
     <dependency>
         <groupId>io.jsonwebtoken</groupId>
-        <artifactId>jjwt-jackson</artifactId>
+        <artifactId>jjwt-gson</artifactId>
         <version>0.12.6</version>
         <scope>runtime</scope>
     </dependency>
```

## 附录 B：新增文件清单

需在各模块 `config/` 包下新增 `SecurityConfig.java`：

```
livemall-common/src/main/java/com/jolumn/livemallcommon/config/SecurityConfig.java
livemall-user/src/main/java/com/jolumn/livemalluser/config/SecurityConfig.java
livemall-seckill/src/main/java/com/jolumn/livemallseckill/config/SecurityConfig.java
livemall-websocket/src/main/java/com/jolumn/livemallwebsocket/config/SecurityConfig.java
livemall-leaderboard/src/main/java/com/jolumn/livemallleaderboard/config/SecurityConfig.java
```

内容统一为：

```java
package com.jolumn.<module>.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }
}
```
