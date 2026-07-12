<template>
  <div class="act-mgmt">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">秒杀活动管理</h1>
        <p class="page-head__sub">创建 / 上下架 / 调库存 · 对接 <code>/seckill/activity</code></p>
      </div>
      <button class="add-btn" @click="onCreate">
        <span class="add-btn__icon">＋</span> 新建活动
      </button>
    </header>

    <!-- 筛选 -->
    <div class="filters glass">
      <button v-for="f in filters" :key="f.key"
        class="filter" :class="{ 'is-on': activeFilter === f.key }"
        @click="activeFilter = f.key">
        {{ f.label }}
        <span class="filter__count">{{ f.count }}</span>
      </button>
      <div class="filters__search">
        <input v-model.trim="keyword" type="text" placeholder="搜索活动名称…" />
      </div>
    </div>

    <!-- 活动列表 -->
    <div v-if="loading && !activities.length" class="loading glass">
      <div class="spinner" /> 加载中…
    </div>
    <div v-else-if="!filtered.length" class="empty glass">
      <div class="empty__icon">📦</div>
      <p>{{ keyword ? '未找到匹配的活动' : '暂无活动，点击右上角创建' }}</p>
    </div>
    <div v-else class="act-grid">
      <div v-for="a in filtered" :key="a.id" class="act-card glass" :class="`act-card--${statusKey(a.status)}`">
        <div class="act-card__cover" :style="{ background: a.cover }">
          <span class="act-card__status">{{ statusLabel(a.status) }}</span>
          <span class="act-card__discount">{{ a.discount }}折</span>
        </div>
        <div class="act-card__body">
          <h3 class="act-card__name">{{ a.name }}</h3>
          <div class="act-card__price">
            <span class="now">¥{{ a.price }}</span>
            <span class="orig">¥{{ a.origPrice }}</span>
          </div>
          <div class="act-card__stock">
            <StockBar :stock="a.stock" :total="a.stockTotal" />
          </div>
          <div class="act-card__meta">
            <span>⏰ {{ formatTime(a.startAt) }}</span>
            <span v-if="a.endAt"> → {{ formatTime(a.endAt) }}</span>
          </div>
          <div class="act-card__stats">
            <span>已售 {{ a.sold }}</span>
            <span>·</span>
            <span>浏览 {{ a.views }}</span>
            <span>·</span>
            <span class="revenue">收益 ¥{{ (a.sold * a.price).toFixed(0) }}</span>
          </div>
          <div class="act-card__actions">
            <button v-if="a.status === 0" class="op op--start" @click="onStart(a)">上架</button>
            <button v-if="a.status === 1" class="op op--stop" @click="onStop(a)">下架</button>
            <button v-if="a.status !== 2" class="op op--edit" @click="onEdit(a)">编辑</button>
            <button v-if="a.status === 2" class="op op--relaunch" @click="onRelaunch(a)">复用</button>
            <button v-if="a.status !== 2" class="op op--del" @click="onDelete(a)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <transition name="modal">
      <div v-if="dialogOpen" class="dlg-mask" @click.self="onCloseDialog">
        <div class="dlg glass">
          <div class="dlg__head">
            <h3>{{ editing ? '编辑活动' : '新建活动' }}</h3>
            <button class="dlg__close" @click="onCloseDialog">×</button>
          </div>
          <form class="dlg__form" @submit.prevent="onSubmit">
            <label class="field">
              <span class="field__label">活动名称</span>
              <input v-model.trim="form.name" type="text" maxlength="40" required placeholder="例：旗舰降噪耳机 · 限量500台" />
            </label>
            <div class="field-row">
              <label class="field">
                <span class="field__label">秒杀价</span>
                <input v-model.number="form.price" type="number" min="0.01" step="0.01" required />
              </label>
              <label class="field">
                <span class="field__label">原价</span>
                <input v-model.number="form.origPrice" type="number" min="0.01" step="0.01" required />
              </label>
            </div>
            <div class="field-row">
              <label class="field">
                <span class="field__label">库存</span>
                <input v-model.number="form.stockTotal" type="number" min="1" required />
              </label>
              <label class="field">
                <span class="field__label">每人限购</span>
                <input v-model.number="form.limit" type="number" min="1" required />
              </label>
            </div>
            <div class="field-row">
              <label class="field">
                <span class="field__label">开始时间</span>
                <input v-model="form.startAt" type="datetime-local" required />
              </label>
              <label class="field">
                <span class="field__label">持续分钟</span>
                <input v-model.number="form.durationMin" type="number" min="1" required />
              </label>
            </div>
            <label class="field">
              <span class="field__label">封面色卡</span>
              <div class="cover-picker">
                <button v-for="(c, i) in COVER_COLORS" :key="i" type="button"
                  class="cover-item" :class="{ 'is-on': form.coverIdx === i }"
                  :style="{ background: c }" @click="form.coverIdx = i" />
              </div>
            </label>
            <div class="dlg__actions">
              <NeonButton type="submit" :loading="submitting">{{ editing ? '保存修改' : '创建活动' }}</NeonButton>
              <NeonButton variant="ghost" type="button" @click="onCloseDialog">取消</NeonButton>
            </div>
          </form>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { seckillApi } from '@/api/seckill'
