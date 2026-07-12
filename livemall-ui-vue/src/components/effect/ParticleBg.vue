<template>
  <canvas ref="canvasRef" class="particle-bg" aria-hidden="true" />
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'

const props = defineProps({
  density: { type: Number, default: 0.00009 }, // 粒子密度（每像素）
  speed: { type: Number, default: 0.28 },
  link: { type: Boolean, default: true },     // 连线
  hue: { type: Array, default: () => [265, 190] }, // 紫~青
})

const canvasRef = ref(null)
let ctx = null
let raf = 0
let particles = []
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
  ctx.clearRect(0, 0, w, h)
  // 连线
  if (props.link) {
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const a = particles[i], b = particles[j]
        const dx = a.x - b.x, dy = a.y - b.y
        const d2 = dx * dx + dy * dy
        if (d2 < 110 * 110) {
          const o = (1 - d2 / (110 * 110)) * 0.18
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
  // 粒子
  for (const p of particles) {
    p.x += p.vx
    p.y += p.vy
    if (p.x < 0 || p.x > w) p.vx *= -1
    if (p.y < 0 || p.y > h) p.vy *= -1
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
    ctx.fillStyle = hueColor(p.a)
    ctx.shadowColor = ctx.fillStyle
    ctx.shadowBlur = 8
    ctx.fill()
  }
  ctx.shadowBlur = 0
  raf = requestAnimationFrame(step)
}

let resizeObserver = null
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
})

onBeforeUnmount(() => {
  running = false
  cancelAnimationFrame(raf)
  resizeObserver?.disconnect()
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
