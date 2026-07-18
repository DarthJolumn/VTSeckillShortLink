// 直播相关 REST 接口（开播/关播/房间列表）· 归 livemall-websocket 服务
// 注：WS 长连接由 infra/ws-client 建立，本文件仅放控制类 HTTP
import http from '@/infra/request'

export const liveApi = {
  startRoom(data) { return http.post('/live/room/start', data) },
  stopRoom(roomId) { return http.post('/live/room/stop', { roomId }) },
  listRooms() { return http.get('/live/rooms') },
  getRoom(roomId) { return http.get(`/live/room/${roomId}`) },
  getMyActive() { return http.get('/live/my-active-room') },
}