import { showToast } from '@/utils/toast'
import { ACTIVITY_STATUS } from '@/constants'
import NeonButton from '@/components/base/NeonButton.vue'
import StockBar from '@/components/base/StockBar.vue'

const COVER_COLORS = [
  'linear-gradient(135deg,#1a1240,#0a2233)',
  'linear-gradient(135deg,#3a0d2a,#2a1a0d)',
  'linear-gradient(135deg,#0d3a2a,#1a2a0d)',
  'linear-gradient(135deg,#2a0d3a,#0d1a3a)',
  'linear-gradient(135deg,#6b4dff,#00e5ff)',
  'linear-gradient(135deg,#ff5470,#ff8a00)',
]

const loading = ref(false)
const activities = ref([])
const activeFilter = ref('all')
const keyword = ref('')

const filters = computed(() => [
  { key: 'all', label: '全部', count: activities.value.length },
  { key: 'pending', label: '待开始', count: activities.value.filter(a => a.status === 0).length },
  { key: 'running', label: '进行中', count: activities.value.filter(a => a.status === 1).length },
  { key: 'ended', label: '已结束', count: activities.value.filter(a => a.status === 2).length },
  { key: 'cancelled', label: '已取消', count: activities.value.filter(a => a.status === 3).length },
])

const filtered = computed(() => {
  let res = activities.value
  if (activeFilter.value !== 'all') {
    const map = { pending: 0, running: 1, ended: 2, cancelled: 3 }
    res = res.filter(a => a.status === map[activeFilter.value])
  }
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    res = res.filter(a => a.name.toLowerCase().includes(k))
  }
  return res
})

// —— 加载 ——
async function load() {
  loading.value = true
  try {
    const list = await seckillApi.activityList({ anchorId: 'me' })
    activities.value = Array.isArray(list) ? list : (list?.records || [])
  } catch {
    activities.value = mockActivities()
    showToast('后端未就绪，已加载演示数据', 'info')
  } finally {
    loading.value = false
  }
}

// —— 状态 ——
function statusLabel(s) {
  return { 0: '待开始', 1: '进行中', 2: '已结束', 3: '已取消' }[s] || '未知'
}
function statusKey(s) {
  return { 0: 'pending', 1: 'running', 2: 'ended', 3: 'cancelled' }[s] || 'pending'
}

