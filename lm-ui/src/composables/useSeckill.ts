import { ref } from 'vue'
import { useSeckillStore } from '@/stores/seckill'
import { showToast } from '@/utils/toast'

/** 秒杀下单闭环：按钮防重 → 下单 → WS 结果确认（store 内置 15s 超时） */
export function useSeckill() {
  const store = useSeckillStore()
  /** 防重锁：点击后立即置位，结果返回前禁止二次点击 */
  const purchasing = ref(false)

  async function purchase(activityId: number): Promise<boolean> {
    if (purchasing.value) return false
    purchasing.value = true
    try {
      await store.placeOrder(activityId)
      showToast('抢购成功！', 'success')
      return true
    } catch (e) {
      showToast(e instanceof Error ? e.message : '抢购失败', 'error')
      return false
    } finally {
      purchasing.value = false
    }
  }

  return { seckill: store, purchasing, purchase }
}
