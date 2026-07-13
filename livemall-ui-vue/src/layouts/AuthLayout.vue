<template>
  <div class="layout layout--auth">
    <!-- 静态径向渐变背景（替代原 ParticleBg Canvas，零 CPU 开销） -->
    <div class="auth-bg" aria-hidden="true">
      <div class="auth-bg__glow auth-bg__glow--purple" />
      <div class="auth-bg__glow auth-bg__glow--cyan" />
      <div class="auth-bg__grid" />
    </div>
    <main class="layout__main">
      <slot />
    </main>
  </div>
</template>

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
.auth-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: var(--bg-deep);
}
/* 两团静态光晕（纯 CSS，无动画无计算） */
.auth-bg__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
}
.auth-bg__glow--purple {
  width: 600px; height: 600px;
  top: -10%; left: -10%;
  background: radial-gradient(circle, var(--neon-purple) 0%, transparent 70%);
}
.auth-bg__glow--cyan {
  width: 500px; height: 500px;
  bottom: -15%; right: -10%;
  background: radial-gradient(circle, var(--neon-cyan) 0%, transparent 70%);
}
/* 静态网格底纹 */
.auth-bg__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(138,99,255,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(138,99,255,0.04) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 80%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 30%, transparent 80%);
}
.layout__main { position: relative; z-index: var(--z-card); width: 100%; display: flex; justify-content: center; }
</style>
