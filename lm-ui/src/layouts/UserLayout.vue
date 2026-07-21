<template>
  <div class="user-layout">
    <header class="nav">
      <router-link to="/" class="logo">Live<span>Mall</span></router-link>
      <span class="crumb">个人中心</span>
    </header>
    <div class="body">
      <aside class="side card">
        <router-link to="/user/profile" class="side-link" active-class="active">👤 个人信息</router-link>
        <router-link to="/user/orders" class="side-link" active-class="active">📦 我的订单</router-link>
        <router-link to="/user/devices" class="side-link" active-class="active">📱 设备管理</router-link>
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
.user-layout { min-height: 100vh; }
.nav {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--border);
}
.logo { font-size: 22px; font-weight: 800; }
.logo span { color: var(--accent-red); }
.crumb { color: var(--text-secondary); font-size: 14px; }
.body {
  display: flex;
  gap: 16px;
  padding: 24px;
  max-width: 1080px;
  margin: 0 auto;
  align-items: flex-start;
}
.side {
  width: 200px;
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
.content { flex: 1; min-width: 0; }
</style>
