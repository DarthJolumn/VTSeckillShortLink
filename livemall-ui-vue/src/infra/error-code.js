// 错误码 → 文案映射（源见 md/docs 2.1-公共模块 §业务错误码）
// 通用码与业务码共存，业务码以 1xxx 开头

export const ERROR_CODE = {
  // 通用
  200: { msg: '成功', type: 'success' },
  400: { msg: '请求参数有误', type: 'warning' },
  401: { msg: '登录已过期，请重新登录', type: 'warning', auth: true },
  403: { msg: '没有权限', type: 'warning' },
  404: { msg: '资源不存在', type: 'warning' },
  429: { msg: '操作太频繁，请稍后再试', type: 'warning', rateLimit: true },
  500: { msg: '服务开小差了', type: 'error' },

  // 业务（摘要，完整表以 2.1 为准）
  1001: { msg: '库存不足', type: 'warning', seckillSoldOut: true },
  1010: { msg: '旧密码错误', type: 'warning' },
  1011: { msg: '用户名或密码错误', type: 'warning' },
  1012: { msg: '用户名已被注册', type: 'warning' },
  1013: { msg: '登录已过期', type: 'warning', auth: true },
  1014: { msg: '手机号已被使用', type: 'warning' },
}

export function resolveError(code, fallbackMsg = '操作失败') {
  return ERROR_CODE[code] || { msg: fallbackMsg, type: 'error' }
}
