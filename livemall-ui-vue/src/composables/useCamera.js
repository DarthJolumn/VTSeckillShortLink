// 摄像头工具 — getUserMedia 封装
// · 安全上下文检测 → 提前告知用户为何不能用
// · 错误分类 → NotAllowedError / NotFoundError / NotReadableError 精确提示
// · macOS/Windows 系统权限指引

import { ref } from 'vue'

/**
 * 检测当前环境能否使用 getUserMedia
 * 规则：HTTPS 或 localhost/127.0.0.1 → 安全；其他 HTTP → 不安全
 */
export function checkSecureContext() {
  if (window.isSecureContext) return { ok: true }
  // 有些浏览器在 localhost 下 isSecureContext 也为 true，这里兜底
  const host = location.hostname
  const isLocal = host === 'localhost' || host === '127.0.0.1' || host === '[::1]'
  if (isLocal) return { ok: true }
  return {
    ok: false,
    reason: `摄像头需要安全上下文（HTTPS 或 localhost）。当前访问地址是 http://${host}，浏览器会拒绝 getUserMedia。请改用 http://localhost:${location.port || 5173} 访问。`,
  }
}

/**
 * 根据 getUserMedia 的 DOMException 返回中文引导文案
 */
export function cameraErrorInfo(error) {
  const name = error?.name || ''
  const msg = error?.message || ''
  switch (name) {
    case 'NotAllowedError':
      return {
        short: '未授权',
        detail: '请允许浏览器使用摄像头。',
        action: '地址栏左侧点击锁/摄像头图标 → 开启摄像头权限。若已拒绝，需在浏览器设置中重置该网站的权限。',
        system: 'Windows：设置 → 隐私与安全性 → 相机 → 确保浏览器的相机开关已打开。\nmacOS：系统偏好设置 → 安全性与隐私 → 相机 → 勾选浏览器。',
      }
    case 'NotFoundError':
      return {
        short: '未检测到摄像头',
        detail: '设备没有摄像头或摄像头未正确连接。',
        action: '请检查摄像头是否已插入（台式机需外接 USB 摄像头），或尝试重新插拔。内置摄像头请确认驱动正常。',
        system: null,
      }
    case 'NotReadableError':
      return {
        short: '摄像头被占用',
        detail: '摄像头正被其他应用使用。',
        action: '请关闭正在使用摄像头的程序（如 QQ、微信视频、腾讯会议、OBS、虚拟机等），然后刷新页面重试。',
        system: null,
      }
    case 'OverconstrainedError':
      return {
        short: '分辨率不支持',
        detail: `摄像头不支持请求的参数（${msg}）。`,
        action: '将尝试用默认参数重新打开。',
        system: null,
      }
    default:
      return {
        short: '摄像头不可用',
        detail: msg || '未知错误。',
        action: '请检查摄像头硬件和驱动，或尝试重启浏览器。',
        system: null,
      }
  }
}

/**
 * 请求摄像头 — 返回 { stream, error, info }
 * 失败时 error 为 DOMException，info 为 cameraErrorInfo(error)
 *
 * @param {{ video?: boolean|object, audio?: boolean }} constraints
 * @returns {Promise<{stream: MediaStream|null, error: DOMException|null, info: object|null}>}
 */
export async function requestCamera(constraints = { video: true, audio: false }) {
  // 1. 安全上下文检查
  const ctx = checkSecureContext()
  if (!ctx.ok) {
    return { stream: null, error: null, info: { short: '环境不支持', detail: ctx.reason, action: null, system: null } }
  }
  // 2. 检查 mediaDevices API 是否存在
  if (!navigator.mediaDevices?.getUserMedia) {
    return { stream: null, error: null, info: { short: 'API 不可用', detail: '当前浏览器不支持 getUserMedia API（需要 HTTPS 或 localhost）。', action: '请使用 Chrome / Firefox / Edge 最新版，并用 localhost 访问。', system: null } }
  }
  // 3. 请求
  try {
    const stream = await navigator.mediaDevices.getUserMedia(constraints)
    return { stream, error: null, info: null }
  } catch (err) {
    return { stream: null, error: err, info: cameraErrorInfo(err) }
  }
}

/**
 * 在组件中使用摄像头的响应式封装
 * 用法：
 *   const { stream, status, errorInfo, open, close, toggleMic, toggleCam } = useCamera()
 *   await open({ video: true, audio: true })
 */
export function useCamera() {
  const stream = ref(null)
  const status = ref('idle')   // idle | requesting | ok | error
  const errorInfo = ref(null)  // cameraErrorInfo 返回值

  async function open(constraints = { video: true, audio: false }) {
    status.value = 'requesting'
    errorInfo.value = null
    const res = await requestCamera(constraints)
    if (res.stream) {
      stream.value = res.stream
      status.value = 'ok'
      return true
    }
    errorInfo.value = res.info
    status.value = 'error'
    return false
  }

  function close() {
    if (stream.value) {
      stream.value.getTracks().forEach(t => t.stop())
      stream.value = null
    }
    status.value = 'idle'
    errorInfo.value = null
  }

  function toggleMic() {
    if (stream.value) {
      const tracks = stream.value.getAudioTracks()
      tracks.forEach(t => t.enabled = !t.enabled)
      return tracks[0]?.enabled ?? false
    }
    return false
  }

  function toggleCam() {
    if (stream.value) {
      const tracks = stream.value.getVideoTracks()
      tracks.forEach(t => t.enabled = !t.enabled)
      return tracks[0]?.enabled ?? false
    }
    return false
  }

  return { stream, status, errorInfo, open, close, toggleMic, toggleCam }
}
