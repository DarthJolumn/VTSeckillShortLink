<template>
  <div class="seckill-admin">
    <!-- 创建活动 -->
    <div class="card panel">
      <h3>创建秒杀活动</h3>
      <form class="create-form" @submit.prevent="onCreate">
        <label>
          活动名称
          <input v-model.trim="form.name" class="input-dark" maxlength="200" placeholder="如：AirPods Pro 2 限量秒杀" />
        </label>
        <label>
          秒杀价（元）
          <input v-model.number="form.price" class="input-dark" type="number" min="0.01" step="0.01" />
        </label>
        <label>
          原价（元）
          <input v-model.number="form.origPrice" class="input-dark" type="number" min="0.01" step="0.01" />
        </label>
        <label>
          库存
          <input v-model.number="form.stockTotal" class="input-dark" type="number" min="1" step="1" />
        </label>
        <label>
          开始时间
          <input v-model="form.startAt" class="input-dark" type="datetime-local" />
        </label>
        <label>
          结束时间
          <input v-model="form.endAt" class="input-dark" type="datetime-local" />
        </label>
        <label>
          商品 ID
          <input v-model.number="form.productId" class="input-dark" type="number" min="1" placeholder="默认 1001" />
        </label>
        <label>
          关联直播间 ID（选填）
          <input v-model.number="form.roomId" class="input-dark" type="number" min="0" placeholder="不关联则留空" />
        </label>
        <button class="btn-primary submit" type="submit" :disabled="creating">
          {{ creating ? '创建中...' : '创建活动' }}
        </button>
      </form>
    </div>

    <!-- 活动列表 -->
    <div class="card panel">
      <div class="list-head">
        <h3>活动列表</h3>
        <button class="btn-ghost refresh" @click="loadList">刷新</button>
      </div>

      <div v-if="seckillStore.activities.length === 0" class="empty">暂无活动</div>

      <table v-else class="act-table">
        <thead>
          <tr>
            <th>ID</th><th>名称</th><th>秒杀价</th><th>原价</th><th>库存</th>
            <th>开始时间</th><th>结束时间</th><th>直播间</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in seckillStore.activities" :key="a.id">
            <td class="mono">{{ a.id }}</td>
            <td class="name-cell">{{ a.title }}</td>
            <td class="mono red">¥{{ a.seckillPrice }}</td>
            <td class="mono dim">¥{{ a.originalPrice }}</td>
            <td class="mono">{{ a.totalStock }}</td>
            <td class="mono">{{ formatTime(a.startTime) }}</td>
            <td class="mono">{{ formatTime(a.endTime) }}</td>
            <td class="mono">{{ a.roomId || '-' }}</td>
            <td><span class="status" :class="statusClass(a.status)">{{ statusText(a.status) }}</span></td>
            <td>
              <button
                v-if="a.status === 0"
                class="op-btn up"
                @click="onUpdateStatus(a.id, 1)"
              >上架</button>
              <button
                v-if="a.status === 1 || a.status === 0"
                class="op-btn down"
                @click="onUpdateStatus(a.id, 3)"
              >下架</button>
              <span v-if="a.status === 2 || a.status === 3" class="dim">-</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="hint">⚠️ 后端列表接口当前仅返回进行中活动（status=1），全量列表为后端待补能力；Mock 环境展示全量。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useSeckillStore } from '@/stores/seckill'
import { showToast } from '@/utils/toast'
import { ApiError } from '@/utils/http'

const seckillStore = useSeckillStore()

const now = Date.now()
const toLocalInput = (ts: number) => {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const form = reactive({
  name: '',
  price: 9.9,
  origPrice: 99,
  stockTotal: 100,
  startAt: toLocalInput(now + 3600_000),
  endAt: toLocalInput(now + 7200_000),
  productId: 1001,
  roomId: undefined as number | undefined,
})

const creating = ref(false)

onMounted(loadList)

async function loadList() {
  try {
    await seckillStore.fetchActivities()
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '加载失败', 'error')
  }
}

async function onCreate() {
  if (!form.name) { showToast('请填写活动名称', 'warning'); return }
  if (form.price <= 0 || form.origPrice <= 0) { showToast('价格必须大于 0', 'warning'); return }
  if (form.stockTotal <= 0) { showToast('库存必须大于 0', 'warning'); return }
  const startAt = new Date(form.startAt).getTime()
  const endAt = new Date(form.endAt).getTime()
  if (startAt >= endAt) { showToast('开始时间不能晚于结束时间', 'warning'); return }

  creating.value = true
  try {
    // 字段名即后端 DTO 字段：name/price/origPrice/stockTotal/startAt/endAt(epoch ms)
    await seckillStore.createActivity({
      name: form.name,
      price: form.price,
      origPrice: form.origPrice,
      stockTotal: form.stockTotal,
      startAt,
      endAt,
      productId: form.productId || 1001, // 后端实体 product_id nullable=false，必须传
      roomId: form.roomId || undefined,
    })
    showToast('活动已创建', 'success')
    form.name = ''
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '创建失败', 'error')
  } finally {
    creating.value = false
  }
}

async function onUpdateStatus(id: number, status: number) {
  try {
    await seckillStore.updateActivityStatus(id, status)
    showToast(status === 1 ? '已上架' : '已下架', 'success')
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '操作失败', 'error')
  }
}

function statusText(s: number) {
  return { 0: '待开始', 1: '进行中', 2: '已结束', 3: '已取消' }[s] || '未知'
}
function statusClass(s: number) {
  return { 0: 'st-0', 1: 'st-1', 2: 'st-2', 3: 'st-3' }[s] || ''
}
function formatTime(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.seckill-admin { display: flex; flex-direction: column; gap: 16px; }
.panel { padding: 20px 24px; }
h3 { font-size: 16px; margin-bottom: 14px; }

.create-form {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.create-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.submit { grid-column: span 4; justify-self: start; }

.list-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.refresh { padding: 6px 14px; font-size: 13px; }
.empty { text-align: center; color: var(--text-secondary); padding: 40px 0; font-size: 13px; }

.act-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.act-table th {
  text-align: left;
  color: var(--text-secondary);
  font-weight: 400;
  font-size: 12px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
}
.act-table td { padding: 10px; border-bottom: 1px solid var(--border); }
.mono { font-family: var(--font-mono); font-size: 12px; }
.red { color: var(--accent-red); font-weight: 600; }
.dim { color: var(--text-secondary); }
.name-cell { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.status { font-size: 12px; padding: 2px 8px; border-radius: 10px; }
.st-0 { color: var(--accent-gold); background: rgba(255, 214, 102, 0.1); }
.st-1 { color: var(--status-green); background: rgba(52, 199, 89, 0.1); }
.st-2, .st-3 { color: var(--text-secondary); background: rgba(142, 142, 147, 0.1); }

.op-btn {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 6px;
  margin-right: 6px;
  border: 1px solid;
}
.op-btn.up { color: var(--status-green); border-color: var(--status-green); }
.op-btn.down { color: var(--accent-red); border-color: var(--accent-red); }

.hint { font-size: 12px; color: var(--text-secondary); margin-top: 12px; }
</style>
