<template>
  <div class="profile">
    <!-- 顶部资料横幅 -->
    <header class="hero glass">
      <div class="hero__bg" aria-hidden="true">
        <div class="hero__grid" />
        <div class="hero__scan" />
      </div>
      <div class="hero__main">
        <div class="hero__avatar" :style="{ background: avatarBg }">
          <img v-if="form.avatar" :src="form.avatar" alt="" />
          <span v-else>{{ (userStore.nickname || 'U').slice(0, 1) }}</span>
          <button class="hero__avatar-edit" @click="onPickAvatar" title="更换头像">✎</button>
        </div>
        <div class="hero__info">
          <div class="hero__name-line">
            <h1 class="hero__name">{{ userStore.nickname || '未登录' }}</h1>
            <span class="hero__role" :class="`role--${roleKey}`">{{ roleLabel }}</span>
            <span v-if="userStore.userInfo?.status === 0" class="hero__ban">已封禁</span>
          </div>
          <p class="hero__meta">
            <span>@{{ userStore.userInfo?.username || '-' }}</span>
            <span class="dot">·</span>
            <span>ID #{{ userStore.userInfo?.id ?? '-' }}</span>
            <span class="dot">·</span>
            <span>{{ userStore.userInfo?.phone || '未绑定手机' }}</span>
          </p>
          <p class="hero__join">加入 LiveMall 第 {{ joinDays }} 天</p>
        </div>
      </div>
    </header>

    <!-- 主体两栏 -->
    <div class="profile__body">
      <!-- 左：编辑资料 + 改密 -->
      <section class="col col--left">
        <div class="panel glass">
          <div class="panel__head">
            <h3>资料编辑</h3>
            <span class="panel__hint">展示在弹幕与排行榜</span>
          </div>
          <form class="form" @submit.prevent="onSaveProfile">
            <label class="field">
              <span class="field__label">昵称</span>
              <input v-model.trim="form.nickname" type="text" maxlength="20" placeholder="给自己起个昵称" />
            </label>
            <label class="field">
              <span class="field__label">头像 URL</span>
              <input v-model.trim="form.avatar" type="url" placeholder="https://…" />
            </label>
            <label class="field">
              <span class="field__label">手机号</span>
              <input v-model.trim="form.phone" type="tel" maxlength="11" placeholder="11 位手机号" />
            </label>
            <div class="form__actions">
              <NeonButton type="submit" :loading="savingProfile">保存资料</NeonButton>
              <NeonButton variant="ghost" type="button" @click="resetForm">重置</NeonButton>
            </div>
          </form>
        </div>

        <div class="panel glass">
          <div class="panel__head">
            <h3>修改密码</h3>
            <span class="panel__hint">建议定期更换</span>
          </div>
          <form class="form" @submit.prevent="onChangePassword">
            <label class="field">
              <span class="field__label">当前密码</span>
              <input v-model="pwd.old" type="password" autocomplete="current-password" placeholder="••••••" required />
            </label>
            <label class="field">
              <span class="field__label">新密码</span>
              <input v-model="pwd.next" type="password" autocomplete="new-password" placeholder="至少 6 位" required />
              <span class="field__strength" :class="`s-${strength}`">强度：{{ strengthLabel }}</span>
            </label>
            <label class="field">
              <span class="field__label">确认新密码</span>
              <input v-model="pwd.confirm" type="password" autocomplete="new-password" placeholder="再次输入" required />
            </label>
            <div class="form__actions">
              <NeonButton type="submit" :loading="savingPwd">更新密码</NeonButton>
            </div>
          </form>
        </div>
      </section>

      <!-- 右：订单概览 + 角色权限 -->
      <aside class="col col--right">
        <div class="panel glass">
          <div class="panel__head">
            <h3>订单概览</h3>
            <RouterLink to="/orders" class="panel__link">全部 →</RouterLink>
          </div>
          <div class="stats">
            <RouterLink v-for="s in orderStats" :key="s.key" :to="s.to" class="stat" :class="`stat--${s.key}`">
              <span class="stat__num"><NumberFlip :value="s.count" /></span>
              <span class="stat__label">{{ s.label }}</span>
            </RouterLink>
          </div>
        </div>

        <div class="panel glass">
          <div class="panel__head">
            <h3>角色权限</h3>
            <span class="panel__hint">{{ roleLabel }}</span>
          </div>
          <ul class="perms">
            <li v-for="p in perms" :key="p.label" :class="{ 'is-on': p.on }">
              <span class="perms__icon">{{ p.on ? '✓' : '×' }}</span>
              <span>{{ p.label }}</span>
            </li>
          </ul>
          <button v-if="!userStore.isAnchor" class="upgrade" @click="onUpgradeRole">
            <span>切换为主播角色 →</span>
          </button>
        </div>

        <div class="panel glass">
          <div class="panel__head">
            <h3>账号安全</h3>
          </div>
          <ul class="sec-list">
            <li>
              <span>登录设备</span>
              <RouterLink to="/devices" class="sec-list__action">管理 →</RouterLink>
            </li>
            <li>
              <span>账号状态</span>
              <span class="tag" :class="userStore.userInfo?.status === 0 ? 'tag--danger' : 'tag--ok'">
                {{ userStore.userInfo?.status === 0 ? '已封禁' : '正常' }}
              </span>
            </li>
          </ul>
          <button class="logout" @click="onLogout">退出登录</button>
        </div>
      </aside>
    </div>

    <!-- 头像选择浮层 -->
    <transition name="modal">
      <div v-if="avatarPicker" class="picker-mask" @click.self="avatarPicker = false">
        <div class="picker glass">
          <h3>选择头像色卡</h3>
          <p class="picker__hint">演示模式：从预设色卡选一个作为头像背景</p>
          <div class="picker__grid">
            <button v-for="(c, i) in AVATAR_COLORS" :key="i"
              class="picker__item" :style="{ background: c }"
              @click="onSelectAvatar(c)" />
          </div>
          <NeonButton variant="ghost" @click="avatarPicker = false">取消</NeonButton>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { orderStore } from '@/stores/order'
