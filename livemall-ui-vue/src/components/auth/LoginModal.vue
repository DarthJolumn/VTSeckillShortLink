<template>
  <Teleport to="body">
    <div v-if="visible" class="login-modal-overlay" @click.self="close">
      <div class="login-modal-card">
        <button class="close-btn" @click="close">&times;</button>

        <div class="tab-row">
          <button :class="{ active: activeTab === 'login' }" @click="activeTab = 'login'">登录</button>
          <button :class="{ active: activeTab === 'register' }" @click="activeTab = 'register'">注册</button>
        </div>

        <!-- 登录表单 -->
        <form v-if="activeTab === 'login'" @submit.prevent="onLogin">
          <div class="field">
            <label>用户名</label>
            <input v-model="loginForm.username" type="text" placeholder="请输入用户名" required />
          </div>
          <div class="field">
            <label>密码</label>
            <input v-model="loginForm.password" type="password" placeholder="请输入密码" required />
          </div>
          <p v-if="errMsg" class="error-msg">{{ errMsg }}</p>
          <button type="submit" class="submit-btn" :disabled="loading">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <!-- 注册表单 -->
        <form v-else @submit.prevent="onRegister">
          <div class="field">
            <label>用户名</label>
            <input v-model="regForm.username" type="text" placeholder="4-20 位字符" required />
          </div>
          <div class="field">
            <label>密码</label>
            <input v-model="regForm.password" type="password" placeholder="至少 8 位" required />
          </div>
          <div class="field">
            <label>确认密码</label>
            <input v-model="regForm.confirmPassword" type="password" placeholder="再输一次" required />
          </div>
          <div class="field">
            <label>手机号（可选）</label>
            <input v-model="regForm.phone" type="text" placeholder="11 位手机号" />
          </div>
          <p v-if="errMsg" class="error-msg">{{ errMsg }}</p>
          <button type="submit" class="submit-btn" :disabled="loading">
            {{ loading ? '注册中...' : '注册并登录' }}
          </button>
        </form>

        <p class="hint">登录后可发弹幕、送礼物、参与秒杀</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useLoginModal } from '@/composables/useLoginModal'

const { visible, activeTab, handleLogin, handleRegister, close } = useLoginModal()

const loading = ref(false)
const errMsg = ref('')

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', confirmPassword: '', phone: '' })

async function onLogin() {
  errMsg.value = ''
  loading.value = true
  try {
    await handleLogin({ ...loginForm })
    loginForm.username = ''
    loginForm.password = ''
  } catch (e) {
    errMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  errMsg.value = ''
  if (regForm.password !== regForm.confirmPassword) {
    errMsg.value = '两次密码不一致'
    return
  }
  if (regForm.password.length < 8) {
    errMsg.value = '密码至少 8 位'
    return
  }
  loading.value = true
  try {
    await handleRegister({ ...regForm })
    regForm.username = ''
    regForm.password = ''
    regForm.confirmPassword = ''
    regForm.phone = ''
  } catch (e) {
    errMsg.value = e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.login-modal-card {
  position: relative;
  width: 380px;
  padding: 28px 32px 24px;
  border-radius: 12px;
  background: #1a1a2e;
  border: 1px solid rgba(0, 255, 200, 0.2);
  box-shadow: 0 8px 40px rgba(0, 255, 200, 0.15);
}
.close-btn {
  position: absolute;
  top: 12px;
  right: 14px;
  background: none;
  border: none;
  color: #888;
  font-size: 22px;
  cursor: pointer;
}
.close-btn:hover { color: #fff; }
.tab-row {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.tab-row button {
  flex: 1;
  padding: 8px;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: #888;
  font-size: 14px;
  cursor: pointer;
}
.tab-row button.active {
  color: #00ffc8;
  border-bottom-color: #00ffc8;
}
.field {
  margin-bottom: 14px;
}
.field label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: #aaa;
}
.field input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  background: #0d0d1f;
  color: #eee;
  font-size: 14px;
}
.field input:focus {
  border-color: rgba(0, 255, 200, 0.4);
  outline: none;
}
.error-msg {
  color: #ff5a5a;
  font-size: 12px;
  margin: 4px 0;
}
.submit-btn {
  width: 100%;
  padding: 10px;
  margin-top: 8px;
  border: none;
  border-radius: 6px;
  background: linear-gradient(135deg, #00ffc8, #00d4ff);
  color: #000;
  font-weight: 600;
  cursor: pointer;
}
.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.hint {
  margin-top: 14px;
  text-align: center;
  font-size: 12px;
  color: #666;
}
</style>