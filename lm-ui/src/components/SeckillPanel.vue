<template>
  <div class="seckill-panel">
    <!-- 创建活动 -->
    <div class="panel-section">
      <h4>创建秒杀活动</h4>
      <form class="create-form" @submit.prevent="onCreate">
        <label>
          活动名称
          <input v-model.trim="form.name" class="input-dark" maxlength="200" placeholder="如：AirPods Pro 2 限量秒杀" />
        </label>
        <div class="row-2">
          <label>
            秒杀价（元）
            <input v-model.number="form.price" class="input-dark" type="number" min="0.01" step="0.01" />
          </label>
          <label>
            原价（元）
            <input v-model.number="form.origPrice" class="input-dark" type="number" min="0.01" step="0.01" />
          </label>
        </div>
        <div class="row-2">
          <label>
            库存
            <input v-model.number="form.stockTotal" class="input-dark" type="number" min="1" step="1" />
          </label>
          <label>
            商品 ID
            <input v-model.number="form.productId" class="input-dark" type="number" min="1" placeholder="默认 1001" />
          </label>
        </div>
        <div class="row-2">
          <label>
            倒计时（秒）
            <input v-model.number="form.countdownSec" class="input-dark" type="number" min="0" step="1" />
          </label>
          <label>
            持续时间（秒）
            <input v-model.number="form.durationSec" class="input-dark" type="number" min="1" step="1" />
          </label>
        </div>
        <button class="btn-primary submit" type="submit" :disabled="creating">
          {{ creating ? '创建中...' : '创建活动' }}
        </button>
      </form>
    </div>

    <!-- 活动列表 -->
    <div class="panel-section">
      <div class="list-head">
        <h4>活动列表</h4>
        <button class="btn-ghost refresh" @click="onRefresh">刷新</button>
      </div>

      <div v-if="seckillStore.activities.length === 0" class="empty">暂无活动</div>

      <table v-else class="act-table">
        <thead>
          <tr>
            <th>名称</th><th>价格</th><th>库存</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in seckillStore.activities" :key="a.id">
            <td class="name-cell">{{ a.title }}</td>
            <td class="mono red">¥{{ a.seckillPrice }}</td>
            <td class="mono">{{ a.totalStock }}</td>
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useSeckillStore } from '@/stores/seckill'
import { useRoomStore } from '@/stores/room'
import { showToast } from '@/utils/toast'
import { ApiError } from '@/utils/http'

const props = defineProps<{
  roomId?: number
}>()

const seckillStore = useSeckillStore()
const roomStore = useRoomStore()

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
})

const creating = ref(false)

onMounted(loadList)

async function loadList() {
  console.log('[SeckillPanel] loadList 开始, roomId=', props.roomId)
  try {
    const count = await seckillStore.fetchActivities(props.roomId)
    console.log('[SeckillPanel] 获取成功, 条数:', count)
  } catch (e) {
    console.error('[SeckillPanel] 获取失败:', e)
    showToast(e instanceof ApiError ? e.message : '加载失败', 'error', 8000)
  }
}

function scrollToList() {
  const el = document.querySelector('.list-head')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function onRefresh() {
  await loadList()
  scrollToList()
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
    await seckillStore.createActivity({
      name: form.name,
      price: form.price,
      origPrice: form.origPrice,
      stockTotal: form.stockTotal,
      startAt,
      endAt,
      productId: form.productId || 1001,
      roomId: props.roomId || undefined,
    })
    showToast('活动已创建', 'success')
    form.name = ''
    await loadList()
    scrollToList()
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
    await loadList()
    scrollToList()
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
</script>

<style scoped>
.seckill-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  min-height: 0;
}
.panel-section {
  padding: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.panel-section:first-child {
  flex-shrink: 0;
  max-height: 50%;
  overflow-y: auto;
}
.panel-section:last-child {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.panel-section + .panel-section { border-top: 1px solid var(--border); padding-top: 12px; margin-top: 4px; }
h4 { font-size: 14px; margin-bottom: 12px; color: var(--text-primary); flex-shrink: 0; }

.create-form { display: flex; flex-direction: column; gap: 10px; }
.create-form label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}
.row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.submit { margin-top: 4px; padding: 8px 16px; font-size: 13px; flex-shrink: 0; }

.list-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; flex-shrink: 0; }
.refresh { padding: 4px 10px; font-size: 12px; }
.empty { text-align: center; color: var(--text-secondary); padding: 24px 0; font-size: 12px; }

.act-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.act-table thead { position: sticky; top: 0; background: var(--bg-secondary); z-index: 1; }
.act-table th {
  text-align: left;
  color: var(--text-secondary);
  font-weight: 400;
  font-size: 11px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border);
}
.act-table td { padding: 8px; border-bottom: 1px solid var(--border); }
.mono { font-family: var(--font-mono); font-size: 11px; }
.red { color: var(--accent-red); font-weight: 600; }
.dim { color: var(--text-secondary); }
.name-cell { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.status { font-size: 11px; padding: 2px 6px; border-radius: 8px; }
.st-0 { color: var(--accent-gold); background: rgba(255, 214, 102, 0.1); }
.st-1 { color: var(--status-green); background: rgba(52, 199, 89, 0.1); }
.st-2, .st-3 { color: var(--text-secondary); background: rgba(142, 142, 147, 0.1); }

.op-btn {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  margin-right: 4px;
  border: 1px solid;
}
.op-btn.up { color: var(--status-green); border-color: var(--status-green); }
.op-btn.down { color: var(--accent-red); border-color: var(--accent-red); }
</style>