import { showToast } from '@/utils/toast'
import { ROLE, ROLE_LABEL, ORDER_STATUS } from '@/constants'
import NeonButton from '@/components/base/NeonButton.vue'
import NumberFlip from '@/components/base/NumberFlip.vue'

const router = useRouter()
const userStore = useUserStore()

// —— 头像配色（演示用） ——
const AVATAR_COLORS = [
  'linear-gradient(135deg,#8a63ff,#00e5ff)',
  'linear-gradient(135deg,#ff7ad9,#8a63ff)',
  'linear-gradient(135deg,#52e5a4,#00e5ff)',
  'linear-gradient(135deg,#ffcb55,#ff7ad9)',
  'linear-gradient(135deg,#ff5470,#ff8a00)',
  'linear-gradient(135deg,#4cc9f0,#8a63ff)',
  'linear-gradient(135deg,#ffd666,#ff5470)',
  'linear-gradient(135deg,#12132a,#8a63ff)',
]
const avatarBg = ref(AVATAR_COLORS[0])
const avatarPicker = ref(false)
function onPickAvatar() { avatarPicker.value = true }
function onSelectAvatar(c) {
  avatarBg.value = c
  form.avatar = '' // 清空 URL，使用色卡
  avatarPicker.value = false
  showToast('已选色卡，记得点保存资料', 'info')
}

// —— 资料表单 ——
const form = reactive({
  nickname: '',
  avatar: '',
  phone: '',
})
const savingProfile = ref(false)

function syncForm() {
  const u = userStore.userInfo || {}
  form.nickname = u.nickname || u.username || ''
  form.avatar = u.avatar || ''
  form.phone = u.phone || ''
  if (!form.avatar) avatarBg.value = AVATAR_COLORS[(u.id || 0) % AVATAR_COLORS.length]
}
function resetForm() { syncForm(); showToast('已重置', 'info') }

async function onSaveProfile() {
  if (!form.nickname) { showToast('昵称不能为空', 'warning'); return }
  if (form.phone && !/^1\d{10}$/.test(form.phone)) { showToast('手机号格式不对', 'warning'); return }
  savingProfile.value = true
  try {
    await userStore.updateProfile({
      nickname: form.nickname,
      avatar: form.avatar,
      phone: form.phone,
    })
    showToast('资料已更新', 'success')
  } catch (e) {
    showToast('保存失败：' + (e?.message || '请稍后重试'), 'danger')
  } finally {
    savingProfile.value = false
  }
}

