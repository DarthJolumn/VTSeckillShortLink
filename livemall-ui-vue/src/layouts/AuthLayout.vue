<template>
  <div class="layout layout--auth">
    <ParticleBg />
    <main class="layout__main">
      <slot />
    </main>
  </div>
</template>

<script setup>
import ParticleBg from '@/components/effect/ParticleBg.vue'
</script>

<style scoped>
.layout--auth {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
}
.layout__main { position: relative; z-index: var(--z-card); width: 100%; display: flex; justify-content: center; }
</style>