// —— 操作 ——
async function onStart(a) {
  try {
    await seckillApi.updateActivityStatus(a.id, ACTIVITY_STATUS.RUNNING)
    a.status = ACTIVITY_STATUS.RUNNING
    showToast(`「${a.name}」已上架`, 'success')
  } catch {
    a.status = ACTIVITY_STATUS.RUNNING
    showToast(`「${a.name}」已上架（演示）`, 'success')
  }
}
async function onStop(a) {
  if (!confirm(`确定下架「${a.name}」？`)) return
  try {
    await seckillApi.updateActivityStatus(a.id, ACTIVITY_STATUS.ENDED)
    a.status = ACTIVITY_STATUS.ENDED
    showToast(`「${a.name}」已下架`, 'info')
  } catch {
    a.status = ACTIVITY_STATUS.ENDED
    showToast(`「${a.name}」已下架（演示）`, 'info')
  }
}
function onEdit(a) {
  editing.value = a
  Object.assign(form, {
    name: a.name,
    price: a.price,
    origPrice: a.origPrice,
    stockTotal: a.stockTotal,
    limit: a.limit || 1,
    startAt: toLocal(a.startAt),
    durationMin: a.durationMin || 30,
    coverIdx: a.coverIdx ?? 0,
  })
  dialogOpen.value = true
}
function onRelaunch(a) {
  // 复用：复制参数创建新活动
  editing.value = null
  Object.assign(form, {
    name: a.name,
    price: a.price,
    origPrice: a.origPrice,
    stockTotal: a.stockTotal,
    limit: a.limit || 1,
    startAt: toLocal(Date.now() + 60 * 60 * 1000),
    durationMin: a.durationMin || 30,
    coverIdx: a.coverIdx ?? 0,
  })
  dialogOpen.value = true
}
async function onDelete(a) {
  if (!confirm(`确定删除「${a.name}」？此操作不可恢复。`)) return
  try {
    await seckillApi.updateActivityStatus(a.id, ACTIVITY_STATUS.CANCELLED)
  } catch { /* 演示模式忽略 */ }
  activities.value = activities.value.filter(x => x.id !== a.id)
  showToast(`已删除「${a.name}」`, 'warning')
}

// —— 弹窗 ——
const dialogOpen = ref(false)
const editing = ref(null)
const submitting = ref(false)
const form = reactive({
  name: '',
  price: 299,
  origPrice: 899,
  stockTotal: 500,
  limit: 1,
  startAt: '',
  durationMin: 30,
  coverIdx: 0,
})

function onCreate() {
  editing.value = null
  Object.assign(form, {
    name: '',
    price: 299,
    origPrice: 899,
    stockTotal: 500,
    limit: 1,
    startAt: toLocal(Date.now() + 10 * 60 * 1000),
    durationMin: 30,
    coverIdx: 0,
  })
  dialogOpen.value = true
}
function onCloseDialog() {
  dialogOpen.value = false
  editing.value = null
}

async function onSubmit() {
  if (form.price >= form.origPrice) { showToast('秒杀价应低于原价', 'warning'); return }
  if (form.stockTotal < 1) { showToast('库存必须大于 0', 'warning'); return }
  if (!form.startAt) { showToast('请选择开始时间', 'warning'); return }
  submitting.value = true
  const payload = {
    name: form.name,
    price: form.price,
    origPrice: form.origPrice,
    stock: form.stockTotal,
    stockTotal: form.stockTotal,
    limit: form.limit,
    startAt: new Date(form.startAt).getTime(),
    endAt: new Date(form.startAt).getTime() + form.durationMin * 60 * 1000,
    durationMin: form.durationMin,
    coverIdx: form.coverIdx,
    cover: COVER_COLORS[form.coverIdx],
    discount: Math.round((form.price / form.origPrice) * 10),
  }
  try {
    if (editing.value) {
      // 编辑：调用 update（接口未实现，仅本地更新）
      await seckillApi.updateActivityStatus(editing.value.id, editing.value.status).catch(() => {})
      Object.assign(editing.value, payload)
      showToast('活动已更新', 'success')
    } else {
      // 新建
      const created = await seckillApi.createActivity(payload).catch(() => null)
      const newAct = created || {
        id: `A${Date.now().toString(36).toUpperCase().slice(-6)}`,
        ...payload,
        status: ACTIVITY_STATUS.PENDING,
        sold: 0,
        views: 0,
      }
      activities.value.unshift(newAct)
      showToast('活动已创建', 'success')
    }
    onCloseDialog()
  } catch (e) {
    showToast('操作失败：' + (e?.message || '请稍后重试'), 'danger')
  } finally {
    submitting.value = false
  }
}

