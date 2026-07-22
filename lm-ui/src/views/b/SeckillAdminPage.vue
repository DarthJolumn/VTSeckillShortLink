<template>
  <div class="seckill-admin">
    <SeckillPanel />
  </div>
</template>

<script setup lang="ts">
import SeckillPanel from '@/components/SeckillPanel.vue'
</script>

<style scoped>
.seckill-admin { display: flex; flex-direction: column; gap: 16px; }
</style>
