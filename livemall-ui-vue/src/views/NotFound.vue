<template>
  <div class="err">
    <div class="err__bg" aria-hidden="true" />
    <div class="err__code num">404</div>
    <h1 class="err__title">信号丢失</h1>
    <p class="err__desc">你要找的页面飘出了直播间。</p>
    <NeonButton variant="cyan" @click="home">回到广场</NeonButton>
  </div>
</template>
<script setup>
import { useRouter } from 'vue-router'
import NeonButton from '@/components/base/NeonButton.vue'
const router = useRouter()
function home() { router.push('/') }
</script>
<style scoped>
.err { min-height: 80vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; text-align: center; padding: 24px; position: relative; }
.err__bg { position: absolute; inset: 0; background: radial-gradient(closest-side at 50% 40%, rgba(0,229,255,0.14), transparent 70%); pointer-events: none; }
.err__code { font-size: clamp(90px, 18vw, 180px); font-weight: 900; line-height: 1; color: transparent; -webkit-text-stroke: 2px var(--neon-cyan); text-shadow: 0 0 30px var(--neon-cyan-soft); position: relative; }
.err__title { font-size: 28px; position: relative; }
.err__desc { color: var(--text-muted); position: relative; }
</style>
