// 前端限流工具 — throttle（首次执行 + 冷却期） / debounce（延迟执行）
// 用于秒杀按钮、弹幕发送、送礼等高频操作的客户端防护

/**
 * throttle：首次立即执行，之后在 delay ms 内忽略后续调用。
 * 适用场景：秒杀按钮、登录/注册提交
 *
 * @param {Function} fn 要限流的函数
 * @param {number} delay 冷却时间（毫秒），默认 500
 * @returns {Function} 包装后的函数（同步返回 undefined 表示被节流）
 */
export function throttle(fn, delay = 500) {
  let last = 0
  return function (...args) {
    const now = Date.now()
    if (now - last < delay) return
    last = now
    return fn.apply(this, args)
  }
}

/**
 * debounce：连续调用只执行最后一次，等待 delay ms 无新调用后执行。
 * 适用场景：搜索输入、窗口 resize
 */
export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      fn.apply(this, args)
    }, delay)
  }
}

/**
 * 创建带冷却期的异步函数 — 执行期间自动加锁，完成后等 cooldown ms 才能再次调用。
 * 适用场景：送礼（需等 WS 确认）、秒杀下单（REST 等响应）
 *
 * @param {Function} fn 异步函数
 * @param {number} cooldown 完成后冷却时间（毫秒），默认 800
 * @returns {Function} 包装后的异步函数（被锁时返回 Promise.resolve(null)）
 */
export function asyncLock(fn, cooldown = 800) {
  let locked = false
  return async function (...args) {
    if (locked) return null
    locked = true
    try {
      return await fn.apply(this, args)
    } finally {
      setTimeout(() => { locked = false }, cooldown)
    }
  }
}
