<template>
  <div class="hall-page">
    <!-- 顶部导航 -->
    <header class="nav">
      <router-link to="/" class="logo">Live<span>Mall</span></router-link>
      <div class="nav-right">
        <template v-if="auth.isLoggedIn">
          <router-link to="/user/orders" class="user-entry">📦 订单</router-link>
          <template v-if="auth.isAnchor">
            <router-link to="/product/publish" class="streamer-entry">📦 发布</router-link>
            <router-link to="/product/manage" class="streamer-entry">📋 商品管理</router-link>
            <router-link to="/streamer/panel" class="streamer-entry">🎥 主播中心</router-link>
          </template>
          <router-link to="/user/profile" class="user-entry">
            👤 {{ userStore.nickname || '个人中心' }}
          </router-link>
          <button class="btn-ghost logout-btn" @click="onLogout">退出</button>
        </template>
        <router-link v-else to="/login" class="btn-primary login-btn">登录</router-link>
      </div>
    </header>

    <!-- 商品推荐（两排横向滚动） -->
    <main class="content">
      <section class="product-section">
        <div class="product-header">
          <div>
            <span class="section-badge">热卖</span>
            <span class="section-title">限时秒杀</span>
          </div>
          <router-link to="/products" class="more-link">查看更多 →</router-link>
        </div>
        <div class="product-scroll">
          <div v-for="col in productCols" :key="col[0].id" class="product-col">
            <div
              v-for="p in col"
              :key="p.id"
              class="product-card"
              @click="goProduct(p.id)"
            >
              <div class="product-img" :style="p.mainImage ? { backgroundImage: `url(${p.mainImage})`, backgroundSize: 'cover', backgroundPosition: 'center' } : {}">
                <span class="share-overlay" @click.stop="onCopyShare(p)">转发</span>
              </div>
              <div class="product-body">
                <p class="product-title">{{ p.title }}</p>
                <div class="product-price">
                  <span class="price-current">¥{{ p.price.toFixed(2) }}</span>
                </div>
              </div>
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
import { showToast } from '@/utils/toast'
import LiveCard from '@/components/LiveCard.vue'
import { useRoomStore } from '@/stores/room'
import { useSeckillStore } from '@/stores/seckill'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'
import { listProducts } from '@/api/product'
import type { ProductDTO } from '@/types/product'
import { toLocalShareUrl } from '@/utils/shareUrl'

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

const products = ref<ProductDTO[]>([])

const productCols = computed(() => {
  const cols: ProductDTO[][] = []
  for (let i = 0; i < products.value.length; i += 2) cols.push(products.value.slice(i, i + 2))
  return cols
})

onMounted(async () => {
  try {
    const [prodRes] = await Promise.all([
      listProducts({ size: 8 }),
      roomStore.fetchRoomList(),
      seckillStore.fetchActivities(),
    ])
    products.value = prodRes.data.records
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

function goProduct(id: number) {
  router.push(`/product/${id}`)
}

async function onLogout() {
  await logout()
  router.push('/')
}

function onCopyShare(p: ProductDTO) {
  navigator.clipboard.writeText(toLocalShareUrl(p.shareUrl)).then(() => {
    showToast('商品链接已复制', 'success')
  }).catch(() => {
    showToast('复制失败，请手动复制', 'error')
  })
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
.section-title { font-size: 18px; margin-bottom: 16px; text-align: center; }
.hint { color: var(--text-secondary); text-align: center; padding: 60px 0; }

/* ---- 商品推荐（两排横向滚动）---- */
.product-section { margin-bottom: 28px; }
.product-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 14px;
  position: relative;
}
.product-header .more-link { position: absolute; right: 0; }
.product-header .section-badge {
  font-size: 18px;
  font-weight: 800;
  color: var(--accent-red);
  margin-right: 10px;
}
.product-header .section-title { font-size: 18px; font-weight: 700; color: var(--text-primary); }
.more-link { font-size: 13px; color: var(--text-secondary); }
.more-link:hover { color: var(--accent-red); }
.product-scroll {
  display: flex;
  justify-content: center;
  gap: 10px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0 4px 6px;
  scroll-snap-type: x mandatory;
}
.product-scroll::-webkit-scrollbar { height: 4px; }
.product-scroll::-webkit-scrollbar-thumb { background: var(--border); border-radius: 2px; }
.product-col {
  flex: 0 0 175px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  scroll-snap-align: start;
}
.product-card {
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  overflow: hidden;
  transition: transform 0.15s, border-color 0.15s;
  cursor: pointer;
}
.product-card:hover { transform: translateY(-2px); border-color: var(--accent-red); }
.product-img {
  height: 120px;
  background: linear-gradient(135deg, #2a2a3e 0%, #1a1a2e 100%);
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 6px;
  position: relative;
}
.share-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  opacity: 0;
  transition: opacity 0.2s;
  letter-spacing: 2px;
}
.product-card:hover .share-overlay { opacity: 1; }
.product-body { padding: 8px 10px 10px; }
.product-title { font-size: 12px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; margin-bottom: 6px; color: var(--text-primary); }
.product-price { display: flex; align-items: baseline; gap: 6px; }
.price-current { font-family: var(--font-mono); font-size: 15px; font-weight: 700; color: var(--accent-red); }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
</style>
