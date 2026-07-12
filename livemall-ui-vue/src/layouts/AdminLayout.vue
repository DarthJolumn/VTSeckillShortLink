<template>
  <div class="admin-layout">
    <aside class="admin-layout__side">
      <RouterLink to="/" class="brand">
        <span class="brand__mark">LM</span>
        <span>管理后台</span>
      </RouterLink>
      <nav>
        <RouterLink to="/admin/dashboard" class="side-item">作战大屏</RouterLink>
        <RouterLink to="/admin/users" class="side-item">用户管理</RouterLink>
        <RouterLink to="/admin/activities" class="side-item">活动总览</RouterLink>
      </nav>
      <div class="side-foot">
        <RouterLink to="/" class="side-item side-item--dim">返回广场</RouterLink>
      </div>
    </aside>
    <main class="admin-layout__main">
      <router-view v-slot="{ Component, route }">
        <transition name="route" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; }
.admin-layout__side { width: 220px; flex-shrink: 0; background: rgba(7, 8, 26, 0.92); border-right: 1px solid var(--border-faint); padding: 20px 14px; display: flex; flex-direction: column; gap: 16px; position: sticky; top: 0; height: 100vh; backdrop-filter: blur(14px); }
.brand { display: flex; align-items: center; gap: 10px; font-family: var(--font-display); font-weight: 700; color: var(--text-strong); letter-spacing: 0.08em; }
.brand__mark { width: 28px; height: 28px; border-radius: 8px; background: linear-gradient(135deg, var(--neon-purple), var(--neon-cyan)); display: grid; place-items: center; color: #07081a; font-family: var(--font-num); font-weight: 900; font-size: 12px; }
nav { display: flex; flex-direction: column; gap: 4px; }
.side-item { padding: 10px 14px; border-radius: 8px; color: var(--text-muted); font-size: 14px; transition: all 0.2s; }
.side-item:hover { color: var(--text-strong); background: var(--bg-card); }
.side-item.router-link-active { color: var(--text-strong); background: var(--bg-card); border: 1px solid var(--border-soft); box-shadow: inset 2px 0 0 var(--neon-cyan); }
.side-item--dim { color: var(--text-dim); }
.side-foot { margin-top: auto; }
.admin-layout__main { flex: 1; padding: 24px 32px; min-width: 0; }
</style>
