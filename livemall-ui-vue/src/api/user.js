// 用户服务接口（/auth /user） · 对应后端 2.8 接口文档 §一
import http from '@/infra/request'

export const authApi = {
  /** 注册 */
  register(data) {
    return http.post('/auth/register', data)
  },
  /** 登录 → { accessToken, refreshToken, expiresIn } */
  login(data) {
    return http.post('/auth/login', data)
  },
  /** 刷新 Access Token */
  refresh(refreshToken) {
    return http.post('/auth/refresh', { refreshToken }, { _raw: true })
  },
  /** 退出 */
  logout(refreshToken) {
    return http.post('/auth/logout', { refreshToken })
  },
}

export const userApi = {
  profile() { return http.get('/user/profile') },
  updateProfile(data) { return http.put('/user/profile', data) },
  updatePassword(data) { return http.put('/user/password', data) },
  devices() { return http.get('/user/devices') },
  kickDevice(deviceId) { return http.delete(`/user/devices/${deviceId}`) },
  ban(userId, status) { return http.put(`/user/ban/${userId}`, { status }) },
}
