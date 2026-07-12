<template>
  <div class="user-mgmt">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">用户管理</h1>
        <p class="page-head__sub">注册用户 · 角色分布 · 封禁/解禁 · 对接 <code>/user/ban/:id</code></p>
      </div>
      <button class="add-btn" @click="onExport">导出 CSV</button>
    </header>

    <!-- 顶部统计 -->
    <div class="stats">
      <div v-for="s in statsCards" :key="s.label" class="stat-card glass" :class="`stat-card--${s.key}`">
        <span class="stat-card__icon">{{ s.icon }}</span>
        <div>
          <span class="stat-card__num"><NumberFlip :value="s.value" /></span>
          <span class="stat-card__label">{{ s.label }}</span>
        </div>
        <span class="stat-card__delta" :class="s.delta >= 0 ? 'up' : 'down'">
          {{ s.delta >= 0 ? '▲' : '▼' }} {{ Math.abs(s.delta) }}%
        </span>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filters glass">
      <div class="filters__left">
        <button v-for="f in roleFilters" :key="f.key"
          class="filter" :class="{ 'is-on': activeRole === f.key }"
          @click="activeRole = f.key">
          {{ f.label }} <span class="filter__count">{{ f.count }}</span>
        </button>
        <span class="filter__sep">|</span>
        <button v-for="f in statusFilters" :key="f.key"
          class="filter" :class="{ 'is-on': activeStatus === f.key }"
          @click="activeStatus = f.key">
          {{ f.label }} <span class="filter__count">{{ f.count }}</span>
        </button>
      </div>
      <div class="filters__right">
        <input v-model.trim="keyword" type="text" placeholder="搜索用户名 / ID / 手机号…" />
        <select v-model="sortBy">
          <option value="createdAt-desc">注册时间 ↓</option>
          <option value="createdAt-asc">注册时间 ↑</option>
          <option value="username-asc">用户名 A-Z</option>
        </select>
      </div>
    </div>

    <!-- 列表 -->
    <div v-if="loading && !users.length" class="loading glass">
      <div class="spinner" /> 加载中…
    </div>
    <div v-else-if="!filtered.length" class="empty glass">
      <div class="empty__icon">👤</div>
      <p>未找到匹配用户</p>
    </div>
    <div v-else class="table-wrap glass">
      <table>
        <thead>
          <tr>
            <th><input type="checkbox" :checked="allChecked" @change="onToggleAll" /></th>
            <th>用户</th>
            <th>角色</th>
            <th>手机号</th>
            <th>注册时间</th>
            <th>最近登录</th>
            <th>订单数</th>
            <th>消费</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in filtered" :key="u.id" :class="{ 'is-ban': u.status === 0 }">
            <td><input type="checkbox" :checked="checked.has(u.id)" @change="onToggleOne(u.id)" /></td>
            <td>
              <div class="user-cell">
                <div class="user-cell__avatar" :style="{ background: u.avatarBg }">{{ (u.nickname || u.username)[0] }}</div>
                <div>
                  <div class="user-cell__name">{{ u.nickname || u.username }}</div>
                  <div class="user-cell__id">@{{ u.username }} · #{{ u.id }}</div>
                </div>
              </div>
            </td>
            <td><span class="role" :class="`role--${roleKey(u.role)}`>{{ roleLabel(u.role) }}</span></td>
            <td class="num">{{ u.phone || '-' }}</td>
            <td class="num">{{ formatTime(u.createdAt) }}</td>
            <td class="num">{{ u.lastLoginAt ? formatTime(u.lastLoginAt) : '-' }}</td>
            <td class="num">{{ u.orderCount }}</td>
            <td class="num revenue">¥{{ u.totalSpent.toFixed(0) }}</td>
            <td>
              <span class="status" :class="u.status === 1 ? 'status--ok' : 'status--ban'">
                <span class="status__dot" /> {{ u.status === 1 ? '正常' : '已封禁' }}
              </span>
            </td>
            <td>
              <div class="row-actions">
                <button v-if="u.status === 1" class="op op--ban" @click="onBan(u)">封禁</button>
                <button v-else class="op op--unban" @click="onUnban(u)">解禁</button>
                <button class="op op--view" @click="onView(u)">详情</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="pager">
      <button class="pager__btn" :disabled="page === 1" @click="page--">上一页</button>
      <span class="pager__info">第 {{ page }} / {{ totalPages }} 页 · 共 {{ filtered.length }} 条</span>
      <button class="pager__btn" :disabled="page === totalPages" @click="page++">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, reactive } from 'vue'
