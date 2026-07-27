<template>
  <div class="profile-page">
    <div class="card panel">
      <h3>个人信息</h3>
      <div class="info-row" v-if="userStore.profile">
        <div class="avatar">{{ (userStore.nickname || '?').charAt(0) }}</div>
        <div>
          <div class="uname">{{ userStore.profile.username }}</div>
          <div class="uid">ID: {{ userStore.profile.id }} · 余额 🪙 {{ userStore.balance }}</div>
        </div>
      </div>

      <form class="edit-form" @submit.prevent="onSaveProfile">
        <label>
          昵称
          <input v-model.trim="profileForm.nickname" class="input-dark" placeholder="昵称" maxlength="30" />
        </label>
        <label>
          手机号
          <input v-model.trim="profileForm.phone" class="input-dark" placeholder="手机号" maxlength="11" />
        </label>
        <button class="btn-primary" type="submit" :disabled="savingProfile">保存资料</button>
      </form>
    </div>

    <div class="card panel">
      <h3>修改密码</h3>
      <form class="edit-form" @submit.prevent="onSavePassword">
        <label>
          旧密码
          <input v-model="pwdForm.oldPassword" class="input-dark" type="password" maxlength="255" />
        </label>
        <label>
          新密码（至少 8 位）
          <input v-model="pwdForm.newPassword" class="input-dark" type="password" maxlength="255" />
        </label>
        <button class="btn-primary" type="submit" :disabled="savingPwd">修改密码</button>
      </form>
    </div>

    <div class="card panel danger">
      <h3>账号操作</h3>
      <button class="btn-ghost" @click="onLogout">退出登录</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'
import { showToast } from '@/utils/toast'
import { ApiError } from '@/utils/http'

const router = useRouter()
const userStore = useUserStore()
const { logout } = useAuth()

const profileForm = reactive({ nickname: '', phone: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const savingProfile = ref(false)
const savingPwd = ref(false)

const PHONE_RE = /^1[3-9]\d{9}$/

onMounted(async () => {
  try {
    await Promise.all([userStore.fetchProfile(), userStore.fetchBalance()])
    profileForm.nickname = userStore.profile?.nickname || ''
    profileForm.phone = userStore.profile?.phone || ''
  } catch { /* 401 由拦截器处理 */ }
})

async function onSaveProfile() {
  if (profileForm.phone && !PHONE_RE.test(profileForm.phone)) {
    showToast('手机号格式不正确', 'warning')
    return
  }
  savingProfile.value = true
  try {
    await userStore.updateProfile({ nickname: profileForm.nickname || undefined, phone: profileForm.phone || undefined })
    showToast('资料已保存', 'success')
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '保存失败', 'error')
  } finally {
    savingProfile.value = false
  }
}

async function onSavePassword() {
  if (pwdForm.newPassword.length < 8) {
    showToast('新密码至少 8 位', 'warning')
    return
  }
  savingPwd.value = true
  try {
    await userStore.updatePassword(pwdForm)
    showToast('密码已修改', 'success')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
  } catch (e) {
    showToast(e instanceof ApiError ? e.message : '修改失败', 'error')
  } finally {
    savingPwd.value = false
  }
}

async function onLogout() {
  await logout()
  router.push('/login')
}
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; gap: 16px; }
.panel { padding: 20px 24px; }
h3 { font-size: 16px; margin-bottom: 16px; }
.info-row { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8a63ff, #00e5ff);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
}
.uname { font-weight: 600; font-size: 16px; }
.uid { color: var(--text-secondary); font-size: 12px; margin-top: 2px; }
.edit-form { display: flex; flex-direction: column; gap: 12px; max-width: 360px; }
.edit-form label { font-size: 13px; color: var(--text-secondary); display: flex; flex-direction: column; gap: 6px; }
.edit-form .btn-primary { align-self: flex-start; }
.danger .btn-ghost { color: var(--accent-red); border-color: var(--accent-red); }
</style>
