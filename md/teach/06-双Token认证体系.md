# 06 — 双 Token 认证体系

> **目标：** 这是最有"面试纵深"的设计之一。从为什么双 Token，到 JWT 原理，到并发刷新，每一层都能聊。

---

## 1. 三种认证方案对比

| 维度 | Session | 纯 JWT | 双 Token（本项目） |
|------|---------|--------|-------------------|
| 原理 | 服务端存 session | 客户端存 JWT | Access JWT + Refresh Redis |
| 每次请求 | 查 Redis | 仅验签（CPU） | 仅验签（CPU） |
| 可撤销 | ✅ 删 Redis | ❌ 不可撤销 | ✅ 删 Refresh |
| 性能 | ❌ 10w IO/s | ✅ 0 IO | ✅ ~111 IO/s |
| 安全（泄露后） | 影响有限 | 永久影响 | 15min 内影响有限 |

## 2. 双 Token 设计

```
Access Token:
  - 格式：JWT (Header.Payload.Signature)
  - 算法：HS256
  - 内容：{ sub: userId, role: 1, iat: ..., exp: ... }
  - TTL：15 分钟
  - 存储：客户端 sessionStorage
  - 用途：每次请求放 Authorization: Bearer <token>
  - 特点：无状态，服务端不存，仅验签

Refresh Token:
  - 格式：rft_ + UUID（31 字符）
  - 存储：Redis key=refresh:{token}, value=userId:role:deviceId
  - TTL：7 天
  - 用途：Access Token 过期后换新的
  - 特点：有状态，服务端可随时删除（踢人/封禁）
```

## 3. 完整登录流程

```
① 用户登录（用户名+密码）
   → BCrypt 验证密码
   → 生成 Access Token（JWT，15min）
   → 生成 Refresh Token（rft_UUID，存 Redis，7d）
   → 设备绑定：SADD device_sessions:{userId} deviceId
   → 返回 { accessToken, refreshToken, user }

② 正常请求（Access Token 有效）
   → 请求带 Authorization: Bearer <accessToken>
   → Gateway JwtAuthGlobalFilter 验签
   → 提取 userId + role
   → 写入 Header X-User-Id, X-User-Role
   → 转发到下游服务

③ Access Token 过期（前端拦截器检测）
   → 前端发现 accessToken 过期（或 401）
   → 发 POST /auth/refresh { refreshToken }
   → 服务端查 Redis refresh:{token}
   → 存在且未过期 → 签发新 Access Token
   → 不存在 → 返回 401，前端跳转登录页

④ 登出
   → POST /auth/logout（带 refreshToken）
   → 删除 Redis refresh:{token}
   → 删除 Redis device_sessions:{userId} 中的 deviceId
   → 前端清除 sessionStorage
```

## 4. 并发刷新处理（前端）

**问题：** 多个请求同时发现 Access Token 过期，同时发 Refresh 请求 → 浪费。

**前端方案（`guards.js` + `auth.js`）：**
```javascript
// 并发刷新合并：同一时刻只发一个 refresh 请求
let refreshPromise = null;

async function refreshAccessToken() {
    if (refreshPromise) return refreshPromise;  // 复用已有请求
    refreshPromise = doRefresh()
        .finally(() => { refreshPromise = null; });
    return refreshPromise;
}
```

## 5. JWT 验签性能

```
HS256 (HMAC-SHA256)：纯 CPU 运算，无 IO
1 核可验 50,000+ TPS
10w QPS × 验签 ≈ 2 核 CPU

Session 方案：10w QPS × 1 次 Redis GET = 10w IO/s
               = Redis 单线程压力巨大
```

## 6. ScopedValue 用户上下文传递

**问题：** 下游服务需要知道"当前请求是谁"。传统方案是每个方法传 userId 参数。

**本项目方案：**
```
Gateway 注入 Header → Interceptor 提取 → ScopedValue 绑定

// Gateway Filter 中
exchange.getAttributes().put("userId", userId);
request.mutate()
    .header("X-User-Id", userId.toString())
    .header("X-User-Role", role.toString());

// 下游 Interceptor 中
String userId = request.getHeader("X-User-Id");
ScopedValue.where(UserContext.USER, new User(userId, role))
    .run(() -> { /* 整个请求链路 */ });

// Controller/Service 中
User user = UserContext.get().get();  // 零侵入获取当前用户
```

**为什么用 ScopedValue 不用 ThreadLocal：**
```
ThreadLocal 问题（VT 环境下）：
  - 每个 VT 有 16-slot ThreadLocalMap，即使不 set 也占内存
  - VT 池化后 ThreadLocal 不清除 → 内存泄漏 + 数据错乱

ScopedValue 优势：
  - 没 bound 的线程不占任何内存
  - 不可变（线程安全）
  - 作用域结束自动清理
  - 支持继承（子 VT 可继承父 VT 的 ScopedValue）
```

## 7. 安全问题及应对

| 威胁 | 应对 |
|------|------|
| JWT 泄露 | 15min TTL，泄露影响有限 |
| Refresh Token 泄露 | 攻击者能换 Access，但服务端可删 Refresh |
| XSS 窃取 Token | Access 存 sessionStorage（非 localStorage），关标签即清；HttpOnly Cookie 更安全但跨域麻烦 |
| CSRF | JWT 在 Header 不在 Cookie，天然防 CSRF |
| 重放攻击 | Token 有 exp + 签名有 nonce |
| 暴力破解 | BCrypt 慢哈希（12 rounds），单次 ~250ms |

## 8. 设备管理

```
登录时：SADD device_sessions:{userId} deviceId
登出时：SREM device_sessions:{userId} deviceId
查看设备：SMEMBERS device_sessions:{userId}
踢设备：SREM + 删 Redis refresh token + WS 推送 BAN
```

---

## 面试追问速答

| 追问 | 回答 |
|------|------|
| JWT 的三个部分是什么 | Header（算法+类型）、Payload（claims）、Signature（签名） |
| JWT 能放敏感信息吗 | 不能！Payload 仅 Base64 编码，非加密。敏感信息放服务端 |
| 为什么用 HS256 不用 RS256 | 单体验证足够，HS256 更快。RS256 适合多服务间验签（公钥分发） |
| BCrypt 为什么慢 | 故意慢（12 rounds），防暴力破解。单次 ~250ms，10w QPS 登录就要用异步 |
| Access Token 为什么 15 分钟 | 泄露影响窗口和刷新频率的 trade-off。5min 太频繁，1h 泄露影响大 |
| 为什么不把 token 放 Cookie | Cookie 有 CSRF 风险 + 跨域麻烦。Header 更方便 |
