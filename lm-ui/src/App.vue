<template>
  <router-view v-slot="{ Component, route }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
  <Toast />
</template>

<script setup lang="ts">
import Toast from '@/components/Toast.vue'
</script>

<style>
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>
