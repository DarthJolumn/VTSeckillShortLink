# LiveMall 知识库

## JDK 25 + Spring Boot 4.1 升级要点（2026-07-18 审计）

详见 `md/docs/7-杂七杂八/升级报告-JDK25-Boot4.1.md`

### 已知问题

#### JJWT + Gson：claims.get("role", Integer.class) 抛异常

**根因**：升级报告 §4.2 将 `jjwt-jackson` 换成了 `jjwt-gson`。Gson 把 JWT payload 中的所有数字解析为 `Double`，而 JJWT 内置类型转换不支持 `Double → Integer`。

**排查方法**：Gateway 日志出现以下错误即为该问题：
```
Cannot convert existing claim value of type 'class java.lang.Double' to desired type 'class java.lang.Integer'
```

**修复方式**：所有从 JWT claims 读取 `Integer` 类型的地方，将：
```java
Integer role = claims.get("role", Integer.class);
```
改为：
```java
Integer role = ((Number) claims.get("role")).intValue();
```

受影响的文件（共 3 处）：
- `livemall-gateway/.../JwtAuthGlobalFilter.java:82`
- `livemall-websocket/.../LiveWebSocket.java:43`
- `livemall-websocket/.../LiveWebSocket.java:135`

#### Spring Cloud Gateway 5.0+ 路由配置路径变更

升级报告 §4.8：Gateway 5.0.2 路由配置路径从 `spring.cloud.gateway.routes` 变为 `spring.cloud.gateway.server.webflux.routes`。

#### Spring Boot 4.x 下 Jackson 包名变更

`com.fasterxml.jackson` → `tools.jackson`（升级报告 §3.3）
