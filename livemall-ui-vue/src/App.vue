<template>
  <router-view v-slot="{ Component, route }">
    <transition name="route" mode="out-in">
      <component :is="Component" :key="route.fullPath" />
    </transition>
  </router-view>
</template>

<script setup>
// 顶层只承载路由出口与转场动效，业务布局由各 layout 负责
</script>

<style>
/* 全局 reset / 主题在 styles 下，这里只放路由转场 */
.route-enter-active,
.route-leave-active {
  transition: opacity 0.28s cubic-bezier(0.16, 1, 0.3, 1),
    filter 0.28s cubic-bezier(0.16, 1, 0.3, 1);
}
.route-enter-from {
  opacity: 0;
  filter: blur(8px);
}
.route-leave-to {
  opacity: 0;
  filter: blur(6px);
}
</style>
