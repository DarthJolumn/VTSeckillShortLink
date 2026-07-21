<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h1 class="logo">Live<span>Mall</span></h1>
      <p class="slogan">直播秒杀 · 好物抢先购</p>

      <form @submit.prevent="onSubmit">
        <input v-model.trim="form.username" class="input-dark" placeholder="用户名" maxlength="50" />
        <input v-model="form.password" class="input-dark" type="password" placeholder="密码" maxlength="255" />
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        <button class="btn-primary submit" type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>

      <div class="switch">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { ApiError } from '@/utils/http'
import { showToast } from '@/utils/toast'

const router = useRouter()
const route = useRoute()
const { login } = useAuth()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')

async function onSubmit() {
  errorMsg.value = ''
  if (!form.username || !form.password) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    await login(form)
    showToast('登录成功', 'success')
    router.push((route.query.redirect as string) || '/')
  } catch (e) {
    errorMsg.value = e instanceof ApiError ? e.message : '登录失败，请稍后重试'
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
