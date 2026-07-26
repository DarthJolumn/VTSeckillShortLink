# 网关服务 (gateway)

> 端口 **8080** · WebFlux 响应式 · 不做业务逻辑

## 职责

1. **路由转发** — 5 条路由分发到下游服务（Nacos `lb://`）
2. **JWT 鉴权** — HMAC-SHA256 验签，白名单放行
3. **签名验签** — 可选接口签名 + Redis Nonce 防重放
4. **Sentinel 限流** — 接口级限流降级
5. **CORS 跨域** — 允许前端跨域请求

## 类结构

```
filter/
├── JwtAuthGlobalFilter.java    # 全局 JWT 鉴权过滤器
├── SignVerifyGlobalFilter.java # 可选签名验签过滤器
└── GlobalErrorWebExceptionHandler.java  # WebFlux 全局异常处理
config/
├── CorsConfig.java             # CORS 配置
└── ...SentinelConfig.java      # Sentinel 规则
```

## 路由表

| ID | Path | 后端 | 说明 |
|----|------|------|------|
| user-service | `/auth/**`, `/user/**` | `lb://livemall-user:8081` | 用户认证 + CRUD |
| seckill-service | `/seckill/**` | `lb://livemall-seckill:8090` | 秒杀业务 |
| leaderboard-service | `/leaderboard/**` | `lb://livemall-leaderboard:8084` | 排行榜 |
| live-service | `/live/**` | `lb://livemall-websocket:8083` | 直播间 REST |
| websocket-service | `/ws/**` | `lb:ws://livemall-websocket:8083` | WebSocket 长连接 |

## 鉴权白名单

**完全公开**（不验 JWT）：`/auth/login`, `/auth/register`, `/auth/refresh`, `/ws/**`, `/actuator/health`

**GET 公开**（仅 GET 放行）：`/live/rooms`, `/live/room/**`, `/leaderboard/**`
