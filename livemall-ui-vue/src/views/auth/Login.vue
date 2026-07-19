<template>
  <AuthLayout>
    <div class="login">
      <!-- 左：品牌叙事 -->
      <section class="login__hero">
        <div class="hero__badge anim-float-up">LIVE · SECKILL · LEADERBOARD</div>
        <h1 class="hero__title">
          <span class="gradient-text">LiveMall</span>
        </h1>
        <p class="hero__subtitle">
          高并发直播电商秒杀系统 · 把 10w QPS 的架构深度<br />
          压进一个直播间。
        </p>
        <ul class="hero__stack">
          <li v-for="(s, i) in stack" :key="s" class="chip" :style="{ animationDelay: i * 0.06 + 's' }">
            {{ s }}
          </li>
        </ul>
      </section>

      <!-- 右：登录卡 -->
      <section class="login__card glass anim-pop-in">
        <div class="card__glow" aria-hidden="true" />
        <header class="card__head">
          <h2>欢迎回来</h2>
          <p>登录进入直播间</p>
        </header>

        <form class="form" @submit.prevent="onSubmit">
          <label class="field">
            <span class="field__label">用户名</span>
            <div class="field__control" :class="{ 'is-focus': focused === 'username' }">
              <svg viewBox="0 0 24 24" class="field__icon" aria-hidden="true">
                <path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-5 0-9 2.5-9 6v2h18v-2c0-3.5-4-6-9-6Z" fill="currentColor"/>
              </svg>
              <input
                v-model.trim="form.username"
                type="text"
                autocomplete="username"
                placeholder="4 ~ 20 位用户名"
                @focus="focused = 'username'"
                @blur="focused = ''"
              />
            </div>
          </label>

          <label class="field">
            <span class="field__label">密码</span>
            <div class="field__control" :class="{ 'is-focus': focused === 'password' }">
              <svg viewBox="0 0 24 24" class="field__icon" aria-hidden="true">
                <path d="M12 2a5 5 0 0 0-5 5v3H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2h-1V7a5 5 0 0 0-5-5Zm3 8H9V7a3 3 0 0 1 6 0v3Z" fill="currentColor"/>
              </svg>
              <input
                v-model="form.password"
                :type="showPwd ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
                @focus="focused = 'password'"
                @blur="focused = ''"
              />
              <button type="button" class="field__toggle" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
                {{ showPwd ? '隐藏' : '显示' }}
              </button>
            </div>
          </label>

          <NeonButton type="submit" variant="purple" block :loading="userStore.loading" class="form__submit">
            登 录
          </NeonButton>
        </form>

        <p class="card__foot">
          还没账号？<RouterLink to="/register">立即注册 →</RouterLink>
        </p>

        <button type="button" class="demo-btn" @click="demoLogin">
          <span class="demo-btn__dot" /> 演示登录（无后端直进）
        </button>
      </section>

      <!-- 转场光波 -->
      <div v-if="rippling" class="ripple" aria-hidden="true" />
    </div>
  </AuthLayout>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthLayout from '@/layouts/AuthLayout.vue'
import NeonButton from '@/components/base/NeonButton.vue'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'
import { ROLE } from '@/constants'

const stack = ['Java 21 VT', 'Spring Cloud Gateway', 'Dubbo', 'Kafka', 'Redis Lua', 'WebSocket', 'Snowflake', 'Sentinel']

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

const form = reactive({ username: '', password: '' })
const showPwd = ref(false)
const focused = ref('')
const rippling = ref(false)

const HOME_BY_ROLE = {
  [ROLE.AUDIENCE]: '/',
  [ROLE.ANCHOR]: '/anchor/stream',
  [ROLE.ADMIN]: '/admin/dashboard',
}

async function onSubmit() {
  if (!form.username || !form.password) {
    showToast('请输入用户名和密码', 'warning')
    return
  }
  try {
    await userStore.login(form)
    showToast('登录成功', 'success')
    rippling.value = true
    const target = route.query.redirect || HOME_BY_ROLE[userStore.role] || '/'
    setTimeout(() => router.replace(target), 320)
  } catch {
    /* 错误已在拦截器/ store 内提示 */
  }
}

// 演示登录：Mock 模式（VITE_MOCK_MODE=1）下跳过后端，直进直播间
// 通过 userStore 直接设置凭据，不走 authApi（后端未就绪时避免网络错误）
async function demoLogin() {
  userStore.accessToken = 'demo-access'
  userStore.refreshToken = 'demo-refresh'
  userStore.userInfo = {
    id: 0, username: 'demo', nickname: '演示观众',
    avatar: '', role: ROLE.AUDIENCE, status: 1, phone: '',
  }
  // 写 sessionStorage 以便 request.js 拦截器读取 JWT
  sessionStorage.setItem('lm_access', 'demo-access')
  localStorage.setItem('lm_user', JSON.stringify(userStore.userInfo))
  showToast('已进入演示模式', 'success')
  rippling.value = true
  setTimeout(() => router.replace('/'), 320)
}
</script>

