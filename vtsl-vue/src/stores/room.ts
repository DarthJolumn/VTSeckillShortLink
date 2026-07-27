import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get, post } from '@/utils/http'
import type { RoomVO, StartRoomRequest, StopRoomRequest } from '@/types/live'

export const useRoomStore = defineStore('room', () => {
  const roomList = ref<RoomVO[]>([])
  const currentRoom = ref<RoomVO | null>(null)
  const onlineCount = ref(0)
  const activeActivityId = ref<number | null>(null)

  async function fetchRoomList() {
    const res = await get<RoomVO[]>('/live/rooms')
    roomList.value = res.data
  }

  async function fetchRoomDetail(roomId: number) {
    const res = await get<RoomVO>(`/live/room/${roomId}`)
    currentRoom.value = res.data
    onlineCount.value = res.data.onlineCount
  }

  async function fetchMyActiveRoom(): Promise<RoomVO | null> {
    const res = await get<RoomVO | null>('/live/my-active-room')
    return res.data
  }

  async function startRoom(req: StartRoomRequest) {
    const res = await post<RoomVO>('/live/room/start', req)
    currentRoom.value = res.data
  }

  async function stopRoom(req: StopRoomRequest) {
    await post('/live/room/stop', req)
    currentRoom.value = null
  }

  return { roomList, currentRoom, onlineCount, activeActivityId,
           fetchRoomList, fetchRoomDetail, fetchMyActiveRoom, startRoom, stopRoom }
})
