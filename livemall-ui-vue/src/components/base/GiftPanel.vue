<template>
  <transition name="drawer">
    <aside v-if="open" class="gift-drawer" role="dialog" aria-label="礼物面板">
      <!-- 遮罩 -->
      <div class="gift-drawer__mask" @click="$emit('close')" />

      <!-- 抽屉本体 -->
      <div class="gift-drawer__panel glass">
        <header class="gd-head">
          <div class="gd-head__left">
            <h3>🎁 礼物商城</h3>
            <span class="gd-head__hint">送礼加榜分 · 大礼物全屏特效</span>
          </div>
          <button class="gd-head__close" @click="$emit('close')" aria-label="关闭">✕</button>
        </header>

        <!-- 余额 + 连击 -->
        <div class="gd-balance">
          <div class="gd-balance__item">
            <span class="gd-balance__label">钱包余额</span>
            <span class="gd-balance__value num">¥{{ balance.toLocaleString() }}</span>
          </div>
          <div class="gd-balance__item gd-balance__item--combo" :class="{ 'is-active': comboCount > 1 }">
            <span class="gd-balance__label">连击</span>
            <span class="gd-balance__value num">×{{ comboCount }}</span>
          </div>
        </div>

        <!-- 分类切换 -->
        <div class="gd-cats">
          <button v-for="c in categories" :key="c.key"
            class="gd-cat" :class="{ 'is-on': activeCat === c.key }"
            @click="activeCat = c.key">
            <span class="gd-cat__icon">{{ c.icon }}</span>
            <span class="gd-cat__label">{{ c.label }}</span>
          </button>
        </div>

        <!-- 礼物网格 -->
        <div class="gd-grid">
          <button v-for="g in filteredGifts" :key="g.id"
            class="gd-gift" :class="{ 'is-selected': selectedId === g.id, 'is-big': g.price >= 100 }"
            @click="onSelect(g)" @dblclick="onQuickSend(g)">
            <div class="gd-gift__icon">{{ g.icon }}</div>
            <div class="gd-gift__name">{{ g.name }}</div>
            <div class="gd-gift__price num">¥{{ g.price }}</div>
            <span v-if="g.price >= 100" class="gd-gift__big-tag">BIG</span>
          </button>
        </div>

        <!-- 数量选择 -->
        <div class="gd-qty">
          <span class="gd-qty__label">数量</span>
          <div class="gd-qty__picks">
            <button v-for="q in qtyOptions" :key="q"
              class="gd-qty__btn" :class="{ 'is-on': quantity === q }"
              @click="quantity = q">×{{ q }}</button>
          </div>
          <div class="gd-qty__custom">
            <button class="gd-qty__step" @click="stepQty(-1)" :disabled="quantity <= 1">−</button>
            <input type="number" v-model.number="quantity" min="1" max="999" class="gd-qty__input" />
            <button class="gd-qty__step" @click="stepQty(1)" :disabled="quantity >= 999">+</button>
          </div>
        </div>

        <!-- 预览 + 发送 -->
        <footer class="gd-foot">
          <div class="gd-foot__preview">
            <template v-if="selected">
              <span class="gd-foot__icon">{{ selected.icon }}</span>
              <span class="gd-foot__name">{{ selected.name }}</span>
              <span class="gd-foot__qty">×{{ quantity }}</span>
              <span class="gd-foot__total num">= ¥{{ (selected.price * quantity).toLocaleString() }}</span>
            </template>
            <span v-else class="gd-foot__empty">请选择礼物</span>
          </div>
          <button class="gd-foot__send" :disabled="!selected || sending"
            @click="onSend" @mousedown="onPressStart" @mouseup="onPressEnd" @mouseleave="onPressEnd">
            <span v-if="sending" class="gd-foot__combo">×{{ comboCount }}</span>
            <span v-else>{{ selected ? '发送' : '请选择' }}</span>
          </button>
        </footer>

        <!-- 提示 -->
        <p class="gd-tip">💡 双击礼物图标可快速送出 ×1 · 长按发送键进入连击模式 · 余额不足时自动提示</p>
      </div>
    </aside>
  </transition>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { showToast } from '@/utils/toast'