// —— 改密 ——
const pwd = reactive({ old: '', next: '', confirm: '' })
const savingPwd = ref(false)
const strength = computed(() => {
  const p = pwd.next
  if (!p) return 0
  let s = 0
  if (p.length >= 6) s++
  if (p.length >= 10) s++
  if (/[A-Z]/.test(p) && /[a-z]/.test(p)) s++
  if (/\d/.test(p) && /[^a-zA-Z0-9]/.test(p)) s++
  return Math.min(4, s)
})
const strengthLabel = computed(() => ['—', '弱', '一般', '较强', '强'][strength.value] || '—')

async function onChangePassword() {
  if (pwd.next.length < 6) { showToast('新密码至少 6 位', 'warning'); return }
  if (pwd.next !== pwd.confirm) { showToast('两次密码不一致', 'warning'); return }
  if (pwd.next === pwd.old) { showToast('新密码不能与旧密码相同', 'warning'); return }
  savingPwd.value = true
  try {
    // 演示模式：调用 userApi.updatePassword，失败则提示
    const { userApi } = await import('@/api/user')
    await userApi.updatePassword({ oldPassword: pwd.old, newPassword: pwd.next })
    showToast('密码已更新，请重新登录', 'success')
    pwd.old = ''; pwd.next = ''; pwd.confirm = ''
    setTimeout(() => { userStore.logout(); router.replace('/login') }, 1500)
  } catch (e) {
    showToast('改密失败：' + (e?.message || '后端未就绪，演示跳过'), 'danger')
  } finally {
    savingPwd.value = false
  }
}

// —— 订单概览 ——
const orderStats = computed(() => {
  const all = orderStore.list()
  return [
    { key: 'pending', label: '待支付', count: all.filter(o => o.status === ORDER_STATUS.PENDING).length, to: '/orders?status=0' },
    { key: 'paid',    label: '已支付', count: all.filter(o => o.status === ORDER_STATUS.PAID).length,    to: '/orders?status=1' },
    { key: 'cancel',  label: '已取消', count: all.filter(o => o.status === ORDER_STATUS.CANCELLED).length, to: '/orders?status=2' },
    { key: 'refund',  label: '已退款', count: all.filter(o => o.status === ORDER_STATUS.REFUNDED).length, to: '/orders?status=3' },
  ]
})

// —— 角色权限 ——
const roleKey = computed(() => {
  const r = userStore.role
  if (r === ROLE.ANCHOR) return 'anchor'
  if (r === ROLE.ADMIN) return 'admin'
  return 'audience'
})
const roleLabel = computed(() => ROLE_LABEL[userStore.role] || '游客')
const perms = computed(() => {
  const r = userStore.role
  const base = [
    { label: '观看直播 / 发弹幕', on: true },
    { label: '参与秒杀 / 下单', on: true },
    { label: '送礼物 / 上榜', on: true },
  ]
  if (r === ROLE.AUDIENCE) {
    return [
      ...base,
      { label: '开播推流', on: false },
      { label: '管理活动', on: false },
      { label: '后台管理', on: false },
    ]
  }
  if (r === ROLE.ANCHOR) {
    return [
      ...base,
      { label: '开播推流', on: true },
      { label: '管理活动', on: true },
      { label: '后台管理', on: false },
    ]
  }
  return [
    ...base,
    { label: '开播推流', on: true },
    { label: '管理活动', on: true },
    { label: '后台管理', on: true },
  ]
})

function onUpgradeRole() {
  // 演示：直接切换角色码
  const u = userStore.userInfo
  if (!u) { showToast('请先登录', 'warning'); return }
  u.role = ROLE.ANCHOR
  localStorage.setItem('lm_user', JSON.stringify(u))
  showToast('已切换为主播，刷新后可见「主播台」入口', 'success')
  setTimeout(() => location.reload(), 1200)
}

// —— 退出 ——
async function onLogout() {
  await userStore.logout()
  router.replace('/login')
}

