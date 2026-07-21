export interface RoomVO {
  id: number
  title: string
  anchorName: string
  category: string
  coverColor: string
  onlineCount: number
  status: number   // 0=已结束 1=直播中
  startedAt: string
}

export interface StartRoomRequest {
  title: string       // ≤80
  category?: string   // ≤20
  coverColor?: string
}

export interface StopRoomRequest {
  roomId: number
}
