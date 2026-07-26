<template>
  <div class="product-form-page">
    <div class="page-head">
      <h3>{{ isEdit ? '编辑商品' : '发布商品' }}</h3>
      <router-link to="/streamer/products" class="btn-ghost">← 返回管理</router-link>
    </div>

    <div v-if="loading" class="hint">加载中...</div>

    <form v-else class="panel card" @submit.prevent="onSubmit">
      <label>
        商品名称
        <input v-model.trim="form.title" class="input-dark" maxlength="200" required placeholder="如：无线蓝牙耳机 Pro" />
      </label>

      <label>
        副标题
        <input v-model.trim="form.subtitle" class="input-dark" maxlength="500" placeholder="可选：一句话卖点" />
      </label>

      <div class="row-2">
        <label>
          价格（元）
          <input v-model.number="form.price" class="input-dark" type="number" min="0.01" step="0.01" required />
        </label>
        <label>
          库存
          <input v-model.number="form.stock" class="input-dark" type="number" min="1" step="1" required />
        </label>
      </div>

      <label>
        主图 URL
        <input v-model.trim="form.mainImage" class="input-dark" placeholder="可选：https://example.com/image.jpg" />
      </label>

      <label>
        详情图（JSON 数组）
        <input v-model.trim="form.detailImages" class="input-dark" placeholder='可选：["https://...jpg", "https://...jpg"]' />
      </label>

      <div class="form-actions">
        <button class="btn-primary" type="submit" :disabled="submitting">
          {{ submitting ? '提交中...' : (isEdit ? '保存修改' : '发布商品') }}
        </button>
        <router-link to="/streamer/products" class="btn-ghost">取消</router-link>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from '@/utils/toast'
import { getProductById, publishProduct, updateProduct } from '@/api/product'

const route = useRoute()
const router = useRouter()

const productId = computed(() => Number(route.params.id) || 0)
const isEdit = computed(() => !!route.params.id)

const loading = ref(isEdit.value)
const submitting = ref(false)

const form = reactive({
  title: '',
  subtitle: '',
  price: null as number | null,
  stock: null as number | null,
  mainImage: '',
  detailImages: '',
})

onMounted(async () => {
  if (!isEdit.value) return
  try {
    const res = await getProductById(productId.value)
    const p = res.data
    form.title = p.title
    form.subtitle = p.subtitle || ''
    form.price = p.price
    form.stock = p.stock
    form.mainImage = p.mainImage || ''
    form.detailImages = p.detailImages || ''
  } catch (e: any) {
    showToast(e?.message || '加载商品失败', 'error')
    router.push('/streamer/products')
  } finally {
    loading.value = false
  }
})

async function onSubmit() {
  if (!form.title || form.price == null || form.stock == null) {
    showToast('请填写必填字段', 'warning')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateProduct(productId.value, {
        title: form.title,
        subtitle: form.subtitle || undefined,
        price: form.price,
        stock: form.stock,
        mainImage: form.mainImage || undefined,
        detailImages: form.detailImages || undefined,
      })
      showToast('保存成功', 'success')
    } else {
      await publishProduct({
        title: form.title,
        subtitle: form.subtitle || undefined,
        price: form.price,
        stock: form.stock,
        mainImage: form.mainImage || undefined,
        detailImages: form.detailImages || undefined,
      })
      showToast('发布成功', 'success')
    }
    router.push('/streamer/products')
  } catch (e: any) {
    showToast(e?.message || '操作失败', 'error')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.product-form-page { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; align-items: center; justify-content: space-between; }
.page-head h3 { font-size: 16px; }
.hint { color: var(--text-secondary); text-align: center; padding: 40px 0; }

.panel { padding: 24px; display: flex; flex-direction: column; gap: 16px; max-width: 560px; }
.panel label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.row-2 { display: flex; gap: 16px; }
.row-2 label { flex: 1; }
.form-actions { display: flex; gap: 12px; padding-top: 8px; }
</style>