// —— 加入天数（演示：基于 userInfo.id 哈希出 1~365，没有真实 createdAt） ——
const joinDays = computed(() => {
  const id = userStore.userInfo?.id ?? 1
  return ((id * 37) % 365) + 1
})

onMounted(() => {
  syncForm()
  if (!userStore.userInfo) {
    // 尝试拉一次
    userStore.fetchProfile().then(syncForm).catch(() => {})
  }
})
</script>

<style scoped>
.profile { display: flex; flex-direction: column; gap: 20px; }

/* —— 顶部 Hero —— */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-lg);
  padding: 32px 28px;
  border: 1px solid var(--border-soft);
}
.hero__bg { position: absolute; inset: 0; z-index: 0; pointer-events: none; }
.hero__grid {
  position: absolute; inset: -50%;
  background-image:
    linear-gradient(rgba(138,99,255,0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(138,99,255,0.08) 1px, transparent 1px);
  background-size: 32px 32px;
  transform: perspective(400px) rotateX(60deg) translateY(-20%);
  transform-origin: center top;
  opacity: 0.6;
}
.hero__scan {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, transparent 0%, rgba(0,229,255,0.06) 50%, transparent 100%);
  animation: hero-scan 4s linear infinite;
}
@keyframes hero-scan {
  0% { transform: translateY(-100%); }
  100% { transform: translateY(100%); }
}
.hero__main { position: relative; z-index: 1; display: flex; align-items: center; gap: 24px; }
.hero__avatar {
  width: 88px; height: 88px;
  border-radius: 24px;
  display: grid; place-items: center;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 36px;
  color: #07081a;
  position: relative;
  border: 2px solid var(--border-strong);
  box-shadow: var(--glow-purple);
  overflow: hidden;
}
.hero__avatar img { width: 100%; height: 100%; object-fit: cover; }
.hero__avatar-edit {
  position: absolute; right: -6px; bottom: -6px;
  width: 28px; height: 28px;
  border-radius: 50%;
  background: var(--neon-cyan);
  color: #07081a;
  border: 2px solid var(--bg-800);
  font-size: 14px;
  cursor: pointer;
  display: grid; place-items: center;
  transition: transform 0.2s;
}
.hero__avatar-edit:hover { transform: scale(1.15); }
.hero__info { flex: 1; min-width: 0; }
.hero__name-line { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hero__name {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-strong);
  margin: 0;
  letter-spacing: 0.02em;
}
.hero__role {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
}
.role--audience { color: var(--neon-cyan); border-color: var(--neon-cyan-soft); background: rgba(0,229,255,0.08); }
.role--anchor   { color: var(--neon-purple); border-color: var(--neon-purple-soft); background: rgba(138,99,255,0.1); }
.role--admin    { color: var(--warning); border-color: rgba(255,203,85,0.4); background: rgba(255,203,85,0.1); }
.hero__ban { color: var(--danger); font-size: 12px; font-weight: 600; }
.hero__meta {
  margin: 8px 0 4px;
  color: var(--text-muted);
  font-size: 13px;
  display: flex; gap: 8px; flex-wrap: wrap;
}
.hero__meta .dot { opacity: 0.5; }
.hero__join { margin: 0; color: var(--text-dim); font-size: 12px; }

/* —— 主体两栏 —— */
.profile__body {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
}
@media (max-width: 1024px) {
  .profile__body { grid-template-columns: 1fr; }
}
.col { display: flex; flex-direction: column; gap: 20px; }

.panel {
  border-radius: var(--radius-lg);
  padding: 20px 22px;
  border: 1px solid var(--border-faint);
  background: var(--bg-card);
  backdrop-filter: blur(10px);
}
.panel__head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px;
}
.panel__head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 16px;
  letter-spacing: 0.08em;
  color: var(--text-strong);
}
.panel__hint { font-size: 12px; color: var(--text-dim); }
.panel__link { font-size: 12px; color: var(--neon-cyan); text-decoration: none; }
.panel__link:hover { text-decoration: underline; }

