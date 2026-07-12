<template>
  <div class="audience-layout">
    <header class="audience-layout__bar">
      <div class="bar__inner container">
        <RouterLink to="/" class="brand" aria-label="LiveMall 首页">
          <span class="brand__mark">LM</span>
          <span class="brand__name">LiveMall</span>
        </RouterLink>
        <nav class="nav">
          <RouterLink to="/" class="nav__item">广场</RouterLink>
          <RouterLink to="/orders" class="nav__item">我的订单</RouterLink>
          <RouterLink to="/devices" class="nav__item">设备</RouterLink>
          <RouterLink v-if="userStore.isAnchor || userStore.isAdmin" to="/anchor/stream" class="nav__item nav__item--accent">主播台</RouterLink>
          <RouterLink v-if="userStore.isAdmin" to="/admin/dashboard" class="nav__item nav__item--accent">作战大屏</RouterLink>
        </nav>
        <div class="me">
          <RouterLink to="/profile" class="me__avatar" :title="userStore.nickname">
            <img v-if="userStore.avatar" :src="userStore.avatar" alt="" />
            <span v-else>{{ (userStore.nickname || 'U').slice(0, 1) }}</span>
          </RouterLink>
        </div>
      </div>
    </header>
    <main class="audience-layout__content">
      <router-view v-slot="{ Component, route }">
        <transition name="route" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
</script>

<style scoped>
.audience-layout { min-height: 100vh; }
.audience-layout__bar {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  background: rgba(10, 11, 26, 0.72);
  backdrop-filter: blur(16px) saturate(140%);
  border-bottom: 1px solid var(--border-faint);
}
.bar__inner {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.brand { display: flex; align-items: center; gap: 10px; }
.brand__mark {
  width: 32px; height: 32px;
  display: grid; place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--neon-purple), var(--neon-cyan));
  color: #07081a;
  font-family: var(--font-num);
  font-weight: 900;
  font-size: 14px;
  box-shadow: var(--glow-purple);
}
.brand__name {
  font-family: var(--font-display);
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--text-strong);
}
.nav { display: flex; gap: 4px; margin-left: 8px; }
.nav__item {
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--text-muted);
  transition: color 0.2s, background 0.2s;
}
.nav__item:hover { color: var(--text-strong); }
.nav__item.router-link-active {
  color: var(--text-strong);
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
}
.nav__item--accent { color: var(--neon-cyan); }
.me { margin-left: auto; }
.me__avatar {
  width: 34px; height: 34px;
  display: grid; place-items: center;
  border-radius: 50%;
  background: var(--bg-card);
  border: 1px solid var(--border-strong);
  overflow: hidden;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--text-strong);
}
.me__avatar img { width: 100%; height: 100%; object-fit: cover; }
.audience-layout__content { padding: 24px 0 64px; }
</style>
