import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get, post, del } from '@/utils/http'
import type { ApiResponse } from '@/types/api'
import type { ShortLinkVO, CreateShortLinkRequest, ShortLinkListData } from '@/types/shortlink'

export const useShortLinkStore = defineStore('shortlink', () => {
  const list = ref<ShortLinkVO[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(20)
  const loading = ref(false)

  async function fetchList(p: number = 1, s: number = 20) {
    loading.value = true
    try {
      const res = await get<ShortLinkListData>('/s/manage/list', { params: { page: p, size: s } })
      list.value = res.data.records
      total.value = res.data.total
      page.value = res.data.page
      size.value = res.data.size
    } finally {
      loading.value = false
    }
  }

  async function create(req: CreateShortLinkRequest): Promise<ShortLinkVO> {
    const res = await post<ShortLinkVO>('/s/manage/create', req)
    return res.data
  }

  async function remove(id: number) {
    await del(`/s/manage/${id}`)
    list.value = list.value.filter(l => l.id !== id)
    total.value--
  }

  return { list, total, page, size, loading, fetchList, create, remove }
})
