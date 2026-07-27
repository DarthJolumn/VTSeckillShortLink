<template>
  <div class="detail-page">
    <header class="nav">
      <router-link to="/" class="logo">Live<span>Mall</span></router-link>
      <span class="crumb">商品详情</span>
      <button class="btn-ghost back-btn" @click="$router.back()">← 返回</button>
    </header>

    <main v-if="loading" class="content"><div class="hint">加载中...</div></main>

    <main v-else-if="error" class="content"><div class="not-found">{{ error }}</div></main>

    <main v-else-if="product" class="content">
      <div class="detail-layout">
        <div class="detail-gallery">
          <div class="main-img" :style="imgStyle">
            <span v-if="product.status !== 1" class="status-tag">已下架</span>
          </div>
        </div>

        <div class="detail-info">
          <h1 class="detail-title">{{ product.title }}</h1>
          <p v-if="product.subtitle" class="detail-subtitle">{{ product.subtitle }}</p>

          <div class="detail-price">
            <span class="price-current">¥{{ product.price.toFixed(2) }}</span>
          </div>

          <div class="info-rows">
            <div class="info-row">
              <span class="info-label">库存</span>
              <span class="info-value">{{ product.stock > 0 ? product.stock + ' 件' : '已售罄' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">分享</span>
              <span class="info-value share-link" @click="onCopyLink">
                {{ localShareUrl }} <span class="copy-badge">复制</span>
              </span>
            </div>
          </div>

          <div class="detail-actions">
            <button class="btn-primary btn-large" @click="onBuy">立即购买</button>
            <button class="btn-ghost btn-large" @click="onCopyLink">📋 复制链接</button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { showToast } from '@/utils/toast'
import { getProductById } from '@/api/product'
import type { ProductDTO } from '@/types/product'
import { toLocalShareUrl } from '@/utils/shareUrl'

const route = useRoute()
const productId = Number(route.params.id)

const product = ref<ProductDTO | null>(null)
const loading = ref(true)
const error = ref('')

const imgStyle = computed(() => {
  const img = product.value?.mainImage
  return img ? { backgroundImage: `url(${img})`, backgroundSize: 'cover', backgroundPosition: 'center' }
            : {}
})

const localShareUrl = computed(() => product.value ? toLocalShareUrl(product.value.shareUrl) : '')

onMounted(async () => {
  try {
    const res = await getProductById(productId)
    product.value = res.data
  } catch (e: any) {
    error.value = e?.message || '商品不存在'
  } finally {
    loading.value = false
  }
})

function onBuy() {
  showToast('购买功能开发中', 'info')
}

async function onCopyLink() {
  if (!product.value) return
  try {
    await navigator.clipboard.writeText(localShareUrl.value)
    showToast('商品链接已复制', 'success')
  } catch {
    showToast('复制失败', 'error')
  }
}
</script>

<style scoped>
.detail-page { min-height: 100vh; }
.nav {
  display: flex;
  align-items: center;
  gap: 16px;
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
.crumb { font-size: 14px; color: var(--text-secondary); flex: 1; }
.back-btn { padding: 6px 14px; font-size: 13px; }

.content { padding: 24px; max-width: 1080px; margin: 0 auto; }
.hint { text-align: center; padding: 80px 0; color: var(--text-secondary); }

.detail-layout { display: flex; gap: 40px; align-items: flex-start; }
.detail-gallery { flex: 0 0 420px; }
.main-img {
  height: 420px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2a2a3e 0%, #1a1a2e 100%);
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 10px;
}
.status-tag {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 8px;
  background: var(--text-secondary);
  color: #fff;
  font-weight: 700;
}

.detail-info { flex: 1; min-width: 0; }
.detail-title { font-size: 22px; font-weight: 700; line-height: 1.4; margin-bottom: 6px; }
.detail-subtitle { font-size: 14px; color: var(--text-secondary); margin-bottom: 16px; }
.detail-price {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 16px 20px;
  background: rgba(255, 44, 85, 0.06);
  border-radius: 10px;
  margin-bottom: 20px;
}
.price-current { font-family: var(--font-mono); font-size: 32px; font-weight: 800; color: var(--accent-red); }

.info-rows { display: flex; flex-direction: column; margin-bottom: 28px; }
.info-row {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
}
.info-label { color: var(--text-secondary); width: 80px; flex-shrink: 0; }
.info-value { color: var(--text-primary); }
.share-link { cursor: pointer; display: flex; align-items: center; gap: 8px; }
.share-link:hover { color: var(--accent-red); }
.copy-badge {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 4px;
  border: 1px solid var(--accent-red);
  color: var(--accent-red);
}

.detail-actions { display: flex; gap: 12px; }
.btn-large { padding: 12px 32px; font-size: 15px; border-radius: 10px; }

.not-found { text-align: center; padding: 80px 0; color: var(--text-secondary); font-size: 16px; }
</style>
