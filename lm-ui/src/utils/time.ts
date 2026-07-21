/** 全局时间校准：秒杀倒计时以服务端时间为准（联调文档 §2.3） */

let offset = 0 // 服务端时间 - 本地时间

/** 用任意响应的 Date header 校准（登录后调用一次即可） */
export function syncTimeFromHeaders(dateHeader: string | null, rttStart: number): void {
  if (!dateHeader) return
  const serverDate = new Date(dateHeader).getTime()
  if (Number.isNaN(serverDate)) return
  const end = Date.now()
  offset = serverDate - (rttStart + end) / 2
}

/** 服务端当前时间（ms） */
export function serverNow(): number {
  return Date.now() + offset
}
