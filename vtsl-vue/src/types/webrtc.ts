export interface WhipSignalRequest {
  sdp: string
  roomId: number
}

export interface WhipSignalResponse {
  sdp: string
  sessionId: string
  streamUrl: string
}

export interface CameraDevice {
  deviceId: string
  label: string
  facing: 'user' | 'environment'
}

/** 推流状态机 */
export type PushStatus =
  | 'IDLE'         // 未开始
  | 'CAMERA_INIT'  // 正在获取摄像头
  | 'READY'        // 摄像头就绪，待推流
  | 'CONNECTING'   // 正在连接信令服务
  | 'PUSHING'      // 推流中
  | 'ERROR'        // 异常
