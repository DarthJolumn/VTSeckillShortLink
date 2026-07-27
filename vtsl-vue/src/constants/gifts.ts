/** 礼物目录（本地维护）。
 * ⚠️ 后端 GIFT 广播只转发 giftId/quantity，不带名称/图标/价格
 * （LiveWebSocket.handleGift 中 giftName:"" giftIcon:"" price:0 gain:0），
 * 元数据由前端本地映射，gain = price * quantity。 */
export interface GiftDef {
  id: number
  name: string
  icon: string
  price: number
}

export const GIFT_CATALOG: GiftDef[] = [
  { id: 1, name: '玫瑰', icon: '🌹', price: 1 },
  { id: 2, name: '啤酒', icon: '🍺', price: 5 },
  { id: 3, name: '火箭', icon: '🚀', price: 100 },
  { id: 4, name: '皇冠', icon: '👑', price: 520 },
]

export function getGiftDef(id: number): GiftDef {
  return GIFT_CATALOG.find(g => g.id === id) ?? { id, name: `礼物${id}`, icon: '🎁', price: 0 }
}
