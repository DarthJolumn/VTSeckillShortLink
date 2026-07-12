<template>
  <div class="anchor-layout">
    <aside class="anchor-layout__side">
      <RouterLink to="/" class="brand">
        <span class="brand__mark">LM</span>
        <span>主播控制台</span>
      </RouterLink>
      <nav>
        <RouterLink to="/anchor/stream" class="side-item">开播</RouterLink>
        <RouterLink to="/anchor/activity" class="side-item">秒杀活动</RouterLink>
        <RouterLink to="/anchor/data" class="side-item">直播数据</RouterLink>
      </nav>
      <div class="side-foot">
        <RouterLink to="/" class="side-item side-item--dim">返回广场</RouterLink>
      </div>
    </aside>
    <main class="anchor-layout__main">
      <router-view v-slot="{ Component, route }">
        <transition name="route" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.anchor-layout { display: flex; min-height: 100vh; }
.anchor-layout__side {
  width: 220px;
  flex-shrink: 0;
  background: rgba(10, 11, 26, 0.82);
  border-right: 1px solid var(--border-faint);
  padding: 20px 14px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 0;
  height: 100vh;
  backdrop-filter: blur(14px);
}
.brand { display: flex; align-items: center; gap: 10px; font-family: var(--font-display); font-weight: 700; color: var(--text-strong); letter-spacing: 0.08em; }
.brand__mark { width: 28px; height: 28px; border-radius: 8px; background: linear-gradient(135deg, var(--neon-purple), var(--neon-cyan)); display: grid; place-items: center; color: #07081a; font-family: var(--font-num); font-weight: 900; font-size: 12px; }
nav { display: flex; flex-direction: column; gap: 4px; }
.side-item { padding: 10px 14px; border-radius: 8px; color: var(--text-muted); font-size: 14px; transition: all 0.2s; }
.side-item:hover { color: var(--text-strong); background: var(--bg-card); }
.side-item.router-link-active { color: var(--text-strong); background: var(--bg-card); border: 1px solid var(--border-soft); box-shadow: inset 2px 0 0 var(--neon-purple); }
.side-item--dim { color: var(--text-dim); }
.side-foot { margin-top: auto; }
.anchor-layout__main { flex: 1; padding: 24px 32px; min-width: 0; }
</style>