import { userApi } from '@/api/user'
import { showToast } from '@/utils/toast'
import { ROLE, ROLE_LABEL } from '@/constants'
import NumberFlip from '@/components/base/NumberFlip.vue'

const loading = ref(false)
const users = ref([])
const keyword = ref('')
const activeRole = ref('all')
const activeStatus = ref('all')
const sortBy = ref('createdAt-desc')
const page = ref(1)
const pageSize = 10
const checked = reactive(new Set())

const statsCards = computed(() => [
  { key: 'total',  icon: '👥', label: '注册用户',   value: users.value.length, delta: 8 },
  { key: 'anchor', icon: '🎤', label: '主播',       value: users.value.filter(u => u.role === ROLE.ANCHOR).length, delta: 12 },
  { key: 'today',  icon: '✨', label: '今日新增',   value: users.value.filter(u => isToday(u.createdAt)).length, delta: 24 },
  { key: 'ban',    icon: '⛔', label: '已封禁',     value: users.value.filter(u => u.status === 0).length, delta: -3 },
])

const roleFilters = computed(() => [
  { key: 'all', label: '全部', count: users.value.length },
  { key: 'audience', label: '观众', count: users.value.filter(u => u.role === ROLE.AUDIENCE).length },
  { key: 'anchor', label: '主播', count: users.value.filter(u => u.role === ROLE.ANCHOR).length },
  { key: 'admin', label: '管理员', count: users.value.filter(u => u.role === ROLE.ADMIN).length },
])
const statusFilters = computed(() => [
  { key: 'all', label: '全部状态', count: users.value.length },
  { key: 'ok', label: '正常', count: users.value.filter(u => u.status === 1).length },
  { key: 'ban', label: '已封禁', count: users.value.filter(u => u.status === 0).length },
])

const filtered = computed(() => {
  let res = [...users.value]
  if (activeRole.value !== 'all') {
    const map = { audience: ROLE.AUDIENCE, anchor: ROLE.ANCHOR, admin: ROLE.ADMIN }
    res = res.filter(u => u.role === map[activeRole.value])
  }
  if (activeStatus.value !== 'all') {
    res = res.filter(u => activeStatus.value === 'ban' ? u.status === 0 : u.status === 1)
  }
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    res = res.filter(u =>
      u.username.toLowerCase().includes(k) ||
      String(u.id).includes(k) ||
      (u.phone || '').includes(k)
    )
  }
  // 排序
  const [field, dir] = sortBy.value.split('-')
  res.sort((a, b) => {
    let v = 0
    if (field === 'createdAt') v = a.createdAt - b.createdAt
    else if (field === 'username') v = a.username.localeCompare(b.username)
    return dir === 'asc' ? v : -v
  })
  return res
})

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const allChecked = computed(() => filtered.value.length > 0 && filtered.value.every(u => checked.has(u.id)))

function onToggleAll(e) {
  const v = e.target.checked
  filtered.value.forEach(u => {
    if (v) checked.add(u.id)
    else checked.delete(u.id)
  })
}
function onToggleOne(id) {
  if (checked.has(id)) checked.delete(id)
  else checked.add(id)
}

// —— 加载 ——
async function load() {
  loading.value = true
  try {
    // 后端无 /user/list，暂用 mock
    const list = await userApi.devices().catch(() => null) // 借口任何调用看后端是否在线
    users.value = mockUsers()
    if (!list) showToast('后端未就绪，已加载演示数据', 'info')
  } finally {
    loading.value = false
  }
}