const props = defineProps({
  open: { type: Boolean, default: false },
  balance: { type: Number, default: 8888 },
})
const emit = defineEmits(['close', 'send'])

// —— 礼物数据 ——
const ALL_GIFTS = [
  { id: 1, name: '点赞',  price: 1,   icon: '👍', cat: 'common' },
  { id: 2, name: '玫瑰',  price: 9,   icon: '🌹', cat: 'common' },
  { id: 3, name: '爱心',  price: 18,  icon: '💖', cat: 'common' },
  { id: 4, name: '棒棒糖', price: 6,  icon: '🍭', cat: 'common' },
  { id: 5, name: '蛋糕',  price: 52,  icon: '🎂', cat: 'common' },
  { id: 6, name: '皇冠',  price: 88,  icon: '👑', cat: 'luxury' },
  { id: 7, name: '跑车',  price: 120, icon: '🏎️', cat: 'luxury' },
  { id: 8, name: '游艇',  price: 388, icon: '🛥️', cat: 'luxury' },
  { id: 9, name: '火箭',  price: 666, icon: '🚀', cat: 'luxury' },
  { id: 10, name: '城堡', price: 1888, icon: '🏰', cat: 'luxury' },
]

const categories = [
  { key: 'all',     label: '全部', icon: '🎁' },
  { key: 'common',  label: '常用', icon: '💝' },
  { key: 'luxury',  label: '奢华', icon: '💎' },
]
const activeCat = ref('all')
const filteredGifts = computed(() => {
  if (activeCat.value === 'all') return ALL_GIFTS
  return ALL_GIFTS.filter(g => g.cat === activeCat.value)
})

// —— 选择 + 数量 ——
const selectedId = ref(2)
const selected = computed(() => ALL_GIFTS.find(g => g.id === selectedId.value))
const quantity = ref(1)
const qtyOptions = [1, 6, 18, 66, 99, 188, 520]

function stepQty(delta) {
  quantity.value = Math.max(1, Math.min(999, quantity.value + delta))
}
function onSelect(g) {
  selectedId.value = g.id
}

// —— 连击 ——
const sending = ref(false)
const comboCount = ref(1)
let comboTimer = null
let pressTimer = null

function clearCombo() {
  if (comboTimer) { clearTimeout(comboTimer); comboTimer = null }
  comboCount.value = 1
}

// 长按发送键进入连击
function onPressStart() {
  pressTimer = setInterval(() => {
    doSend(true)
  }, 180)
}
function onPressEnd() {
  if (pressTimer) { clearInterval(pressTimer); pressTimer = null }
}

// 双击快速送出 ×1
function onQuickSend(g) {
  selectedId.value = g.id
  quantity.value = 1
  doSend(false)
}

function onSend() {
  doSend(false)
}

function doSend(isCombo) {
  if (!selected.value) {
    showToast('请先选择礼物', 'warning')
    return
  }
  const g = selected.value
  const qty = isCombo ? 1 : quantity.value
  const total = g.price * qty
  if (total > props.balance) {
    showToast(`余额不足（需 ¥${total}）`, 'warning')
    return
  }
  emit('send', { gift: g, quantity: qty })
  showToast(`送出 ${g.name} ×${qty}${isCombo ? ` 连击 ×${comboCount.value}` : ''}`, 'success')

  if (isCombo) {
    comboCount.value += 1
    if (comboTimer) clearTimeout(comboTimer)
    comboTimer = setTimeout(clearCombo, 1200) // 1.2s 内无操作重置连击
  } else {
    clearCombo()
  }
}

// 抽屉关闭时重置连击
watch(() => props.open, (v) => {
  if (!v) clearCombo()
})
onUnmounted(() => {
  if (comboTimer) clearTimeout(comboTimer)
  if (pressTimer) clearInterval(pressTimer)
})
</script>

