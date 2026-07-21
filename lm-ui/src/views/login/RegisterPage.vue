<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h1 class="logo">Live<span>Mall</span></h1>
      <p class="slogan">注册新账号</p>

      <form @submit.prevent="onSubmit">
        <input v-model.trim="form.username" class="input-dark" placeholder="用户名（4~50 位）" maxlength="50" />
        <input v-model="form.password" class="input-dark" type="password" placeholder="密码（至少 8 位）" maxlength="255" />
        <input v-model.trim="form.phone" class="input-dark" placeholder="手机号（选填）" maxlength="11" />
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        <button class="btn-primary submit" type="submit" :disabled="loading">
          {{ loading ? '注册中...' : '注 册' }}
        </button>
      </form>

      <div class="switch">
        已有账号？<router-link to="/login">直接登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { ApiError } from '@/utils/http'
import { showToast } from '@/utils/toast'

const router = useRouter()
const { register } = useAuth()

const form = reactive({ username: '', password: '', phone: '' })
const loading = ref(false)
const errorMsg = ref('')

const PHONE_RE = /^1[3-9]\d{9}$/

/** 校验规则与后端一致：username 4~50、password ≥8、phone 正则 */
function validate(): string {
  if (form.username.length < 4 || form.username.length > 50) return '用户名需 4~50 位'
  if (form.password.length < 8) return '密码至少 8 位'
  if (form.phone && !PHONE_RE.test(form.phone)) return '手机号格式不正确'
  return ''
}

async function onSubmit() {
  errorMsg.value = validate()
  if (errorMsg.value) return
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      phone: form.phone || undefined,
    })
    showToast('注册成功，请登录', 'success')
    router.push('/login')
  } catch (e) {
    errorMsg.value = e instanceof ApiError ? e.message : '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(ellipse at 20% 30%, rgba(255, 44, 85, 0.12), transparent 50%),
    radial-gradient(ellipse at 80% 70%, rgba(138, 99, 255, 0.1), transparent 50%),
    var(--bg-primary);
}
.auth-card {
  width: 360px;
  padding: 40px 32px;
}
.logo {
  text-align: center;
  font-size: 32px;
  font-weight: 800;
}
.logo span { color: var(--accent-red); }
.slogan {
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
  margin: 6px 0 28px;
}
form { display: flex; flex-direction: column; gap: 14px; }
.error-msg { color: var(--accent-red); font-size: 13px; }
.submit { padding: 12px; font-size: 15px; }
.switch {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}
.switch a { color: var(--accent-red); }
</style>
