<template>
  <div class="err">
    <div class="err__bg" aria-hidden="true" />
    <div class="err__code num">403</div>
    <h1 class="err__title">访问受限</h1>
    <p class="err__desc">你没有进入该区域的权限，可能是角色不符。</p>
    <NeonButton variant="ghost" @click="back">返回</NeonButton>
  </div>
</template>
<script setup>
import { useRouter } from 'vue-router'
import NeonButton from '@/components/base/NeonButton.vue'
const router = useRouter()
function back() { router.back() }
</script>
<style scoped>
.err { min-height: 80vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; text-align: center; padding: 24px; position: relative; }
.err__bg { position: absolute; inset: 0; background: radial-gradient(closest-side at 50% 40%, rgba(255,77,79,0.18), transparent 70%); pointer-events: none; }
.err__code { font-size: clamp(90px, 18vw, 180px); font-weight: 900; line-height: 1; color: transparent; -webkit-text-stroke: 2px var(--seckill-from); text-shadow: 0 0 30px var(--seckill-glow); position: relative; }
.err__title { font-size: 28px; position: relative; }
.err__desc { color: var(--text-muted); position: relative; }
</style>