<style scoped>
.gift-drawer {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal, 1000);
  pointer-events: none;
}
.gift-drawer__mask {
  position: absolute;
  inset: 0;
  background: rgba(7, 8, 26, 0.55);
  backdrop-filter: blur(4px);
  pointer-events: auto;
}
.gift-drawer__panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(420px, 92vw);
  background: rgba(10, 11, 26, 0.96);
  border-left: 1px solid var(--border-soft);
  box-shadow: -16px 0 48px rgba(0, 0, 0, 0.6), inset 1px 0 0 rgba(138, 99, 255, 0.2);
  display: flex;
  flex-direction: column;
  padding: 18px 20px;
  gap: 14px;
  pointer-events: auto;
  overflow-y: auto;
}

/* —— 头部 —— */
.gd-head { display: flex; align-items: flex-start; justify-content: space-between; }
.gd-head__left { display: flex; flex-direction: column; gap: 2px; }
.gd-head h3 { margin: 0; font-family: var(--font-display); font-size: 18px; color: var(--text-strong); letter-spacing: 0.06em; }
.gd-head__hint { font-size: 11px; color: var(--text-dim); }
.gd-head__close {
  width: 30px; height: 30px;
  border-radius: 8px;
  border: 1px solid var(--border-faint);
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.gd-head__close:hover { color: var(--danger); border-color: var(--danger); background: rgba(255, 84, 112, 0.08); }

/* —— 余额 —— */
.gd-balance {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
}
.gd-balance__item {
  padding: 10px 14px;
  border-radius: var(--radius);
  background: rgba(7, 8, 26, 0.6);
  border: 1px solid var(--border-faint);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.gd-balance__item--combo { align-items: flex-end; min-width: 90px; }
.gd-balance__item--combo.is-active {
  border-color: var(--danger);
  box-shadow: 0 0 12px rgba(255, 84, 112, 0.3);
  animation: combo-pulse 0.3s ease;
}
@keyframes combo-pulse {
  0%   { transform: scale(1); }
  50%  { transform: scale(1.05); }
  100% { transform: scale(1); }
}
.gd-balance__label { font-size: 11px; color: var(--text-dim); }
.gd-balance__value { font-family: var(--font-num); font-size: 18px; font-weight: 700; color: var(--warning); }
.gd-balance__item--combo .gd-balance__value { color: var(--text-strong); }
.gd-balance__item--combo.is-active .gd-balance__value { color: var(--danger); }

/* —— 分类 —— */
.gd-cats { display: flex; gap: 6px; }
.gd-cat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  border-radius: var(--radius);
  background: rgba(7, 8, 26, 0.4);
  border: 1px solid var(--border-faint);
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.gd-cat:hover { color: var(--text); border-color: var(--border-soft); }
.gd-cat.is-on {
  color: var(--text-strong);
  background: rgba(138, 99, 255, 0.16);
  border-color: var(--neon-purple);
  box-shadow: 0 0 12px var(--neon-purple-soft);
}
.gd-cat__icon { font-size: 18px; }
.gd-cat__label { font-size: 11px; letter-spacing: 0.06em; }

/* —— 礼物网格 —— */
.gd-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
  padding: 2px;
}
.gd-gift {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 4px 8px;
  border-radius: var(--radius);
  background: rgba(7, 8, 26, 0.5);
  border: 1px solid var(--border-faint);
  cursor: pointer;
  transition: all 0.2s var(--ease-out-expo);
}
.gd-gift:hover {
  transform: translateY(-2px);
  border-color: var(--border-soft);
  background: rgba(138, 99, 255, 0.08);
}
.gd-gift.is-selected {
  border-color: var(--neon-purple);
  background: rgba(138, 99, 255, 0.14);
  box-shadow: 0 0 16px var(--neon-purple-soft), inset 0 0 0 1px var(--neon-purple);
}
.gd-gift.is-big::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: var(--radius);
  background: linear-gradient(135deg, rgba(255, 84, 112, 0.08), rgba(255, 138, 0, 0.08));
  pointer-events: none;
}
.gd-gift__icon { font-size: 28px; line-height: 1; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.4)); }
.gd-gift__name { font-size: 11px; color: var(--text); }
.gd-gift__price { font-size: 11px; color: var(--warning); font-weight: 700; }
.gd-gift__big-tag {
  position: absolute;
  top: 3px;
  right: 3px;
  padding: 1px 5px;
  border-radius: 3px;
  background: linear-gradient(135deg, var(--danger), var(--warning));
  color: #fff;
  font-family: var(--font-num);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

/* —— 数量 —— */
.gd-qty {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-radius: var(--radius);
  background: rgba(7, 8, 26, 0.4);
  border: 1px solid var(--border-faint);
}
.gd-qty__label { font-size: 12px; color: var(--text-dim); letter-spacing: 0.08em; }
.gd-qty__picks { display: flex; gap: 4px; flex-wrap: wrap; }
.gd-qty__btn {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-num);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.gd-qty__btn:hover { color: var(--text); background: rgba(138, 99, 255, 0.08); }
.gd-qty__btn.is-on {
  color: #fff;
  background: var(--neon-purple);
  border-color: var(--neon-purple);
  box-shadow: 0 0 8px var(--neon-purple-soft);
}
.gd-qty__custom { display: flex; align-items: center; gap: 4px; }
.gd-qty__step {
  width: 26px; height: 26px;
  border-radius: 6px;
  border: 1px solid var(--border-faint);
  background: transparent;
  color: var(--text);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.gd-qty__step:hover:not(:disabled) { color: var(--neon-cyan); border-color: var(--neon-cyan); }
.gd-qty__step:disabled { opacity: 0.3; cursor: not-allowed; }
.gd-qty__input {
  width: 48px; height: 26px;
  text-align: center;
  background: rgba(7, 8, 26, 0.6);
  border: 1px solid var(--border-faint);
  border-radius: 6px;
  color: var(--text-strong);
  font-family: var(--font-num);
  font-size: 13px;
  outline: none;
}
.gd-qty__input:focus { border-color: var(--neon-purple); }

/* —— 底部 —— */
.gd-foot {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid var(--border-faint);
}
.gd-foot__preview {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  min-width: 0;
  flex-wrap: wrap;
}
.gd-foot__icon { font-size: 20px; }
.gd-foot__name { color: var(--text-strong); font-weight: 600; }
.gd-foot__qty { color: var(--text-muted); }
.gd-foot__total { color: var(--warning); font-weight: 700; }
.gd-foot__empty { color: var(--text-dim); font-size: 12px; }
.gd-foot__send {
  min-width: 96px;
  height: 44px;
  padding: 0 20px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, var(--danger), var(--warning));
  color: #fff;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 14px;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: filter 0.2s, transform 0.16s;
  box-shadow: 0 0 20px rgba(255, 84, 112, 0.4);
  user-select: none;
}
.gd-foot__send:hover:not(:disabled) { filter: brightness(1.12); }
.gd-foot__send:active:not(:disabled) { transform: translateY(1px) scale(0.98); }
.gd-foot__send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  box-shadow: none;
}
.gd-foot__combo {
  font-family: var(--font-num);
  font-size: 18px;
  font-weight: 900;
  animation: combo-bump 0.2s ease;
}
@keyframes combo-bump {
  0%   { transform: scale(0.8); }
  60%  { transform: scale(1.2); }
  100% { transform: scale(1); }
}

.gd-tip {
  margin: 0;
  font-size: 11px;
  color: var(--text-dim);
  line-height: 1.5;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(0, 229, 255, 0.04);
  border-left: 2px solid var(--neon-cyan);
}

/* —— 滚动条 —— */
.gd-grid::-webkit-scrollbar { width: 4px; }
.gd-grid::-webkit-scrollbar-thumb { background: rgba(138, 99, 255, 0.3); border-radius: 2px; }

/* —— 抽屉转场 —— */
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.3s var(--ease-out-expo);
}
.drawer-enter-active .gift-drawer__panel,
.drawer-leave-active .gift-drawer__panel {
  transition: transform 0.35s var(--ease-out-expo);
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}
.drawer-enter-from .gift-drawer__panel,
.drawer-leave-to .gift-drawer__panel {
  transform: translateX(100%);
}

.num { font-family: var(--font-num); }
</style>
