<template>
  <div class="product-manage">
    <div class="page-head">
      <h3>商品管理</h3>
      <router-link to="/streamer/products/publish" class="btn-primary">+ 发布商品</router-link>
    </div>

    <div v-if="loading" class="hint">加载中...</div>

    <div v-else-if="products.length === 0" class="hint">
      暂无商品，<router-link to="/streamer/products/publish" class="link">立即发布 →</router-link>
    </div>

    <div v-else class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>商品名称</th>
            <th>价格</th>
            <th>库存</th>
            <th>状态</th>
            <th>分享链接</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in products" :key="p.id">
            <td class="mono">{{ p.id }}</td>
            <td class="title-cell">{{ p.title }}</td>
            <td class="mono">¥{{ p.price.toFixed(2) }}</td>
            <td>{{ p.stock }}</td>
            <td>
              <span :class="['status-badge', p.status === 1 ? 'on' : 'off']">
                {{ p.status === 1 ? '上架' : '下架' }}
              </span>
            </td>
            <td class="share-cell">
              <span class="share-text" @click="onCopy(p)">{{ p.shareUrl }}</span>
            </td>
            <td class="action-cell">
              <button class="btn-sm" @click="onEdit(p)">编辑</button>
              <button class="btn-sm" @click="onToggleStatus(p)">
                {{ p.status === 1 ? '下架' : '上架' }}
              </button>
              <button class="btn-sm btn-danger" @click="onDelete(p)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { showToast } from '@/utils/toast'
import { listProducts, updateProductStatus, deleteProduct } from '@/api/product'
import type { ProductDTO } from '@/types/product'

const router = useRouter()
const auth = useAuthStore()

const products = ref<ProductDTO[]>([])
const loading = ref(true)

async function fetchProducts() {
  loading.value = true
  try {
    const res = await listProducts({ userId: auth.user?.id, size: 100 })
    products.value = res.data.records
  } finally {
    loading.value = false
  }
}

function onEdit(p: ProductDTO) {
  router.push(`/streamer/products/${p.id}/edit`)
}

async function onToggleStatus(p: ProductDTO) {
  const newStatus = p.status === 1 ? 0 : 1
  try {
    await updateProductStatus(p.id, newStatus as 0 | 1)
    p.status = newStatus
    showToast(newStatus === 1 ? '已上架' : '已下架', 'success')
  } catch (e: any) {
    showToast(e?.message || '操作失败', 'error')
  }
}

async function onDelete(p: ProductDTO) {
  if (!confirm(`确认删除商品「${p.title}」？`)) return
  try {
    await deleteProduct(p.id)
    products.value = products.value.filter(x => x.id !== p.id)
    showToast('已删除', 'success')
  } catch (e: any) {
    showToast(e?.message || '删除失败', 'error')
  }
}

function onCopy(p: ProductDTO) {
  navigator.clipboard.writeText(p.shareUrl).then(() => {
    showToast('链接已复制', 'success')
  })
}

onMounted(fetchProducts)
</script>

<style scoped>
.product-manage { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; align-items: center; justify-content: space-between; }
.page-head h3 { font-size: 16px; }

.hint { color: var(--text-secondary); text-align: center; padding: 40px 0; font-size: 14px; }
.link { color: var(--accent-red); }

.table-wrap { overflow-x: auto; padding: 0; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th, .data-table td { padding: 10px 14px; text-align: left; border-bottom: 1px solid var(--border); }
.data-table th { font-weight: 600; color: var(--text-secondary); font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; background: var(--bg-primary); position: sticky; top: 0; }
.data-table tr:hover td { background: rgba(255, 255, 255, 0.02); }
.mono { font-family: var(--font-mono); }
.title-cell { max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.status-badge { font-size: 11px; padding: 2px 8px; border-radius: 4px; font-weight: 600; }
.status-badge.on { background: rgba(52, 199, 89, 0.15); color: #34c759; }
.status-badge.off { background: rgba(142, 142, 147, 0.15); color: #8e8e93; }

.share-cell { max-width: 200px; }
.share-text { cursor: pointer; color: var(--text-secondary); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }
.share-text:hover { color: var(--accent-red); }

.action-cell { display: flex; gap: 6px; white-space: nowrap; }
.btn-sm {
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 6px;
  border: 1px solid var(--border);
  color: var(--text-primary);
  transition: all 0.15s;
}
.btn-sm:hover { border-color: var(--accent-red); color: var(--accent-red); }
.btn-danger { color: var(--accent-red); border-color: transparent; }
.btn-danger:hover { background: rgba(255, 44, 85, 0.1); }
</style>
