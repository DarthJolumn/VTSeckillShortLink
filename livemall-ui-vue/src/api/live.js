// 直播相关 REST 接口（开播/关播）· 归 livemall-websocket 服务
// 注：WS 长连接由 infra/ws-client 建立，本文件仅放控制类 HTTP
import http from '@/infra/request'

export const liveApi = {
  startRoom(roomId) { return http.put(`/live/room/${roomId}/start`) },
  stopRoom(roomId) { return http.put(`/live/room/${roomId}/stop`) },
}
