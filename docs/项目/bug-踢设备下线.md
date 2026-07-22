# Bug：踢设备下线 — Redis 数据无影响

## 现象
统一用户在不同设备登录后，在「登录设备管理」页面点击某设备「下线」，前端显示已下线，但 Redis 中 `device_sessions:{userId}` 集合未变化，设备仍在集合中。

## 根因分析

### 后端
- `DeviceInfo` DTO（`livemall-user/.../dto/DeviceInfo.java`）只有 `deviceId`、`current` 两个字段
- `UserService.getDevices()` 只从 Redis 读取 deviceId 列表，无设备名/IP/登录时间等元信息

### 前端
- `Devices.vue:load()` 调用 `userApi.devices()` → `GET /user/devices`
- 前端模板需渲染 `deviceName`、`ip`、`lastLoginAt`、`expiresAt` 等字段，但后端返回的数据中没有这些字段，显示空白
- API 调用可能因响应字段缺失或格式预期不符而抛异常，进入 catch 分支：
  - `Devices.vue:108-111` → 回退到 `mockDevices()`，使用假设备 ID（`d-1`, `d-2` 等）
- `onKick()` 用 mock deviceId 调 `DELETE /user/devices/{deviceId}`
- 后端 `UserService.kickDevice()` 执行 `SREM device_sessions:{userId} d-1`，找不到 → 0 → 抛 404
- 前端 catch 分支 `Devices.vue:135-138` 从本地列表移除、显示"（演示）" toast，Redis 始终未变

## 涉及文件

| 文件 | 说明 |
|---|---|
| `livemall-user/.../dto/DeviceInfo.java` | DTO 字段不足 |
| `livemall-user/.../service/UserService.java` | `getDevices()` 无元信息；`kickDevice()` SREM |
| `livemall-ui-vue/src/views/audience/Devices.vue` | mock 降级掩盖真实数据 |

## 修复方向
1. `DeviceInfo` DTO 增加 `deviceName`、`ip`、`lastLoginAt`、`expiresAt` 字段
2. 登录时存入设备元信息到 Redis Hash `device_info:{userId}:{deviceId}`
3. `getDevices()` 返回完整设备信息
4. 前端移除 mock 降级
