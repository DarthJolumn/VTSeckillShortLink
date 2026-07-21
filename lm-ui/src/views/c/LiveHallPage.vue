<template>
  <div class="hall-page">
    <!-- 顶部导航 -->
    <header class="nav">
      <router-link to="/" class="logo">Live<span>Mall</span></router-link>
      <div class="nav-right">
        <template v-if="auth.isLoggedIn">
          <router-link to="/user/orders" class="user-entry">📦 订单</router-link>
          <router-link v-if="auth.isAnchor" to="/streamer/panel" class="streamer-entry">
            🎥 主播中心
          </router-link>
          <router-link to="/user/profile" class="user-entry">
            👤 {{ userStore.nickname || '个人中心' }}
          </router-link>
          <button class="btn-ghost logout-btn" @click="onLogout">退出</button>
        </template>
        <router-link v-else to="/login" class="btn-primary login-btn">登录</router-link>
      </div>
    </header>

    <!-- 直播间网格 -->
    <main class="content">
      <h2 class="section-title">正在直播</h2>
      <div v-if="loading" class="hint">加载中...</div>
      <div v-else-if="roomStore.roomList.length === 0" class="hint">暂无直播</div>
      <div v-else class="grid">
        <LiveCard
          v-for="room in roomStore.roomList"
          :key="room.id"
          :room="room"
          :has-seckill="seckillRoomIds.has(room.id)"
          @click="goRoom"
        />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import LiveCard from '@/components/LiveCard.vue'
import { useRoomStore } from '@/stores/room'
import { useSeckillStore } from '@/stores/seckill'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const roomStore = useRoomStore()
const seckillStore = useSeckillStore()
const auth = useAuthStore()
const userStore = useUserStore()
const { logout } = useAuth()

const loading = ref(true)

/** 有进行中秒杀活动的直播间集合 → 秒杀角标 */
const seckillRoomIds = computed(() => {
  const now = Date.now()
  return new Set(
    seckillStore.activities
      .filter(a => a.status === 1 && new Date(a.startTime).getTime() <= now && now <= new Date(a.endTime).getTime())
      .map(a => a.roomId),
  )
})

onMounted(async () => {
  try {
    await Promise.all([roomStore.fetchRoomList(), seckillStore.fetchActivities()])
    if (auth.isLoggedIn && !auth.user) {
      userStore.fetchProfile().catch(() => {})
    }
  } finally {
    loading.value = false
  }
})

function goRoom(roomId: number) {
  router.push(`/live/${roomId}`)
}

async function onLogout() {
  await logout()
  router.push('/')
}
</script>

<style scoped>
.hall-page { min-height: 100vh; }
.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  background: rgba(15, 15, 20, 0.9);
  backdrop-filter: blur(8px);
  z-index: 100;
}
.logo { font-size: 22px; font-weight: 800; }
.logo span { color: var(--accent-red); }
.nav-right { display: flex; align-items: center; gap: 12px; }
.user-entry { font-size: 14px; color: var(--text-secondary); }
.user-entry:hover { color: var(--text-primary); }
.streamer-entry { font-size: 14px; color: var(--accent-gold); }
.streamer-entry:hover { opacity: 0.85; }
.login-btn { padding: 8px 20px; font-size: 13px; }
.logout-btn { padding: 6px 14px; font-size: 13px; }

.content { padding: 24px; max-width: 1280px; margin: 0 auto; }
.section-title { font-size: 18px; margin-bottom: 16px; }
.hint { color: var(--text-secondary); text-align: center; padding: 60px 0; }
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
</style>
