<template>
  <div class="products-page">
    <header class="nav">
      <router-link to="/" class="logo">Live<span>Mall</span></router-link>
      <span class="crumb">全部商品</span>
    </header>

    <main class="content">
      <div class="filter-bar">
        <button v-for="s in sortOptions" :key="s.key" :class="{ active: sortBy === s.key }"
          @click="onSort(s.key)">
          {{ s.label }}
          <span v-if="sortBy === s.key" class="sort-arrow">{{ sortDir === 'asc' ? '↑' : '↓' }}</span>
        </button>
      </div>

      <div v-if="loading" class="hint">加载中...</div>

      <template v-else-if="products.length">
        <div class="product-grid">
          <div v-for="p in products" :key="p.id" class="product-card" @click="goProduct(p.id)">
            <div class="product-img" :style="p.mainImage ? { backgroundImage: `url(${p.mainImage})`, backgroundSize: 'cover', backgroundPosition: 'center' } : {}">
              <span class="share-overlay" @click.stop="onCopyShare(p)">转发</span>
            </div>
            <div class="product-body">
              <p class="product-title">{{ p.title }}</p>
              <div class="product-price">
                <span class="price-current">¥{{ p.price.toFixed(2) }}</span>
              </div>
              <div class="product-footer">
                <span>库存 {{ p.stock }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="pagination">
          <button :disabled="page <= 1" @click="goPage(page - 1)">‹</button>
          <button v-for="p in pageButtons" :key="p" :class="{ active: page === p }" @click="goPage(p)">{{ p }}</button>
          <button :disabled="page >= totalPages" @click="goPage(page + 1)">›</button>
        </div>
      </template>

      <div v-else class="hint">暂无商品</div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from '@/utils/toast'
import { listProducts } from '@/api/product'
import type { ProductDTO } from '@/types/product'
import { toLocalShareUrl } from '@/utils/shareUrl'

const router = useRouter()

const products = ref<ProductDTO[]>([])
const loading = ref(true)
const page = ref(1)
const totalPages = ref(1)
const sortBy = ref<'createdAt' | 'price'>('createdAt')
const sortDir = ref<'asc' | 'desc'>('desc')

const sortOptions = [
  { key: 'createdAt' as const, label: '最新' },
  { key: 'price' as const, label: '价格' },
]

const pageButtons = computed(() => {
  const total = totalPages.value
  const cur = page.value
  const pages: number[] = []
  const start = Math.max(1, cur - 2)
  const end = Math.min(total, cur + 2)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

async function fetchProducts() {
  loading.value = true
  try {
    const res = await listProducts({ page: page.value, size: 20, sortBy: sortBy.value, sortDir: sortDir.value })
    products.value = res.data.records
    totalPages.value = res.data.totalPages ?? 1
  } finally {
    loading.value = false
  }
}

function goPage(p: number) {
  page.value = p
  fetchProducts()
}

function onSort(key: 'createdAt' | 'price') {
  if (sortBy.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortBy.value = key
    sortDir.value = 'desc'
  }
  page.value = 1
  fetchProducts()
}

function goProduct(id: number) {
  router.push(`/product/${id}`)
}

function onCopyShare(p: ProductDTO) {
  navigator.clipboard.writeText(toLocalShareUrl(p.shareUrl)).then(() => {
    showToast('商品链接已复制', 'success')
  }).catch(() => {
    showToast('复制失败', 'error')
  })
}

onMounted(fetchProducts)
</script>

<style scoped>
.products-page { min-height: 100vh; }
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
.crumb { font-size: 14px; color: var(--text-secondary); }

.content { padding: 24px; max-width: 1280px; margin: 0 auto; }
.hint { color: var(--text-secondary); text-align: center; padding: 60px 0; }

.filter-bar { display: flex; gap: 8px; margin-bottom: 20px; }
.filter-bar button {
  padding: 6px 18px;
  border-radius: 16px;
  font-size: 13px;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  transition: all 0.15s;
}
.filter-bar button.active { color: var(--accent-red); border-color: var(--accent-red); }
.filter-bar button:hover { border-color: var(--accent-red); }
.sort-arrow { margin-left: 2px; }

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
}
.product-card {
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  overflow: hidden;
  transition: transform 0.15s, border-color 0.15s;
  cursor: pointer;
}
.product-card:hover { transform: translateY(-3px); border-color: var(--accent-red); }
.product-img {
  height: 200px;
  background: linear-gradient(135deg, #2a2a3e 0%, #1a1a2e 100%);
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 8px;
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
.product-body { padding: 10px 12px 14px; }
.product-title { font-size: 13px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; margin-bottom: 8px; }
.product-price { display: flex; align-items: baseline; gap: 8px; margin-bottom: 6px; }
.price-current { font-family: var(--font-mono); font-size: 18px; font-weight: 700; color: var(--accent-red); }
.product-footer { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-secondary); }

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding-top: 28px;
}
.pagination button {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: transparent;
  font-size: 14px;
  cursor: pointer;
  color: var(--text-secondary);
}
.pagination button.active { border-color: var(--accent-red); color: var(--accent-red); }
.pagination button:disabled { opacity: 0.3; cursor: default; }
.pagination button:not(:disabled):hover { border-color: var(--accent-red); color: var(--accent-red); }
</style>
