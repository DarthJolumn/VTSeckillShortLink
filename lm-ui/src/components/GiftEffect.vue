<template>
  <!-- 普通礼物：左侧横幅（滑入，最多叠 3 条） -->
  <div class="gift-banner-wrap">
    <TransitionGroup name="banner">
      <div v-for="g in normalGifts" :key="g.id" class="gift-banner">
        <span class="gb-icon">{{ g.giftIcon }}</span>
        <span class="gb-text">
          <b>{{ g.username }}</b> 送出 <b>{{ g.giftName }}</b>
        </span>
        <span class="gb-qty">x{{ g.quantity }}</span>
      </div>
    </TransitionGroup>
  </div>

  <!-- 豪华礼物（gain ≥ 500）：全屏撒花 -->
  <Teleport to="body">
    <div v-if="bigGift" :key="bigGift.id" class="big-gift-mask">
      <div class="particles">
        <span
          v-for="(p, i) in particles"
          :key="i"
          class="particle"
          :style="{ left: p.left + '%', animationDelay: p.delay + 's', fontSize: p.size + 'px' }"
        >{{ p.emoji }}</span>
      </div>
      <div class="big-gift-center">
        <div class="big-icon">{{ bigGift.giftIcon }}</div>
        <div class="big-text">
          <b>{{ bigGift.username }}</b> 豪气送出 <b>{{ bigGift.giftName }}</b> x{{ bigGift.quantity }}
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { giftEffectState } from '@/utils/giftEffect'

/** 豪华礼物阈值：总价 ≥ 500 触发全屏特效 */
const BIG_GIFT_THRESHOLD = 500

const normalGifts = computed(() =>
  giftEffectState.list.filter(g => g.gain < BIG_GIFT_THRESHOLD).slice(-3),
)
const bigGift = computed(() =>
  giftEffectState.list.find(g => g.gain >= BIG_GIFT_THRESHOLD) ?? null,
)

// 撒花粒子（每次豪华礼物出现时重新生成）
const particles = computed(() => {
  if (!bigGift.value) return []
  const emojis = ['🎉', '✨', '🎊', '💰', '🪙', '⭐']
  return Array.from({ length: 24 }, () => ({
    left: Math.random() * 100,
    delay: Math.random() * 1.2,
    size: 16 + Math.random() * 20,
    emoji: emojis[Math.floor(Math.random() * emojis.length)],
  }))
})
</script>

<style scoped>
/* ===== 普通横幅 ===== */
.gift-banner-wrap {
  position: absolute;
  top: 16px;
  left: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 20;
  pointer-events: none;
}
.gift-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px 6px 8px;
  border-radius: 20px;
  background: linear-gradient(90deg, rgba(255, 214, 102, 0.25), rgba(255, 44, 85, 0.15));
  border: 1px solid rgba(255, 214, 102, 0.4);
  backdrop-filter: blur(4px);
  font-size: 13px;
}
.gb-icon { font-size: 22px; animation: bounce 0.6s ease; }
.gb-text b { color: var(--accent-gold); }
.gb-qty {
  font-family: var(--font-mono);
  color: var(--accent-gold);
  font-weight: 700;
}

.banner-enter-active { transition: all 0.3s ease; }
.banner-leave-active { transition: all 0.25s ease; }
.banner-enter-from { opacity: 0; transform: translateX(-40px); }
.banner-leave-to { opacity: 0; transform: translateY(-10px); }

@keyframes bounce {
  0% { transform: scale(0.3); }
  50% { transform: scale(1.4); }
  100% { transform: scale(1); }
}

/* ===== 全屏撒花 ===== */
.big-gift-mask {
  position: fixed;
  inset: 0;
  z-index: 5000;
  pointer-events: none;
  background: radial-gradient(ellipse at center, rgba(255, 214, 102, 0.12), transparent 60%);
  animation: mask-fade 3.2s ease forwards;
}
@keyframes mask-fade {
  0% { opacity: 0; }
  10% { opacity: 1; }
  80% { opacity: 1; }
  100% { opacity: 0; }
}
.particles { position: absolute; inset: 0; overflow: hidden; }
.particle {
  position: absolute;
  top: -40px;
  animation: fall 2.8s ease-in forwards;
}
@keyframes fall {
  to { transform: translateY(110vh) rotate(360deg); }
}
.big-gift-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
}
.big-icon {
  font-size: 96px;
  animation: big-pop 0.7s cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes big-pop {
  0% { transform: scale(0) rotate(-30deg); }
  100% { transform: scale(1) rotate(0); }
}
.big-text {
  margin-top: 12px;
  font-size: 20px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.8);
}
.big-text b { color: var(--accent-gold); }
</style>
