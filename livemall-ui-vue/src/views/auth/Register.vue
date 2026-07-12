<template>
  <AuthLayout>
    <div class="register">
      <section class="register__card glass anim-pop-in">
        <div class="card__glow" aria-hidden="true" />
        <header class="card__head">
          <RouterLink to="/login" class="back">← 返回登录</RouterLink>
          <h2>创建账号</h2>
          <p>加入 LiveMall，进入直播间</p>
        </header>

        <form class="form" @submit.prevent="onSubmit">
          <label class="field">
            <span class="field__label">用户名</span>
            <div class="field__control" :class="{ 'is-focus': focused === 'username' }">
              <input v-model.trim="form.username" type="text" autocomplete="username"
                placeholder="4 ~ 20 位" @focus="focused='username'" @blur="focused=''" />
            </div>
          </label>

          <label class="field">
            <span class="field__label">密码</span>
            <div class="field__control" :class="{ 'is-focus': focused === 'password' }">
              <input v-model="form.password" type="password" autocomplete="new-password"
                placeholder="至少 8 位" @focus="focused='password'" @blur="focused=''" />
            </div>
          </label>

          <label class="field">
            <span class="field__label">确认密码</span>
            <div class="field__control" :class="{ 'is-focus': focused === 'password2' }">
              <input v-model="form.password2" type="password" autocomplete="new-password"
                placeholder="再次输入" @focus="focused='password2'" @blur="focused=''" />
            </div>
          </label>

          <label class="field">
            <span class="field__label">手机号（可选）</span>
            <div class="field__control" :class="{ 'is-focus': focused === 'phone' }">
              <input v-model.trim="form.phone" type="tel" autocomplete="tel"
                placeholder="11 位手机号" @focus="focused='phone'" @blur="focused=''" />
            </div>
          </label>

          <NeonButton type="submit" variant="cyan" block :loading="loading" class="form__submit">
            注 册
          </NeonButton>
        </form>
      </section>
    </div>
  </AuthLayout>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthLayout from '@/layouts/AuthLayout.vue'
import NeonButton from '@/components/base/NeonButton.vue'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/utils/toast'

const userStore = useUserStore()
const router = useRouter()

const form = reactive({ username: '', password: '', password2: '', phone: '' })
const focused = ref('')
const loading = ref(false)

async function onSubmit() {
  if (!form.username || form.username.length < 4 || form.username.length > 20) {
    return showToast('用户名需 4 ~ 20 位', 'warning')
  }
  if (!form.password || form.password.length < 8) {
    return showToast('密码至少 8 位', 'warning')
  }
  if (form.password !== form.password2) {
    return showToast('两次密码不一致', 'warning')
  }
  loading.value = true
  try {
    await userStore.register({ username: form.username, password: form.password, phone: form.phone || undefined })
    showToast('注册成功，请登录', 'success')
    router.replace('/login')
  } catch {
    /* 错误已提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register { width: 100%; display: flex; justify-content: center; }
.register__card { position: relative; width: 100%; max-width: 420px; padding: 32px 34px 28px; overflow: hidden; }
.card__glow { position: absolute; inset: -40% -10% auto -10%; height: 200px; background: radial-gradient(closest-side, rgba(0, 229, 255, 0.4), transparent 70%); filter: blur(20px); pointer-events: none; }
.card__head { position: relative; margin-bottom: 22px; }
.back { font-size: 12px; color: var(--text-muted); letter-spacing: 0.06em; }
.card__head h2 { font-size: 26px; margin-top: 12px; letter-spacing: 0.04em; }
.card__head p { margin-top: 4px; color: var(--text-muted); font-size: 14px; }

.form { display: flex; flex-direction: column; gap: 14px; }
.field { display: flex; flex-direction: column; gap: 8px; }
.field__label { font-size: 12px; letter-spacing: 0.16em; color: var(--text-dim); text-transform: uppercase; font-family: var(--font-display); }
.field__control { display: flex; align-items: center; gap: 10px; padding: 0 14px; height: 44px; border-radius: 10px; background: rgba(7, 8, 26, 0.6); border: 1px solid var(--border-soft); transition: border-color 0.2s, box-shadow 0.2s, background 0.2s; }
.field__control.is-focus { border-color: var(--neon-cyan); box-shadow: 0 0 0 3px var(--neon-cyan-soft), 0 0 18px var(--neon-cyan-soft); background: rgba(10, 11, 26, 0.9); }
.field__control input { flex: 1; min-width: 0; background: transparent; border: none; outline: none; font-size: 15px; color: var(--text-strong); }
.field__control input::placeholder { color: var(--text-dim); }
.form__submit { margin-top: 6px; height: 46px; font-size: 16px; }
</style>