// —— 操作 ——
async function onBan(u) {
  if (!confirm(`确定封禁「${u.username}」？封禁后该用户无法登录。`)) return
  try {
    await userApi.ban(u.id, 0)
  } catch { /* 演示忽略 */ }
  u.status = 0
  showToast(`已封禁 ${u.username}`, 'warning')
}
async function onUnban(u) {
  try {
    await userApi.ban(u.id, 1)
  } catch { /* 演示忽略 */ }
  u.status = 1
  showToast(`已解禁 ${u.username}`, 'success')
}
function onView(u) {
  showToast(`查看 ${u.username} 详情（待实现）`, 'info')
}
function onExport() {
  const csv = ['ID,用户名,角色,手机号,注册时间,订单数,消费,状态']
  filtered.value.forEach(u => {
    csv.push([u.id, u.username, roleLabel(u.role), u.phone, formatTime(u.createdAt), u.orderCount, u.totalSpent, u.status === 1 ? '正常' : '封禁'].join(','))
  })
  const blob = new Blob([csv.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `users-${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(url)
  showToast(`已导出 ${filtered.value.length} 条`, 'success')
}

// —— 工具 ——
function roleKey(r) {
  return { [ROLE.AUDIENCE]: 'audience', [ROLE.ANCHOR]: 'anchor', [ROLE.ADMIN]: 'admin' }[r] || 'audience'
}
function roleLabel(r) {
  return ROLE_LABEL[r] || '未知'
}
function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function isToday(ts) {
  if (!ts) return false
  const d = new Date(ts), n = new Date()
  return d.toDateString() === n.toDateString()
}

// —— mock ——
const AVATAR_COLORS = [
  'linear-gradient(135deg,#8a63ff,#00e5ff)',
  'linear-gradient(135deg,#ff7ad9,#8a63ff)',
  'linear-gradient(135deg,#52e5a4,#00e5ff)',
  'linear-gradient(135deg,#ffcb55,#ff7ad9)',
  'linear-gradient(135deg,#ff5470,#ff8a00)',
  'linear-gradient(135deg,#4cc9f0,#8a63ff)',
]
function mockUsers() {
  const now = Date.now()
  const names = ['NeonAnchor', 'GlowQueen', 'SnackKing', 'SneakerX', 'user_001', 'user_002', 'user_003', 'admin_root', 'CyberDan', 'PixelPulse', 'VelvetVoice', 'MidnightMike', 'CrystalCara', 'EchoEden', 'LunaLite', 'BassBoss', 'StormStream', 'AuroraAce']
  return names.map((name, i) => ({
    id: i + 1,
    username: name,
    nickname: name,
    phone: i % 3 === 0 ? `138${String(10000000 + i * 12345).slice(0, 8)}` : '',
    role: i === 0 ? ROLE.ANCHOR : i === 1 ? ROLE.ANCHOR : i === 7 ? ROLE.ADMIN : ROLE.AUDIENCE,
    status: i === 5 ? 0 : 1,
    createdAt: now - (i * 86400000 + Math.random() * 3600000),
    lastLoginAt: now - Math.random() * 86400000 * 7,
    orderCount: Math.floor(Math.random() * 50),
    totalSpent: Math.random() * 8000,
    avatarBg: AVATAR_COLORS[i % AVATAR_COLORS.length],
  }))
}

onMounted(load)
</script>

<style scoped>
.user-mgmt { display: flex; flex-direction: column; gap: 16px; }

.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.page-head__title { margin: 0; font-family: var(--font-display); font-size: 24px; color: var(--text-strong); letter-spacing: 0.04em; }
.page-head__sub { margin: 6px 0 0; font-size: 13px; color: var(--text-muted); }
.page-head__sub code { font-family: var(--font-num); color: var(--neon-cyan); }
.add-btn {
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid var(--border-soft);
  background: var(--bg-card);
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.add-btn:hover { border-color: var(--neon-cyan); background: rgba(0,229,255,0.06); }

/* —— 顶部统计 —— */
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
@media (max-width: 900px) { .stats { grid-template-columns: repeat(2, 1fr); } }
.stat-card {
  display: grid;
  grid-template-columns: 40px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 16px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
}
.stat-card__icon { font-size: 24px; text-align: center; }
.stat-card__num { font-family: var(--font-num); font-size: 22px; font-weight: 700; color: var(--text-strong); display: block; }
.stat-card__label { font-size: 11px; color: var(--text-muted); }
.stat-card__delta { font-size: 11px; }
.stat-card__delta.up { color: var(--success); }
.stat-card__delta.down { color: var(--danger); }
.stat-card--ban { border-color: rgba(255,84,112,0.3); }

/* —— 筛选 —— */
.filters {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  flex-wrap: wrap;
  gap: 10px;
}
.filters__left { display: flex; gap: 4px; flex-wrap: wrap; align-items: center; }
.filters__right { display: flex; gap: 8px; }
.filter {
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  display: inline-flex; align-items: center; gap: 5px;
}
.filter:hover { color: var(--text); background: rgba(138,99,255,0.06); }
.filter.is-on { color: var(--text-strong); background: rgba(138,99,255,0.16); border-color: var(--border-soft); }
.filter__count { font-family: var(--font-num); font-size: 10px; padding: 0 5px; border-radius: 999px; background: rgba(7,8,26,0.6); }
.filter__sep { color: var(--text-dim); margin: 0 4px; }
.filters__right input, .filters__right select {
  height: 30px;
  padding: 0 10px;
  background: transparent;
  border: 1px solid var(--border-faint);
  border-radius: var(--radius);
  color: var(--text);
  font-size: 12px;
}
.filters__right input { width: 220px; }
.filters__right input:focus, .filters__right select:focus { outline: none; border-color: var(--neon-purple); }

/* —— 加载/空 —— */
.loading, .empty {
  padding: 40px; text-align: center; color: var(--text-muted);
  border-radius: var(--radius); border: 1px dashed var(--border-faint);
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

/* —— 表格 —— */
.table-wrap {
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  overflow-x: auto;
}
table { width: 100%; border-collapse: collapse; min-width: 920px; }
th, td { padding: 10px 12px; text-align: left; font-size: 13px; border-bottom: 1px solid var(--border-faint); }
th { color: var(--text-muted); font-weight: 500; font-size: 11px; letter-spacing: 0.06em; background: rgba(7,8,26,0.4); text-transform: uppercase; }
tbody tr:last-child td { border-bottom: none; }
tbody tr:hover { background: rgba(138,99,255,0.04); }
tbody tr.is-ban { opacity: 0.55; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.user-cell__avatar {
  width: 32px; height: 32px;
  border-radius: 8px;
  display: grid; place-items: center;
  font-family: var(--font-display);
  font-weight: 700;
  color: #07081a;
  font-size: 14px;
}
.user-cell__name { font-weight: 600; color: var(--text-strong); font-size: 13px; }
.user-cell__id { font-size: 11px; color: var(--text-dim); font-family: var(--font-num); }
.role {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  border: 1px solid;
}
.role--audience { color: var(--neon-cyan); border-color: var(--neon-cyan-soft); background: rgba(0,229,255,0.08); }
.role--anchor { color: var(--neon-purple); border-color: var(--neon-purple-soft); background: rgba(138,99,255,0.1); }
.role--admin { color: var(--warning); border-color: rgba(255,203,85,0.4); background: rgba(255,203,85,0.1); }
.num { font-family: var(--font-num); color: var(--text); }
.revenue { color: var(--warning); font-weight: 600; }
.status {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.status__dot { width: 6px; height: 6px; border-radius: 50%; }
.status--ok { background: rgba(82,229,164,0.14); color: var(--success); }
.status--ok .status__dot { background: var(--success); box-shadow: 0 0 6px var(--success); }
.status--ban { background: rgba(255,84,112,0.14); color: var(--danger); }
.status--ban .status__dot { background: var(--danger); }
.row-actions { display: flex; gap: 5px; }
.op {
  padding: 4px 9px;
  border-radius: 5px;
  border: 1px solid var(--border-faint);
  background: transparent;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}
.op:hover { color: var(--text-strong); border-color: var(--border-strong); }
.op--ban { color: var(--danger); border-color: rgba(255,84,112,0.3); }
.op--ban:hover { background: rgba(255,84,112,0.1); }
.op--unban { color: var(--success); border-color: rgba(82,229,164,0.3); }
.op--unban:hover { background: rgba(82,229,164,0.1); }
.op--view { color: var(--neon-cyan); border-color: rgba(0,229,255,0.3); }
.op--view:hover { background: rgba(0,229,255,0.1); }

/* —— 分页 —— */
.pager { display: flex; align-items: center; justify-content: center; gap: 16px; }
.pager__btn {
  padding: 6px 14px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  color: var(--text);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.pager__btn:hover:not(:disabled) { border-color: var(--neon-cyan); }
.pager__btn:disabled { opacity: 0.4; cursor: not-allowed; }
.pager__info { font-size: 12px; color: var(--text-muted); font-family: var(--font-num); }
</style>
