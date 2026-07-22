<template>
  <div class="room-page">
    <!-- 顶部栏 -->
    <header class="topbar">
      <router-link to="/" class="back">← 大厅</router-link>
      <span class="room-title">{{ roomStore.currentRoom?.title || '直播间' }}</span>
      <router-link v-if="!auth.isLoggedIn" to="/login" class="btn-primary login-btn">登录</router-link>
    </header>

    <div class="main">
      <!-- 左侧 70% -->
      <section class="left">
        <!-- 主播信息栏 -->
        <div class="anchor-bar card">
          <div class="avatar">{{ roomStore.currentRoom?.anchorName?.charAt(0) || '?' }}</div>
          <div class="anchor-info">
            <div class="anchor-name">{{ roomStore.currentRoom?.anchorName || '加载中...' }}</div>
            <div class="anchor-meta">
              <span class="online">👁 {{ onlineText }}</span>
              <span v-if="wsConnected" class="ws-status on">● 已连接</span>
              <span v-else class="ws-status off">● 未连接</span>
            </div>
          </div>
        </div>

        <!-- 视频区 -->
        <div class="video-area">
          <!-- Demo 模式：使用公开测试流，后续替换为后端 /live/stream/{roomId} 接口 -->
          <LivePlayer stream-url="https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8" />

          <!-- 礼物特效层 -->
          <GiftEffect />

          <!-- 秒杀挂载栏 -->
          <div
            v-if="activeSeckill"
            class="seckill-bar"
            @click="drawerVisible = true"
          >
            <span class="sk-tag">秒杀</span>
            <span class="sk-name">{{ activeSeckill.title }}</span>
            <span class="sk-price">¥{{ activeSeckill.seckillPrice }}</span>
            <span class="sk-cta">立即抢 →</span>
          </div>
        </div>
      </section>

      <!-- 右侧 30% -->
      <aside class="right card">
        <div class="tabs">
          <button :class="{ active: tab === 'chat' }" @click="tab = 'chat'">聊天</button>
          <button :class="{ active: tab === 'rank' }" @click="tab = 'rank'">排行榜</button>
        </div>
        <div class="tab-body">
          <ChatPanel
            v-show="tab === 'chat'"
            :messages="msgList"
            :authenticated="canChat"
            @send="onSendBarrage"
            @send-gift="onSendGift"
          />
          <LeaderboardTab
            v-show="tab === 'rank'"
            :rankings="lbStore.rankings"
            :my-rank="lbStore.myRank"
          />
        </div>
      </aside>
    </div>

    <!-- 秒杀抽屉 -->
    <SeckillDrawer
      :visible="drawerVisible"
      :activity="activeSeckill"
      @close="drawerVisible = false"
    />

    <!-- 被踢遮罩 -->
    <KickOverlay :visible="kicked" @relogin="onRelogin" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ChatPanel from '@/components/ChatPanel.vue'
import LeaderboardTab from '@/components/LeaderboardTab.vue'
import SeckillDrawer from '@/components/SeckillDrawer.vue'
import KickOverlay from '@/components/KickOverlay.vue'
import GiftEffect from '@/components/GiftEffect.vue'
import LivePlayer from '@/components/LivePlayer.vue'
import { useRoomStore } from '@/stores/room'
import { useSeckillStore } from '@/stores/seckill'
import { useLeaderboardStore } from '@/stores/leaderboard'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/composables/useWebSocket'
import { pushGiftEffect } from '@/utils/giftEffect'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const roomId = Number(route.params.roomId)

const roomStore = useRoomStore()
const seckillStore = useSeckillStore()
const lbStore = useLeaderboardStore()
const auth = useAuthStore()

const tab = ref<'chat' | 'rank'>('chat')
const drawerVisible = ref(false)
const kicked = ref(false)

// ===== WebSocket =====
const {
  connected: wsConnected,
  authenticated: wsAuthed,
  online,
  msgList,
  connect,
  sendBarrage,
  sendGift,
  onKick,
  onGift,
  onMessage,
} = useWebSocket(roomId)

/** 已登录（或 WS 已升级认证）才能发言/送礼 */
const canChat = computed(() => auth.isLoggedIn || wsAuthed.value)

const onlineText = computed(() => {
  const n = online.value || roomStore.onlineCount
  return n >= 10000 ? (n / 10000).toFixed(1) + 'w' : String(n)
})

