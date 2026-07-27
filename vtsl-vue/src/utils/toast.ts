import { reactive } from 'vue'

export type ToastType = 'info' | 'success' | 'warning' | 'error'

interface ToastItem {
  id: number
  text: string
  type: ToastType
}

export const toastState = reactive<{ list: ToastItem[] }>({ list: [] })

let seq = 0

export function showToast(text: string, type: ToastType = 'info', duration = 2500): void {
  const id = ++seq
  toastState.list.push({ id, text, type })
  setTimeout(() => {
    const i = toastState.list.findIndex(t => t.id === id)
    if (i >= 0) toastState.list.splice(i, 1)
  }, duration)
}