<style scoped>
.login {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 56px;
  align-items: center;
}

/* —— 左 · 品牌叙事 —— */
.login__hero { padding-right: 8px; }
.hero__badge {
  display: inline-block;
  font-family: var(--font-num);
  font-size: 12px;
  letter-spacing: 0.32em;
  color: var(--neon-cyan);
  padding: 6px 14px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: var(--bg-card);
  animation: float-up 0.6s var(--ease-out-expo) both;
}
.hero__title {
  font-family: var(--font-num);
  font-weight: 900;
  font-size: clamp(56px, 9vw, 96px);
  line-height: 1;
  margin: 20px 0 18px;
  letter-spacing: 0.02em;
}
.hero__subtitle {
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1.7;
  margin-bottom: 28px;
}
.hero__stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  padding: 6px 12px;
  font-size: 12px;
  font-family: var(--font-display);
  letter-spacing: 0.08em;
  color: var(--text-muted);
  background: var(--bg-card);
  border: 1px solid var(--border-faint);
  border-radius: 999px;
  animation: float-up 0.5s var(--ease-out-expo) both;
}

/* —— 右 · 登录卡 —— */
.login__card {
  position: relative;
  width: 100%;
  max-width: 400px;
  padding: 36px 34px 30px;
  overflow: hidden;
}
.card__glow {
  position: absolute;
  inset: -40% -10% auto -10%;
  height: 200px;
  background: radial-gradient(closest-side, rgba(138, 99, 255, 0.45), transparent 70%);
  filter: blur(20px);
  pointer-events: none;
}
.card__head { position: relative; margin-bottom: 24px; }
.card__head h2 {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.card__head p {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 14px;
}

.form { display: flex; flex-direction: column; gap: 16px; }
.field { display: flex; flex-direction: column; gap: 8px; }
.field__label {
  font-size: 12px;
  letter-spacing: 0.16em;
  color: var(--text-dim);
  text-transform: uppercase;
  font-family: var(--font-display);
}
.field__control {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  height: 46px;
  border-radius: 10px;
  background: rgba(7, 8, 26, 0.6);
  border: 1px solid var(--border-soft);
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}
.field__control.is-focus {
  border-color: var(--neon-purple);
  box-shadow: 0 0 0 3px var(--neon-purple-soft), 0 0 18px var(--neon-purple-soft);
  background: rgba(10, 11, 26, 0.9);
}
.field__icon { width: 18px; height: 18px; color: var(--text-dim); flex-shrink: 0; }
.field__control input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  font-size: 15px;
  color: var(--text-strong);
}
.field__control input::placeholder { color: var(--text-dim); }
.field__toggle {
  font-size: 12px;
  color: var(--text-muted);
  padding: 4px 8px;
  border-radius: 6px;
  transition: color 0.2s, background 0.2s;
}
.field__toggle:hover { color: var(--neon-cyan); background: var(--bg-card); }

.form__submit { margin-top: 8px; height: 46px; font-size: 16px; }

.card__foot {
  margin-top: 22px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}
.demo-btn {
  margin-top: 14px;
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 9px 14px;
  border-radius: 999px;
  background: transparent;
  border: 1px dashed var(--border-strong);
  color: var(--text-muted);
  font-family: var(--font-display);
  font-size: 12px;
  letter-spacing: 0.12em;
  transition: all 0.2s var(--ease-out-expo);
}
.demo-btn:hover {
  color: var(--neon-cyan);
  border-color: var(--neon-cyan);
  box-shadow: 0 0 12px var(--neon-cyan-soft);
}
.demo-btn__dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--neon-cyan);
  box-shadow: 0 0 8px var(--neon-cyan);
}

/* —— 光波转场 —— */
.ripple {
  position: fixed;
  top: 50%; left: 50%;
  width: 60px; height: 60px;
  border-radius: 50%;
  transform: translate(-50%, -50%) scale(0);
  background: radial-gradient(closest-side, var(--neon-purple), transparent 70%);
  z-index: var(--z-modal);
  animation: ripple 0.6s var(--ease-out-expo) forwards;
  pointer-events: none;
}

@media (max-width: 820px) {
  .login { grid-template-columns: 1fr; gap: 24px; }
  .login__hero { text-align: center; }
  .hero__stack { justify-content: center; }
}
</style>
