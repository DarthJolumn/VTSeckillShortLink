<template>
  <canvas v-if="active" ref="canvasRef" class="gift-rain" aria-hidden="true" />
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  trigger: { type: Number, default: 0 },
  icon: { type: String, default: '🎁' },
  duration: { type: Number, default: 2000 },
})
const canvasRef = ref(null)
const active = ref(false)
let raf = 0
let drops = []
let ctx = null
let w = 0, h = 0
let endAt = 0

function rand(a, b) { return a + Math.random() * (b - a) }

function spawn() {
  drops.push({
    x: rand(0, w),
    y: -20,
    vy: rand(2, 5),
    vx: rand(-0.6, 0.6),
    rot: rand(0, Math.PI * 2),
    vr: rand(-0.1, 0.1),
    size: rand(18, 32),
    icon: props.icon,
    alpha: 1,
  })
}

function step() {
  ctx.clearRect(0, 0, w, h)
  if (Date.now() < endAt && Math.random() < 0.5) spawn()
  drops = drops.filter((d) => d.y < h + 40 && d.alpha > 0)
  for (const d of drops) {
    d.x += d.vx
    d.y += d.vy
    d.rot += d.vr
    if (d.y > h - 60) d.alpha -= 0.02
    ctx.save()
    ctx.translate(d.x, d.y)
    ctx.rotate(d.rot)
    ctx.globalAlpha = Math.max(0, d.alpha)
    ctx.font = `${d.size}px serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.shadowColor = 'rgba(255,200,100,0.6)'
    ctx.shadowBlur = 10
    ctx.fillText(d.icon, 0, 0)
    ctx.restore()
  }
  if (drops.length > 0 || Date.now() < endAt) {
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
    drops = []
    endAt = Date.now() + props.duration
    cancelAnimationFrame(raf)
    raf = requestAnimationFrame(step)
  })
}

watch(() => props.trigger, (v) => { if (v > 0) fire() })
onBeforeUnmount(() => { cancelAnimationFrame(raf); active.value = false })
</script>

<style scoped>
.gift-rain {
  position: fixed;
  inset: 0;
  width: 100%; height: 100%;
  pointer-events: none;
  z-index: var(--z-modal);
}
</style>
