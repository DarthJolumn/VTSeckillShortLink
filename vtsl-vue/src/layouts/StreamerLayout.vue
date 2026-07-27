<template>
  <div class="streamer-layout">
    <header class="nav">
      <router-link to="/" class="logo">Live<span>Mall</span></router-link>
      <span class="crumb">主播中心</span>
      <router-link to="/" class="back-hall">返回大厅 →</router-link>
    </header>
    <div class="body">
      <aside class="side card">
        <router-link to="/streamer/panel" class="side-link" active-class="active">🎥 直播工作台</router-link>
        <router-link to="/streamer/seckill" class="side-link" active-class="active">⚡ 秒杀管理</router-link>
        <router-link to="/streamer/shortlink" class="side-link" active-class="active">🔗 短链管理</router-link>
      </aside>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.streamer-layout { min-height: 100vh; }
.nav {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--border);
}
.logo { font-size: 22px; font-weight: 800; }
.logo span { color: var(--accent-red); }
.crumb { color: var(--text-secondary); font-size: 14px; flex: 1; }
.back-hall { font-size: 13px; color: var(--text-secondary); }
.back-hall:hover { color: var(--text-primary); }
.body {
  display: flex;
  gap: 16px;
  padding: 24px;
  max-width: 1280px;
  margin: 0 auto;
  align-items: flex-start;
}
.side {
  width: 180px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}
.side-link {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--text-secondary);
  transition: background 0.15s, color 0.15s;
}
.side-link:hover { background: var(--bg-primary); color: var(--text-primary); }
.side-link.active { background: rgba(255, 44, 85, 0.12); color: var(--accent-red); }
.content {
  flex: 1;
  min-width: 0;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}
</style>
