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

#### Gateway 白名单模式：Ant `*` 不匹配多级路径

`application.yml` 中 `gateway.auth.public-get-paths` 使用 Ant 路径匹配器。**`*` 只匹配一级路径**，`/live/room/*` 不匹配 `/live/rooms`。

如果用户反映"匿名用户进直播大厅看不到房间列表"，优先排查此处。

修复方式：将需要公开的路径改为 `**` 模式，例如 `/live/**`。

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->
