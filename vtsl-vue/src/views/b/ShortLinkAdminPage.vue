<template>
  <div class="shortlink-page">
    <div class="card panel">
      <div class="panel-header">
        <h3>短链管理</h3>
        <button class="btn-primary" @click="showCreate = true">+ 创建短链</button>
      </div>

      <div v-if="loading" class="empty">加载中...</div>
      <div v-else-if="store.list.length === 0" class="empty">暂无短链</div>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>短码</th>
              <th>标题</th>
              <th>原始 URL</th>
              <th>点击</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="link in store.list" :key="link.id">
              <td>
                <code class="short-code" @click="onCopy(link.shortUrl)">{{ link.shortCode }}</code>
                <span class="copy-hint">点此复制</span>
              </td>
              <td class="title-cell" :title="link.title">{{ link.title }}</td>
              <td class="url-cell" :title="link.originalUrl">
                <a :href="link.originalUrl" target="_blank" rel="noopener">{{ truncate(link.originalUrl, 40) }}</a>
              </td>
              <td class="num">{{ link.clickCount }}</td>
              <td>
                <span class="status" :class="link.status === 1 ? 'on' : 'off'">
                  {{ link.status === 1 ? '正常' : '已删除' }}
                </span>
              </td>
              <td class="time">{{ formatTime(link.createdAt) }}</td>
              <td>
                <div class="actions">
                  <button class="btn-icon" title="复制短链" @click="onCopy(link.shortUrl)">📋</button>
                  <button v-if="link.status === 1" class="btn-icon del" title="删除" @click="onDelete(link)">🗑️</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="totalPages > 1" class="pagination">
          <button :disabled="store.page <= 1" @click="goPage(store.page - 1)">‹</button>
          <span>{{ store.page }} / {{ totalPages }}</span>
          <button :disabled="store.page >= totalPages" @click="goPage(store.page + 1)">›</button>
        </div>
      </div>
    </div>

    <!-- 创建弹窗 -->
    <Teleport to="body">
      <div v-if="showCreate" class="mask" @click.self="showCreate = false">
        <div class="dialog card">
          <button class="close-btn" @click="showCreate = false">✕</button>
          <h3>创建短链</h3>
          <form @submit.prevent="onCreate">
            <label>
              标题（可选）
              <input v-model="createForm.title" class="input-dark" maxlength="100" placeholder="短链备注名称" />
            </label>
            <label>
              商品 ID
              <input v-model.number="createForm.productId" type="number" class="input-dark" min="1" required placeholder="关联商品 ID" />
            </label>
            <label>
              原始 URL
              <input v-model="createForm.originalUrl" type="url" class="input-dark" required placeholder="https://..." />
            </label>
            <div class="dialog-actions">
              <button type="button" class="btn-ghost" @click="showCreate = false">取消</button>
              <button type="submit" class="btn-primary" :disabled="creating">{{ creating ? '创建中...' : '创建' }}</button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useShortLinkStore } from '@/stores/shortlink'
import { showToast } from '@/utils/toast'
import { ApiError } from '@/utils/http'
import type { CreateShortLinkRequest } from '@/types/shortlink'

const store = useShortLinkStore()

const loading = ref(false)
const showCreate = ref(false)
const creating = ref(false)
const createForm = ref<CreateShortLinkRequest>({ productId: 0, originalUrl: '', title: '' })

const totalPages = computed(() => Math.ceil(store.total / store.size))

onMounted(async () => {
  loading.value = true
  try {
    await store.fetchList()
  } finally {
    loading.value = false
  }
})

function goPage(p: number) {
  store.fetchList(p, store.size)
}

async function onCreate() {
  const form = createForm.value
  if (!form.productId || !form.originalUrl) return
  creating.value = true
  try {
      await store.create(form)
      showCreate.value = false
      createForm.value = { productId: 0, originalUrl: '', title: '' }
      showToast('创建成功', 'success')
      await store.fetchList()
    } catch (e: unknown) {
      showToast(e instanceof ApiError ? e.message : '创建失败', 'error')
    } finally {
      creating.value = false
    }
}

async function onDelete(link: { id: number; shortCode: string }) {
  if (!confirm(`确定删除短链 ${link.shortCode} 吗？`)) return
  try {
    await store.remove(link.id)
    showToast('已删除', 'success')
  } catch (e: unknown) {
    showToast(e instanceof ApiError ? e.message : '删除失败', 'error')
  }
}

async function onCopy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    showToast('已复制到剪贴板', 'success')
  } catch {
    showToast('复制失败，请手动复制', 'error')
  }
}

function truncate(s: string, max: number) {
  return s && s.length > max ? s.slice(0, max) + '…' : s
}

function formatTime(iso: string) {
  if (!iso) return '-'
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.panel { padding: 20px 24px; }
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.panel-header h3 { font-size: 16px; margin: 0; }
.empty { text-align: center; color: var(--text-secondary); padding: 40px 0; font-size: 13px; }

.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 2px solid var(--border);
  color: var(--text-secondary);
  font-weight: 600;
  white-space: nowrap;
}
.data-table td {
  padding: 12px;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}
.data-table tbody tr:hover { background: var(--bg-primary); }
.short-code {
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  color: var(--accent-red);
  padding: 2px 6px;
  background: rgba(255, 44, 85, 0.08);
  border-radius: 4px;
}
.short-code:hover { background: rgba(255, 44, 85, 0.18); }
.copy-hint { font-size: 11px; color: var(--text-secondary); margin-left: 6px; }
.title-cell { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.url-cell { max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.url-cell a { color: var(--accent-gold); text-decoration: none; }
.url-cell a:hover { text-decoration: underline; }
.num { font-family: var(--font-mono); color: var(--text-secondary); }
.time { font-family: var(--font-mono); font-size: 12px; color: var(--text-secondary); white-space: nowrap; }
.status { font-size: 12px; padding: 2px 8px; border-radius: 8px; }
.status.on { color: var(--status-green); background: rgba(34, 197, 94, 0.12); }
.status.off { color: var(--text-secondary); background: rgba(128, 128, 128, 0.12); }
.actions { display: flex; gap: 4px; }
.btn-icon {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: transparent;
  font-size: 15px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}
.btn-icon:hover { background: var(--bg-primary); }
.btn-icon.del:hover { border-color: var(--accent-red); }

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding-top: 16px;
  font-size: 13px;
  color: var(--text-secondary);
}
.pagination button {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: transparent;
  font-size: 16px;
  cursor: pointer;
}
.pagination button:disabled { opacity: 0.4; cursor: default; }
.pagination button:not(:disabled):hover { background: var(--bg-primary); }

/* 弹窗 */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dialog {
  width: 440px;
  max-width: 92vw;
  padding: 24px;
  position: relative;
}
.close-btn { position: absolute; top: 14px; right: 16px; color: var(--text-secondary); }
.dialog h3 { font-size: 16px; margin-bottom: 18px; }
.dialog form { display: flex; flex-direction: column; gap: 14px; }
.dialog label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }

</style>
