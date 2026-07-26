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

    <!-- 商品推荐（占上半屏） -->
    <main class="content">
      <section class="product-section">
        <div class="product-header">
          <h2 class="section-title">🔥 热卖商品</h2>
          <a class="more-link" href="javascript:">查看更多 →</a>
        </div>
        <div class="product-scroll">
          <div class="product-card">
            <div class="product-img">
              <span class="product-tag">秒杀</span>
            </div>
            <div class="product-body">
              <p class="product-title">商品名称最多两行显示</p>
              <div class="product-price">
                <span class="price-current">¥19.90</span>
                <span class="price-original">¥99.00</span>
              </div>
              <div class="product-progress"><div class="progress-bar" style="width:67%"></div></div>
              <div class="product-meta"><span>已抢 67%</span><span>剩余 12 件</span></div>
            </div>
          </div>
          <div class="product-card">
            <div class="product-img"><span class="product-tag">热卖</span></div>
            <div class="product-body">
              <p class="product-title">春季新款连衣裙</p>
              <div class="product-price">
                <span class="price-current">¥129.00</span>
                <span class="price-original">¥359.00</span>
              </div>
              <div class="product-progress"><div class="progress-bar" style="width:45%"></div></div>
              <div class="product-meta"><span>已抢 45%</span><span>剩余 55 件</span></div>
            </div>
          </div>
          <div class="product-card">
            <div class="product-img"><span class="product-tag">新品</span></div>
            <div class="product-body">
              <p class="product-title">无线蓝牙耳机 Pro</p>
              <div class="product-price">
                <span class="price-current">¥249.00</span>
                <span class="price-original">¥499.00</span>
              </div>
              <div class="product-progress"><div class="progress-bar" style="width:80%"></div></div>
              <div class="product-meta"><span>已抢 80%</span><span>剩余 20 件</span></div>
            </div>
          </div>
          <div class="product-card">
            <div class="product-img"><span class="product-tag">秒杀</span></div>
            <div class="product-body">
              <p class="product-title">智能手表 S3 运动版</p>
              <div class="product-price">
                <span class="price-current">¥599.00</span>
                <span class="price-original">¥1299.00</span>
              </div>
              <div class="product-progress"><div class="progress-bar" style="width:33%"></div></div>
              <div class="product-meta"><span>已抢 33%</span><span>剩余 67 件</span></div>
            </div>
          </div>
          <div class="product-card">
            <div class="product-img"><span class="product-tag">爆款</span></div>
            <div class="product-body">
              <p class="product-title">有机护肤品套装</p>
              <div class="product-price">
                <span class="price-current">¥399.00</span>
                <span class="price-original">¥899.00</span>
              </div>
              <div class="product-progress"><div class="progress-bar" style="width:91%"></div></div>
              <div class="product-meta"><span>已抢 91%</span><span>剩余 9 件</span></div>
            </div>
          </div>
        </div>
      </section>

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

/* ---- 商品推荐（占上半屏）---- */
.product-section { height: 50vh; display: flex; flex-direction: column; margin-bottom: 24px; }
.product-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.product-header .section-title { margin-bottom: 0; }
.more-link { font-size: 13px; color: var(--text-secondary); }
.more-link:hover { color: var(--accent-red); }
.product-scroll {
  flex: 1;
  display: flex;
  gap: 14px;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 8px;
  scroll-snap-type: x mandatory;
}
.product-scroll::-webkit-scrollbar { height: 6px; }
.product-scroll::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
.product-card {
  flex: 0 0 240px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  scroll-snap-align: start;
  transition: transform 0.15s, border-color 0.15s;
}
.product-card:hover { transform: translateY(-4px); border-color: var(--accent-red); }
.product-img {
  height: 160px;
  background: linear-gradient(135deg, #2a2a3e 0%, #1a1a2e 100%);
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 8px;
  flex-shrink: 0;
}
.product-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 8px;
  background: var(--accent-red);
  color: #fff;
  font-weight: 700;
}
.product-body { padding: 10px 12px 12px; flex: 1; display: flex; flex-direction: column; gap: 6px; }
.product-title { font-size: 13px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.product-price { display: flex; align-items: baseline; gap: 8px; }
.price-current { font-family: var(--font-mono); font-size: 18px; font-weight: 700; color: var(--accent-red); }
.price-original { font-size: 12px; color: var(--text-secondary); text-decoration: line-through; }
.product-progress { height: 4px; border-radius: 2px; background: rgba(255,44,85,0.15); overflow: hidden; }
.progress-bar { height: 100%; border-radius: 2px; background: var(--accent-red); }
.product-meta { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-secondary); }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
</style>
