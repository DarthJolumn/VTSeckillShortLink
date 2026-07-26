# 用户服务 (user)

> 端口 **8081** · Spring MVC + VT · Dubbo 端口 **20881**

## 职责

注册 / 登录 / Token 刷新 / 退出登录 / 设备管理 / 踢设备下线 / 修改用户信息 / 封禁解禁

## 类结构

```
controller/
├── AuthController.java    # 注册、登录、Token 刷新、退出
├── UserController.java    # 用户信息 CRUD、设备管理、封禁
service/
├── UserService.java       # 业务核心（BCrypt + JWT + Redis 设备绑定）
├── UserDubboApiImpl.java  # Dubbo 接口实现（DubboApi）
interceptor/
├── AuthInterceptor.java   # WebMVC 拦截器（绑定 ScopedValue）
├── GlobalExceptionHandler.java  # @RestControllerAdvice 异常处理
config/
└── WebConfig.java         # 注册拦截器
```

## 数据模型

**t_user** — 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password | VARCHAR(255) | BCrypt 密文 |
| phone | VARCHAR(20) UNIQUE INDEX | 手机号 |
| role | TINYINT | 0=普通/1=主播/2=管理员 |
| status | TINYINT | 0=正常/1=封禁 |
| balance | DECIMAL(15,2) | 钱包余额（默认 8888.00） |
| created_at | DATETIME | |
| updated_at | DATETIME | |

## API

| Method | Path | 说明 | 鉴权 |
|--------|------|------|------|
| POST | `/auth/login` | 账号密码登录 | 公开 |
| POST | `/auth/register` | 注册 | 公开 |
| POST | `/auth/refresh` | 刷新 Token | 公开 |
| POST | `/auth/logout` | 退出登录 | JWT |
| PUT | `/user/info` | 修改信息 | JWT |
| GET | `/user/devices` | 设备列表 | JWT |
| DELETE | `/user/device/{deviceId}` | 踢设备下线 | JWT |

## 关键设计

- **双 Token**：Access Token（2h）+ Refresh Token（7d），Refresh Token 采用随机字符串 + Redis 存储
- **密码**：BCrypt 加密
- **设备管理**：每设备唯一 deviceId，Redis SET 记录用户活跃设备
- **ScopedValue**：`AuthInterceptor` 解析 JWT 后绑定 `USER_ID`/`ROLE` 到 ScopedValue
- **踢下线**：Redis DEL 设备 Token + WebSocket Dubbo 推送踢下线指令
