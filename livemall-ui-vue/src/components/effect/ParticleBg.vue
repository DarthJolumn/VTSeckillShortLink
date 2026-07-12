<template>
  <canvas ref="canvasRef" class="particle-bg" aria-hidden="true" />
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'

const props = defineProps({
  density: { type: Number, default: 0.00005 }, // 粒子密度（每像素）· 已降，避免大屏 O(n²) 连线卡顿
  speed: { type: Number, default: 0.28 },
  link: { type: Boolean, default: true },     // 连线
  hue: { type: Array, default: () => [265, 190] }, // 紫~青
})

const canvasRef = ref(null)
let ctx = null
let raf = 0
let particles = []
let frameSkip = 0
let w = 0
let h = 0
let dpr = 1
let running = true

function rand(a, b) { return a + Math.random() * (b - a) }

function hueColor(alpha) {
  const t = Math.random()
  const base = props.hue[0] + t * (props.hue[1] - props.hue[0])
  return `hsla(${base}, 95%, 65%, ${alpha})`
}

function init() {
  const c = canvasRef.value
  ctx = c.getContext('2d')
  dpr = Math.min(window.devicePixelRatio || 1, 2)
  resize()
  const count = Math.floor(w * h * props.density)
  particles = new Array(count).fill(0).map(() => ({
    x: Math.random() * w,
    y: Math.random() * h,
    vx: rand(-props.speed, props.speed),
    vy: rand(-props.speed, props.speed),
    r: rand(0.8, 2.4),
    a: rand(0.25, 0.9),
    _color: hueColor(rand(0.25, 0.9)), // 预计算颜色，避免每帧重算
  }))
}

function resize() {
  const c = canvasRef.value
  w = c.clientWidth
  h = c.clientHeight
  c.width = Math.floor(w * dpr)
  c.height = Math.floor(h * dpr)
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function step() {
  if (!running) return
  // 降帧：每 2 帧渲染一次（30fps 足够，减半 CPU/GPU 开销）
  frameSkip++
  if (frameSkip < 2) {
    raf = requestAnimationFrame(step)
    return
  }
  frameSkip = 0
  ctx.clearRect(0, 0, w, h)
  // 连线（空间分桶剪枝，避免 O(n²)）
  if (props.link) {
    const CELL = 110
    const cols = Math.ceil(w / CELL) + 1
    const grid = new Map()
    for (let i = 0; i < particles.length; i++) {
      const p = particles[i]
      const cx = Math.floor(p.x / CELL), cy = Math.floor(p.y / CELL)
      const key = cx * 10000 + cy
      if (!grid.has(key)) grid.set(key, [])
      grid.get(key).push(i)
    }
    const R2 = CELL * CELL
    for (let i = 0; i < particles.length; i++) {
      const a = particles[i]
      const cx = Math.floor(a.x / CELL), cy = Math.floor(a.y / CELL)
      for (let dx = -1; dx <= 1; dx++) {
        for (let dy = -1; dy <= 1; dy++) {
          const bucket = grid.get((cx + dx) * 10000 + (cy + dy))
          if (!bucket) continue
          for (const j of bucket) {
            if (j <= i) continue // 避免重复配对
            const b = particles[j]
            const ddx = a.x - b.x, ddy = a.y - b.y
            const d2 = ddx * ddx + ddy * ddy
            if (d2 < R2) {
              const o = (1 - d2 / R2) * 0.18
              ctx.strokeStyle = `rgba(138, 99, 255, ${o})`
              ctx.lineWidth = 0.6
              ctx.beginPath()
              ctx.moveTo(a.x, a.y)
              ctx.lineTo(b.x, b.y)
              ctx.stroke()
            }
          }
        }
      }
    }
  }
  // 粒子（不设 shadowBlur，避免 GPU 开销）
  for (const p of particles) {
    p.x += p.vx
    p.y += p.vy
    if (p.x < 0 || p.x > w) p.vx *= -1
    if (p.y < 0 || p.y > h) p.vy *= -1
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
    ctx.fillStyle = p._color
    ctx.fill()
  }
  raf = requestAnimationFrame(step)
}

let resizeObserver = null
let onVisChange = null
onMounted(() => {
  // 尊重减少动效偏好
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    running = false
    canvasRef.value.style.display = 'none'
    return
  }
  init()
  step()
  resizeObserver = new ResizeObserver(() => resize())
  resizeObserver.observe(canvasRef.value)
  // 页面不可见时暂停 RAF，省 CPU
  onVisChange = () => {
    if (document.hidden) {
      if (raf) { cancelAnimationFrame(raf); raf = 0 }
    } else if (running && !raf) {
      step()
    }
  }
  document.addEventListener('visibilitychange', onVisChange)
})

onBeforeUnmount(() => {
  running = false
  cancelAnimationFrame(raf)
  resizeObserver?.disconnect()
  if (onVisChange) document.removeEventListener('visibilitychange', onVisChange)
})
</script>

<style scoped>
.particle-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}
</style>
