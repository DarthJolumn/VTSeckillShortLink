<template>
  <div class="studio-page">
    <div class="studio-layout">
      <!-- 左侧：推流 + 数据 -->
      <div class="studio-left">
        <!-- 推流区 -->
        <LivePusher :pushing="isLive" @start="onStart" @stop="onStop" />

        <!-- 开播设置（未开播时可编辑） -->
        <div v-if="!isLive" class="card panel">
          <h3>开播设置</h3>
          <div class="form-row">
            <label>
              直播标题
              <input v-model.trim="form.title" class="input-dark" maxlength="80" placeholder="今晚播什么？" />
            </label>
            <label>
              分类
              <input v-model.trim="form.category" class="input-dark" maxlength="20" placeholder="如：数码 / 服饰" />
            </label>
            <label>
              封面色
              <input v-model="form.coverColor" type="color" class="color-input" />
            </label>
          </div>
        </div>

        <!-- 实时数据（直播中） -->
        <div v-else class="card panel">
          <h3>实时数据</h3>
          <div class="stats">
            <div class="stat">
              <div class="stat-value">{{ roomStore.currentRoom?.id ?? '-' }}</div>
              <div class="stat-label">房间号</div>
            </div>
            <div class="stat">
              <div class="stat-value">{{ roomStore.onlineCount }}</div>
              <div class="stat-label">在线人数</div>
            </div>
            <div class="stat">
              <div class="stat-value gold">{{ durationText }}</div>
              <div class="stat-label">开播时长</div>
            </div>
          </div>
          <p class="hint">弹幕 / 礼物收益数据需 WebSocket 联调后展示</p>
          <router-link v-if="roomStore.currentRoom" :to="`/live/${roomStore.currentRoom.id}`" class="btn-ghost view-btn">
            查看观众视角 →
          </router-link>
        </div>
      </div>

      <!-- 右侧：评论区 + 秒杀管理 -->
      <div class="studio-right card">
        <div class="tabs">
          <button :class="{ active: rightTab === 'chat' }" @click="rightTab = 'chat'">
            评论区
            <span v-if="msgList.length > 0" class="badge">{{ msgList.length > 99 ? '99+' : msgList.length }}</span>
          </button>
          <button :class="{ active: rightTab === 'seckill' }" @click="rightTab = 'seckill'">秒杀管理</button>
        </div>
        <div class="tab-body">
          <ChatPanel
            v-show="rightTab === 'chat'"
            :messages="msgList"
            :authenticated="isLive"
            @send="onSendBarrage"
            @send-gift="onSendGift"
          />
          <SeckillPanel
            v-show="rightTab === 'seckill'"
            :room-id="roomStore.currentRoom?.id"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import LivePusher from '@/components/LivePusher.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import SeckillPanel from '@/components/SeckillPanel.vue'
import { useRoomStore } from '@/stores/room'
import { usePusherStore } from '@/stores/pusher'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/composables/useWebSocket'
import { showToast } from '@/utils/toast'
import { ApiError } from '@/utils/http'

const roomStore = useRoomStore()
const pusher = usePusherStore()
const auth = useAuthStore()

const form = reactive({ title: '', category: '', coverColor: '#8a63ff' })
/** 是否直播中（以服务端活跃房间为准） */
const isLive = ref(false)
const busy = ref(false)
const rightTab = ref<'chat' | 'seckill'>('chat')

const durationText = computed(() => {
  const s = pusher.pushDuration
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`
})

// WebSocket 连接（主播开播后连接房间）
const roomId = computed(() => roomStore.currentRoom?.id ?? 0)
const {
  msgList,
  connect: wsConnect,
  disconnect: wsDisconnect,
  sendBarrage,
  sendGift,
} = useWebSocket(roomId)

// 进入页面先查是否已在直播（刷新恢复状态）
onMounted(async () => {
  try {
    const room = await roomStore.fetchMyActiveRoom()
    if (room) {
      roomStore.currentRoom = room
      isLive.value = true
      pusher.markPushing()
      // 恢复直播状态后连接 WebSocket
      wsConnect(auth.accessToken || undefined)
      // 如果摄像头已断开（如切页后返回），尝试重新初始化
      if (!pusher.localStream && pusher.status !== 'CAMERA_INIT') {
        try {
          await pusher.initCamera()
        } catch {
          // 摄像头初始化失败不影响直播状态恢复
        }
      }
    }
  } catch { /* 忽略，按未开播展示 */ }
})

async function onStart() {
  if (!form.title) {
    showToast('请先填写直播标题', 'warning')
    return
  }
  busy.value = true
  try {
    // TODO(联调): 真实流程先 POST /live/webrtc/push 完成 WHEP 握手，再开播绑定 sessionId
    await roomStore.startRoom({ ...form })
    isLive.value = true
    pusher.markPushing()
    showToast('已开播', 'success')
    // 开播后连接 WebSocket 接收观众弹幕
    wsConnect(auth.accessToken || undefined)
  } catch (e) {
    pusher.markError()
    showToast(e instanceof ApiError ? e.message : '开播失败', 'error')
  } finally {
    busy.value = false
  }
}

async function onStop() {
  const roomId = roomStore.currentRoom?.id
  if (!roomId) return
  busy.value = true
  try {
    await roomStore.stopRoom({ roomId })
    isLive.value = false
    pusher.reset()
    wsDisconnect()
    showToast('已关播', 'success')
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '关播失败', 'error')
  } finally {
    busy.value = false
  }
}

function onSendBarrage(content: string) {
  if (!isLive.value) {
    showToast('请先开播', 'warning')
    return
  }
  sendBarrage(content)
}

function onSendGift(giftId: number, quantity: number) {
  if (!isLive.value) {
    showToast('请先开播', 'warning')
    return
  }
  sendGift(giftId, quantity)
}

onUnmounted(() => {
  wsDisconnect()
})
</script>

<style scoped>
.studio-page { display: flex; flex-direction: column; gap: 16px; }
.studio-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.studio-left {
  flex: 7;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.studio-right {
  flex: 3;
  min-width: 320px;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 600px;
}

.panel { padding: 20px 24px; }
h3 { font-size: 16px; margin-bottom: 14px; }
.form-row { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; }
.form-row label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  flex: 1;
  min-width: 160px;
}
.color-input {
  width: 48px;
  height: 38px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-primary);
  padding: 4px;
}
.stats { display: flex; gap: 32px; }
.stat-value { font-family: var(--font-mono); font-size: 24px; font-weight: 700; }
.stat-value.gold { color: var(--accent-gold); }
.stat-label { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.hint { font-size: 12px; color: var(--text-secondary); margin-top: 14px; }
.view-btn { display: inline-block; margin-top: 10px; padding: 8px 16px; font-size: 13px; }

.tabs {
  display: flex;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.tabs button {
  flex: 1;
  padding: 12px;
  font-size: 14px;
  color: var(--text-secondary);
  border-bottom: 2px solid transparent;
  transition: color 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.tabs button.active {
  color: var(--text-primary);
  border-bottom-color: var(--accent-red);
}
.badge {
  font-size: 11px;
  background: var(--accent-red);
  color: #fff;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
}
.tab-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.tab-body > * { height: 100%; }
</style>
