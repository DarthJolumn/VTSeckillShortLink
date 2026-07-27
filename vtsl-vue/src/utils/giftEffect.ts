import { reactive } from 'vue'

export interface GiftEffectItem {
  id: number
  userId: number
  username: string
  giftId: number
  giftName: string
  giftIcon: string
  price: number
  gain: number
  quantity: number
}

export const giftEffectState = reactive<{ list: GiftEffectItem[] }>({ list: [] })

let seq = 0
const EFFECT_DURATION = 3200

/** 收到 WS GIFT 时调用（联调文档：播特效 + 刷新排行榜） */
export function pushGiftEffect(g: Omit<GiftEffectItem, 'id'>): void {
  const id = ++seq
  giftEffectState.list.push({ ...g, id })
  setTimeout(() => {
    const i = giftEffectState.list.findIndex(item => item.id === id)
    if (i >= 0) giftEffectState.list.splice(i, 1)
  }, EFFECT_DURATION)
}
