// 极轻量 toast，避免循环依赖 UI 库；后续可被 setToastFn 替换为 NaiveUI useMessage
let container = null
function ensureContainer() {
  if (container) return container
  container = document.createElement('div')
  container.className = 'lm-toast-layer'
  container.setAttribute('role', 'status')
  container.setAttribute('aria-live', 'polite')
  document.body.appendChild(container)
  return container
}

const TYPE_COLOR = {
  success: 'var(--success)',
  warning: 'var(--warning)',
  error: 'var(--danger)',
  info: 'var(--info)',
}

export function showToast(message, type = 'info', duration = 2200) {
  const root = ensureContainer()
  const el = document.createElement('div')
  el.className = 'lm-toast'
  el.textContent = message
  el.style.setProperty('--tc', TYPE_COLOR[type] || TYPE_COLOR.info)
  root.appendChild(el)
  // 触发入场
  requestAnimationFrame(() => el.classList.add('show'))
  setTimeout(() => {
    el.classList.remove('show')
    setTimeout(() => el.remove(), 260)
  }, duration)
}
