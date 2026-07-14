# 待办清单

## P0 — 必须修

- [ ] **Gateway JSON 手拼修复** — `JwtAuthGlobalFilter.unauthorized()`、`SignVerifyGlobalFilter.unauthorized()`、`GlobalErrorWebExceptionHandler.toJson()` 方法手动拼接 JSON，有注入风险。改用 Jackson 序列化 `Result<?>`
- [ ] **Gateway 路由空格的 Bug** — `application.yml` 中 `Path=/auth/**, /user/**` 可能只匹配 `/auth/**`。应改为 `Path=/auth/**,/user/**`（去掉空格）

## P1 — 缺接口

- [ ] **`GET /user/profile`** — 获取个人资料，前端 `userApi.profile()` 已调用，后端未实现
- [ ] **`PUT /user/password`** — 修改密码，前端已预留
- [ ] **`PUT /user/ban/{userId}`** — 管理员封禁用户，前端已预留

## P2 — 优化

- [ ] **锁冲突错误码不准确** — `refresh()` 中 Redis 锁冲突返回 1013（登录过期），应改为 429（请求太频繁）或 503
- [ ] **`refresh` 测试未验证 finally 清理** — 缺少对 `pendingRefreshes.remove()` 和 `lock key delete` 的 verify
- [ ] **`unauthorized` 状态码区分** — 签名参数错误（缺头、时间戳格式错）返回 400，认证失败返回 401
- [ ] **注册昵称兜底** — 注册时 `user.setNickname(username)`
- [ ] **`login` 加 `@Transactional`** — 和 `register` 保持一致
