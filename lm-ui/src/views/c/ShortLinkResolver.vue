<template>
  <div class="resolver-page">
    <div v-if="loading" class="hint">解析中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '@/utils/http'

const route = useRoute()
const code = route.params.code as string

const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const res = await get<{ shortCode: string; originalUrl: string }>(`/s/${code}`)
    if (res.data?.originalUrl) {
      window.location.href = res.data.originalUrl
    } else {
      error.value = '短链解析失败'
    }
  } catch (e: any) {
    error.value = e?.message || '短链不存在或已过期'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.resolver-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hint, .error {
  font-size: 16px;
  color: var(--text-secondary);
}
.error {
  color: var(--accent-red);
}
</style>