// —— 时间 ——
function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function toLocal(ts) {
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// —— mock ——
function mockActivities() {
  const now = Date.now()
  return [
    { id: 'A001', name: '旗舰降噪耳机 · 限量500台', price: 299, origPrice: 899, stock: 480, stockTotal: 500, sold: 20, views: 2340, status: 1, startAt: now - 5 * 60 * 1000, endAt: now + 25 * 60 * 1000, durationMin: 30, coverIdx: 4, cover: COVER_COLORS[4], discount: 3, limit: 1 },
    { id: 'A002', name: '机械键盘 RGB · 200台', price: 199, origPrice: 599, stock: 200, stockTotal: 200, sold: 0, views: 880, status: 0, startAt: now + 30 * 60 * 1000, endAt: now + 60 * 60 * 1000, durationMin: 30, coverIdx: 0, cover: COVER_COLORS[0], discount: 3, limit: 1 },
    { id: 'A003', name: '4K 显示器 · 50台', price: 1299, origPrice: 2999, stock: 0, stockTotal: 50, sold: 50, views: 5120, status: 2, startAt: now - 2 * 3600 * 1000, endAt: now - 90 * 60 * 1000, durationMin: 30, coverIdx: 5, cover: COVER_COLORS[5], discount: 4, limit: 1 },
    { id: 'A004', name: '蓝牙音箱 · 100台', price: 99, origPrice: 299, stock: 100, stockTotal: 100, sold: 0, views: 0, status: 3, startAt: now - 24 * 3600 * 1000, endAt: now - 23 * 3600 * 1000, durationMin: 60, coverIdx: 2, cover: COVER_COLORS[2], discount: 3, limit: 2 },
  ]
}

onMounted(load)
</script>

<style scoped>
.act-mgmt { display: flex; flex-direction: column; gap: 16px; }

.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.page-head__title { margin: 0; font-family: var(--font-display); font-size: 24px; color: var(--text-strong); letter-spacing: 0.04em; }
.page-head__sub { margin: 6px 0 0; font-size: 13px; color: var(--text-muted); }
.page-head__sub code { font-family: var(--font-num); color: var(--neon-cyan); }
.add-btn {
  padding: 10px 18px;
  border-radius: 999px;
  border: 1px solid var(--neon-purple);
  background: linear-gradient(135deg, var(--neon-purple), var(--neon-purple-soft));
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex; align-items: center; gap: 6px;
  transition: transform 0.15s, box-shadow 0.2s;
}
.add-btn:hover { transform: translateY(-1px); box-shadow: var(--glow-purple); }
.add-btn__icon { font-size: 18px; line-height: 1; }

/* —— 筛选 —— */
.filters {
  display: flex; align-items: center; gap: 6px;
  padding: 10px 14px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
}
.filter {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex; align-items: center; gap: 6px;
}
.filter:hover { color: var(--text); background: rgba(138,99,255,0.06); }
.filter.is-on { color: var(--text-strong); background: rgba(138,99,255,0.16); border-color: var(--border-soft); }
.filter__count {
  font-family: var(--font-num);
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(7,8,26,0.6);
  color: var(--text-muted);
}
.filter.is-on .filter__count { color: var(--neon-cyan); }
.filters__search { margin-left: auto; }
.filters__search input {
  width: 200px;
  height: 32px;
  padding: 0 12px;
  background: transparent;
  border: 1px solid var(--border-faint);
  border-radius: var(--radius);
  color: var(--text);
  font-size: 13px;
}
.filters__search input:focus { outline: none; border-color: var(--neon-purple); }

/* —— 加载/空 —— */
.loading, .empty {
  padding: 40px;
  text-align: center;
  color: var(--text-muted);
  border-radius: var(--radius);
  border: 1px dashed var(--border-faint);
}
.empty__icon { font-size: 36px; margin-bottom: 8px; }
.spinner {
  width: 16px; height: 16px;
  border: 2px solid var(--border-soft);
  border-top-color: var(--neon-cyan);
  border-radius: 50%;
  display: inline-block;
  margin-right: 8px;
  animation: spin 0.8s linear infinite;
  vertical-align: middle;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* —— 卡片网格 —— */
.act-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}
.act-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  overflow: hidden;
  transition: transform 0.2s, border-color 0.2s;
}
.act-card:hover { transform: translateY(-3px); border-color: var(--border-soft); }
.act-card--running { border-color: rgba(255,77,79,0.4); box-shadow: 0 0 16px rgba(255,77,79,0.12); }
.act-card__cover {
  height: 100px;
  position: relative;
  display: flex; align-items: center; justify-content: center;
}
.act-card__status {
  position: absolute; top: 8px; left: 8px;
  padding: 3px 8px;
  border-radius: 4px;
  background: rgba(7,8,26,0.7);
  color: var(--text);
  font-size: 11px;
  font-weight: 600;
  backdrop-filter: blur(4px);
}
.act-card--running .act-card__status { background: var(--danger); color: #fff; }
.act-card--pending .act-card__status { background: var(--warning); color: #07081a; }
.act-card--ended .act-card__status { background: var(--text-dim); }
.act-card--cancelled .act-card__status { background: rgba(86,88,122,0.6); }
.act-card__discount {
  font-family: var(--font-num);
  font-size: 32px;
  font-weight: 900;
  color: #fff;
  text-shadow: 0 0 12px rgba(255,255,255,0.6);
}
.act-card__body { padding: 14px 16px; }
.act-card__name {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 15px;
  color: var(--text-strong);
  line-height: 1.4;
}
.act-card__price { display: flex; align-items: baseline; gap: 8px; margin-bottom: 8px; }
.act-card__price .now { font-family: var(--font-num); font-size: 22px; font-weight: 700; color: var(--danger); }
.act-card__price .orig { font-size: 12px; color: var(--text-dim); text-decoration: line-through; }
.act-card__stock { margin-bottom: 8px; }
.act-card__meta { font-size: 11px; color: var(--text-muted); margin-bottom: 6px; font-family: var(--font-num); }
.act-card__stats { font-size: 11px; color: var(--text-muted); margin-bottom: 12px; display: flex; gap: 4px; }
.act-card__stats .revenue { color: var(--warning); font-family: var(--font-num); }
.act-card__actions { display: flex; gap: 6px; flex-wrap: wrap; }
.op {
  padding: 5px 10px;
  border-radius: 6px;
  border: 1px solid var(--border-faint);
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.op:hover { color: var(--text-strong); border-color: var(--border-strong); }
.op--start { color: var(--success); border-color: rgba(82,229,164,0.3); }
.op--start:hover { background: rgba(82,229,164,0.1); }
.op--stop { color: var(--warning); border-color: rgba(255,203,85,0.3); }
.op--stop:hover { background: rgba(255,203,85,0.1); }
.op--edit { color: var(--neon-cyan); border-color: rgba(0,229,255,0.3); }
.op--edit:hover { background: rgba(0,229,255,0.1); }
.op--relaunch { color: var(--neon-purple); border-color: rgba(138,99,255,0.3); }
.op--relaunch:hover { background: rgba(138,99,255,0.1); }
.op--del { color: var(--danger); border-color: rgba(255,84,112,0.3); }
.op--del:hover { background: rgba(255,84,112,0.1); }

/* —— 弹窗 —— */
.dlg-mask {
  position: fixed; inset: 0; z-index: var(--z-modal);
  background: rgba(7,8,26,0.7);
  backdrop-filter: blur(6px);
  display: grid; place-items: center;
}
.dlg {
  width: 480px;
  max-width: 92vw;
  max-height: 90vh;
  overflow-y: auto;
  padding: 22px 24px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-soft);
}
.dlg__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.dlg__head h3 { margin: 0; font-family: var(--font-display); font-size: 18px; color: var(--text-strong); }
.dlg__close {
  width: 28px; height: 28px;
  border-radius: 50%;
  border: none;
  background: var(--bg-card);
  color: var(--text-muted);
  font-size: 18px;
  cursor: pointer;
  transition: background 0.2s;
}
.dlg__close:hover { background: rgba(255,84,112,0.2); color: var(--danger); }
.dlg__form { display: flex; flex-direction: column; gap: 12px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field__label { font-size: 12px; color: var(--text-muted); letter-spacing: 0.05em; }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.field input[type="text"],
.field input[type="number"],
.field input[type="datetime-local"] {
  height: 36px;
  padding: 0 12px;
  background: rgba(7,8,26,0.6);
  border: 1px solid var(--border-faint);
  border-radius: var(--radius);
  color: var(--text);
  font-size: 13px;
  font-family: var(--font-body);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.field input:focus {
  outline: none;
  border-color: var(--neon-purple);
  box-shadow: 0 0 0 3px var(--neon-purple-soft);
}
.cover-picker { display: flex; gap: 8px; flex-wrap: wrap; }
.cover-item {
  width: 44px; height: 26px;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s;
}
.cover-item:hover { transform: scale(1.06); }
.cover-item.is-on { border-color: var(--neon-cyan); box-shadow: 0 0 8px var(--neon-cyan-soft); }
.dlg__actions { display: flex; gap: 10px; margin-top: 8px; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
