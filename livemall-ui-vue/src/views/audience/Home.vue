<template>
  <div class="home container">
    <!-- Hero -->
    <section class="hero">
      <div class="hero__glow" aria-hidden="true" />
      <div class="hero__badge">
        <span class="dot anim-breathe" /> LIVE NOW · 直播中
      </div>
      <h1 class="hero__title">
        今晚 <span class="gradient-text">8 点</span> 开抢
      </h1>
      <p class="hero__subtitle">直播间 · 秒杀 · 排行榜 · 弹幕礼物 —— 全链路实时闭环</p>
      <div class="hero__countdown">
        <div class="cd__item"><span class="num">{{ cd.h }}</span><label>时</label></div>
        <div class="cd__sep">:</div>
        <div class="cd__item"><span class="num">{{ cd.m }}</span><label>分</label></div>
        <div class="cd__sep">:</div>
        <div class="cd__item"><span class="num">{{ cd.s }}</span><label>秒</label></div>
      </div>
    </section>

    <!-- 直播中卡片 -->
    <section class="section">
      <div class="section__head">
        <h2>直播中</h2>
        <span class="section__hint">实时同步 · WebSocket ONLINE_COUNT</span>
      </div>
      <div class="cards">
        <article v-for="r in rooms" :key="r.id" class="live-card glass" @click="goRoom(r.id)">
          <div class="live-card__cover">
            <div class="live-card__cover-inner" :style="{ background: r.color }">
              <span class="live-card__live"><span class="dot" /> LIVE</span>
              <span class="live-card__viewers"><span class="num">{{ r.viewers }}</span> 观看</span>
            </div>
          </div>
          <div class="live-card__body">
            <h3 class="ellipsis">{{ r.title }}</h3>
            <p>主播 · {{ r.anchor }}</p>
          </div>
        </article>
      </div>
    </section>

    <ComingSoon title="全模块已上线" desc="个人中心 / 订单 / 设备 / 主播台 / 直播数据 / 用户管理 / 活动总览 / 作战大屏 全部就绪" tag="ALL READY" />
  </div>
</template>

<script setup>
import { reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import ComingSoon from '@/components/base/ComingSoon.vue'

const router = useRouter()

const rooms = [
  { id: 1, title: '深夜数码秒杀局', anchor: 'NeonAnchor', viewers: 12800, color: 'linear-gradient(135deg,#6b4dff,#00e5ff)' },
  { id: 2, title: '美妆最后一小时', anchor: 'GlowQueen', viewers: 8630, color: 'linear-gradient(135deg,#ff4d8d,#ff8a00)' },
  { id: 3, title: '零食清仓大放送', anchor: 'SnackKing', viewers: 5210, color: 'linear-gradient(135deg,#52e5a4,#4cc9f0)' },
  { id: 4, title: '潮鞋限量首发', anchor: 'SneakerX', viewers: 21400, color: 'linear-gradient(135deg,#8a63ff,#ff7ad9)' },
]

const cd = reactive({ h: '02', m: '18', s: '42' })
let timer = null
function tick() {
  // 简易本地倒计时（演示用）
  let h = +cd.h, m = +cd.m, s = +cd.s - 1
  if (s < 0) { s = 59; m -= 1 }
  if (m < 0) { m = 59; h -= 1 }
  if (h < 0) { h = 0; m = 0; s = 0 }
  cd.h = String(h).padStart(2, '0')
  cd.m = String(m).padStart(2, '0')
  cd.s = String(s).padStart(2, '0')
}
onMounted(() => { timer = setInterval(tick, 1000) })
onBeforeUnmount(() => clearInterval(timer))

function goRoom(id) { router.push(`/live/${id}`) }
</script>

<style scoped>
.home { padding-top: 16px; }

/* Hero */
.hero { position: relative; padding: 40px 36px; border-radius: var(--radius-xl); overflow: hidden; border: 1px solid var(--border-soft); background: linear-gradient(120deg, rgba(20,22,48,0.85), rgba(10,11,26,0.6)); margin-bottom: 40px; }
.hero__glow { position: absolute; top: -60%; right: -10%; width: 60%; height: 200%; background: radial-gradient(closest-side, rgba(138,99,255,0.4), transparent 70%); filter: blur(30px); pointer-events: none; }
.hero__badge { display: inline-flex; align-items: center; gap: 8px; padding: 6px 12px; border-radius: 999px; border: 1px solid var(--border-strong); background: var(--bg-card); font-family: var(--font-display); font-size: 12px; letter-spacing: 0.18em; color: var(--text-strong); position: relative; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--seckill-from); box-shadow: 0 0 10px var(--seckill-from); display: inline-block; }
.hero__title { font-size: clamp(36px, 5vw, 56px); margin: 18px 0 10px; font-weight: 700; position: relative; }
.hero__subtitle { color: var(--text-muted); margin-bottom: 24px; position: relative; }
.hero__countdown { display: flex; align-items: center; gap: 10px; position: relative; }
.cd__item { display: flex; flex-direction: column; align-items: center; padding: 10px 16px; min-width: 70px; border-radius: var(--radius); background: rgba(7,8,26,0.6); border: 1px solid var(--border-soft); }
.cd__item .num { font-family: var(--font-num); font-size: 30px; font-weight: 700; color: var(--text-strong); line-height: 1; }
.cd__item label { font-size: 11px; letter-spacing: 0.2em; color: var(--text-dim); margin-top: 4px; }
.cd__sep { font-family: var(--font-num); font-size: 24px; color: var(--neon-purple); }

/* Section */
.section { margin-bottom: 40px; }
.section__head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 18px; }
.section__head h2 { font-size: 22px; }
.section__hint { font-size: 12px; color: var(--text-dim); font-family: var(--font-display); letter-spacing: 0.12em; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 18px; }
.live-card { cursor: pointer; overflow: hidden; transition: transform 0.25s var(--ease-out-expo), box-shadow 0.25s, border-color 0.25s; animation: float-up 0.5s var(--ease-out-expo) both; }
.live-card:hover { transform: translateY(-4px); border-color: var(--border-strong); box-shadow: 0 8px 32px rgba(0,0,0,0.4), 0 0 24px var(--neon-purple-soft); }
.live-card__cover { aspect-ratio: 16/10; }
.live-card__cover-inner { position: relative; width: 100%; height: 100%; display: flex; align-items: flex-end; justify-content: space-between; padding: 12px; }
.live-card__live { display: inline-flex; align-items: center; gap: 5px; padding: 4px 10px; border-radius: 6px; background: rgba(0,0,0,0.5); backdrop-filter: blur(4px); font-family: var(--font-display); font-size: 11px; letter-spacing: 0.14em; color: #fff; }
.live-card__live .dot { background: #fff; box-shadow: 0 0 8px #fff; }
.live-card__viewers { padding: 4px 10px; border-radius: 6px; background: rgba(0,0,0,0.5); backdrop-filter: blur(4px); font-size: 12px; color: #fff; }
.live-card__body { padding: 14px 16px; }
.live-card__body h3 { font-size: 16px; font-weight: 600; margin-bottom: 4px; }
.live-card__body p { font-size: 13px; color: var(--text-muted); }
</style>