const videoBg = computed(() => {
  const c = roomStore.currentRoom?.coverColor || '#22222E'
  return `linear-gradient(135deg, ${c}22, #0F0F14)`
})

/** 当前房间进行中的秒杀活动 */
const activeSeckill = computed(() =>
  seckillStore.activities.find(a => a.roomId === roomId && a.status === 1) ?? null,
)

onMounted(async () => {
  // 1. 房间信息
  try {
    await roomStore.fetchRoomDetail(roomId)
  } catch {
    showToast('直播间不存在或已关闭', 'error')
    router.push('/')
    return
  }

  // 2. 秒杀活动 + 排行榜（用活动 ID 拉榜）
  try {
    await seckillStore.fetchActivities(roomId)
    const act = activeSeckill.value ?? seckillStore.activities[0]
    if (act) {
      lbStore.fetchTopN(act.id).catch(() => {})
      if (auth.user) lbStore.fetchMyRank(act.id, auth.user.id).catch(() => {})
    }
  } catch { /* 秒杀/榜单失败不阻塞 */ }

  // 3. WebSocket（匿名可连；已登录带 token）
  connect(auth.accessToken || undefined)

  // KICK → 全屏遮罩
  onKick(() => {
    auth.clearTokens()
    kicked.value = true
  })

  // 礼物 → 播特效 + 刷新排行榜
  onGift((gift) => {
    pushGiftEffect(gift)
    const act = activeSeckill.value
    if (act) lbStore.fetchTopN(act.id).catch(() => {})
  })

  // 房间关闭 / 需要登录提示
  onMessage((msg) => {
    if (msg.type === 'ROOM_CLOSED') {
      showToast('直播已结束', 'warning')
      setTimeout(() => router.push('/'), 2000)
    } else if (msg.type === 'NEED_AUTH') {
      showToast('请先登录后再操作', 'warning')
    } else if (msg.type === 'AUTH_FAILED') {
      showToast('登录状态已过期', 'error')
    }
  })
})

function onSendBarrage(content: string) {
  if (!canChat.value) {
    showToast('请先登录后再发言', 'warning')
    return
  }
  sendBarrage(content)
}

function onSendGift(giftId: number, quantity: number) {
  if (!canChat.value) {
    showToast('请先登录后再送礼', 'warning')
    return
  }
  sendGift(giftId, quantity)
}

function onRelogin() {
  router.push('/login')
}
</script>

<style scoped>
.room-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--border);
}
.back { color: var(--text-secondary); font-size: 14px; }
.back:hover { color: var(--text-primary); }
.room-title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.login-btn { padding: 6px 16px; font-size: 13px; }

.main {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 12px;
  min-height: 0;
}
.left {
  flex: 7;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.right {
  flex: 3;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.anchor-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8a63ff, #00e5ff);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 18px;
}
.anchor-name { font-weight: 600; font-size: 15px; }
.anchor-meta { display: flex; gap: 12px; font-size: 12px; color: var(--text-secondary); }
.ws-status.on { color: var(--status-green); }
.ws-status.off { color: var(--text-secondary); }

.video-area {
  flex: 1;
  border-radius: 12px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  overflow: hidden;
}
.video-placeholder { text-align: center; color: var(--text-secondary); }
.play-icon { font-size: 48px; opacity: 0.4; }
.video-placeholder p { margin-top: 8px; font-size: 13px; }

.seckill-bar {
  position: absolute;
  bottom: 16px;
  left: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(26, 26, 36, 0.95);
  border: 1px solid var(--accent-red);
  cursor: pointer;
  transition: transform 0.15s;
}
.seckill-bar:hover { transform: translateY(-2px); }
.sk-tag {
  background: var(--accent-red);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  animation: flash 1s infinite alternate;
}
@keyframes flash { from { opacity: 1; } to { opacity: 0.6; } }
.sk-name {
  flex: 1;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sk-price { font-family: var(--font-mono); color: var(--accent-red); font-weight: 700; }
.sk-cta { font-size: 12px; color: var(--accent-gold); }

.tabs {
  display: flex;
  border-bottom: 1px solid var(--border);
}
.tabs button {
  flex: 1;
  padding: 12px;
  font-size: 14px;
  color: var(--text-secondary);
  border-bottom: 2px solid transparent;
  transition: color 0.15s;
}
.tabs button.active {
  color: var(--text-primary);
  border-bottom-color: var(--accent-red);
}
.tab-body { flex: 1; min-height: 0; }
.tab-body > * { height: 100%; }
</style>
