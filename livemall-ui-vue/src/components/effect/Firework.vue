<template>
  <canvas v-if="active" ref="canvasRef" class="firework" aria-hidden="true" />
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  trigger: { type: Number, default: 0 }, // 自增即触发
})
const canvasRef = ref(null)
const active = ref(false)
let raf = 0
let particles = []
let ctx = null
let w = 0, h = 0

function rand(a, b) { return a + Math.random() * (b - a) }
const HUES = [280, 190, 340, 50, 160]

function burst(x, y) {
  const hue = HUES[Math.floor(Math.random() * HUES.length)]
  const count = 60
  for (let i = 0; i < count; i++) {
    const a = (Math.PI * 2 * i) / count + rand(-0.1, 0.1)
    const sp = rand(2, 6)
    particles.push({
      x, y,
      vx: Math.cos(a) * sp,
      vy: Math.sin(a) * sp,
      life: 1,
      decay: rand(0.012, 0.025),
      hue: hue + rand(-20, 20),
      r: rand(1.5, 3),
    })
  }
}

function step() {
  // 用 clearRect 清屏，避免半透明黑覆盖让整个页面暗化
  ctx.clearRect(0, 0, w, h)
  particles = particles.filter((p) => p.life > 0)
  for (const p of particles) {
    p.x += p.vx
    p.y += p.vy
    p.vy += 0.06 // 重力
    p.vx *= 0.99
    p.life -= p.decay
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r * p.life, 0, Math.PI * 2)
    ctx.fillStyle = `hsla(${p.hue}, 95%, 65%, ${p.life})`
    ctx.shadowColor = ctx.fillStyle
    ctx.shadowBlur = 12
    ctx.fill()
  }
  ctx.shadowBlur = 0
  if (particles.length > 0) {
    raf = requestAnimationFrame(step)
  } else {
    active.value = false
    cancelAnimationFrame(raf)
  }
}

function fire() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  active.value = true
  requestAnimationFrame(() => {
    const c = canvasRef.value
    if (!c) return
    ctx = c.getContext('2d')
    w = c.clientWidth; h = c.clientHeight
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    c.width = w * dpr; c.height = h * dpr
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    // 2~3 处爆点
    const n = 2 + Math.floor(Math.random() * 2)
    for (let i = 0; i < n; i++) {
      setTimeout(() => burst(rand(w * 0.2, w * 0.8), rand(h * 0.25, h * 0.6)), i * 220)
    }
    cancelAnimationFrame(raf)
    raf = requestAnimationFrame(step)
  })
}

watch(() => props.trigger, (v) => { if (v > 0) fire() })

onMounted(() => { if (props.trigger > 0) fire() })
onBeforeUnmount(() => { cancelAnimationFrame(raf); active.value = false })
</script>

<style scoped>
.firework {
  position: fixed;
  inset: 0;
  width: 100%; height: 100%;
  pointer-events: none;
  z-index: var(--z-modal);
}
</style>