/* —— 表单 —— */
.form { display: flex; flex-direction: column; gap: 14px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field__label {
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}
.field input {
  height: 38px;
  padding: 0 12px;
  background: rgba(7, 8, 26, 0.6);
  border: 1px solid var(--border-faint);
  border-radius: var(--radius);
  color: var(--text);
  font-size: 14px;
  font-family: var(--font-body);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.field input:focus {
  outline: none;
  border-color: var(--neon-purple);
  box-shadow: 0 0 0 3px var(--neon-purple-soft);
}
.field__strength {
  font-size: 11px;
  align-self: flex-end;
}
.s-0 { color: var(--text-dim); }
.s-1 { color: var(--danger); }
.s-2 { color: var(--warning); }
.s-3 { color: var(--info); }
.s-4 { color: var(--success); }
.form__actions { display: flex; gap: 10px; margin-top: 4px; }

/* —— 订单概览 —— */
.stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.stat {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 16px 8px;
  border-radius: var(--radius);
  border: 1px solid var(--border-faint);
  background: rgba(7, 8, 26, 0.4);
  text-decoration: none;
  transition: transform 0.2s, border-color 0.2s, background 0.2s;
}
.stat:hover { transform: translateY(-2px); border-color: var(--border-strong); }
.stat__num {
  font-family: var(--font-num);
  font-size: 24px;
  font-weight: 700;
  color: var(--text-strong);
}
.stat__label { font-size: 12px; color: var(--text-muted); }
.stat--pending .stat__num { color: var(--warning); }
.stat--paid .stat__num    { color: var(--success); }
.stat--cancel .stat__num  { color: var(--text-dim); }
.stat--refund .stat__num  { color: var(--info); }

/* —— 权限列表 —— */
.perms { list-style: none; padding: 0; margin: 0 0 12px; display: flex; flex-direction: column; gap: 8px; }
.perms li {
  display: flex; align-items: center; gap: 10px;
  font-size: 13px;
  color: var(--text-muted);
}
.perms__icon {
  width: 18px; height: 18px;
  display: grid; place-items: center;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
}
.is-on .perms__icon { background: rgba(82,229,164,0.18); color: var(--success); }
.is-on { color: var(--text); }
.perms li:not(.is-on) .perms__icon { background: rgba(86,88,122,0.2); color: var(--text-dim); }
.upgrade {
  width: 100%;
  padding: 10px;
  border-radius: var(--radius);
  border: 1px dashed var(--border-strong);
  background: transparent;
  color: var(--neon-cyan);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}
.upgrade:hover { background: rgba(0,229,255,0.06); }

/* —— 账号安全 —— */
.sec-list { list-style: none; padding: 0; margin: 0 0 12px; display: flex; flex-direction: column; gap: 10px; }
.sec-list li {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px;
  color: var(--text-muted);
}
.sec-list__action { color: var(--neon-cyan); text-decoration: none; font-size: 12px; }
.tag { padding: 2px 8px; border-radius: 999px; font-size: 11px; }
.tag--ok { background: rgba(82,229,164,0.14); color: var(--success); }
.tag--danger { background: rgba(255,84,112,0.14); color: var(--danger); }
.logout {
  width: 100%;
  padding: 10px;
  border-radius: var(--radius);
  border: 1px solid rgba(255,84,112,0.3);
  background: rgba(255,84,112,0.06);
  color: var(--danger);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}
.logout:hover { background: rgba(255,84,112,0.14); }

/* —— 头像选择浮层 —— */
.picker-mask {
  position: fixed; inset: 0; z-index: var(--z-modal);
  background: rgba(7,8,26,0.7);
  backdrop-filter: blur(6px);
  display: grid; place-items: center;
}
.picker {
  width: 380px;
  padding: 24px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-soft);
  display: flex; flex-direction: column; gap: 14px;
}
.picker h3 { margin: 0; font-family: var(--font-display); color: var(--text-strong); }
.picker__hint { margin: 0; font-size: 12px; color: var(--text-dim); }
.picker__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.picker__item {
  aspect-ratio: 1;
  border-radius: var(--radius);
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s;
}
.picker__item:hover { transform: scale(1.08); border-color: var(--neon-cyan); }

.modal-enter-active, .modal-leave-active { transition: opacity 0.25s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
