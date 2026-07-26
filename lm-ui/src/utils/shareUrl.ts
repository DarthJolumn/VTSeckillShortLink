/**
 * 转换后端返回的 shareUrl 为当前环境可用的短链
 * 后端硬编码 https://s.livemall.com/，本地开发时需要替换
 */
export function toLocalShareUrl(backendShareUrl: string): string {
  if (import.meta.env.DEV) {
    // 提取短码（后端格式：https://s.livemall.com/P5Fg8Kp2mN9）
    const match = backendShareUrl.match(/\/([^/]+)$/)
    if (match) {
      const shortCode = match[1]
      return `${window.location.origin}/s/${shortCode}`
    }
  }
  return backendShareUrl
}
