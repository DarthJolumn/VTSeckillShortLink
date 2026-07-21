<template>
  <div class="gift-panel">
    <div
      v-for="g in gifts"
      :key="g.id"
      class="gift-item"
      :class="{ active: selected === g.id }"
      @click="selected = g.id"
    >
      <div class="gift-icon">{{ g.icon }}</div>
      <div class="gift-name">{{ g.name }}</div>
      <div class="gift-price">🪙 {{ g.price }}</div>
    </div>
    <button class="btn-primary gift-send" :disabled="!selected" @click="onSend">赠送</button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { GIFT_CATALOG } from '@/constants/gifts'

const emit = defineEmits<{ send: [giftId: number, quantity: number] }>()

const gifts = GIFT_CATALOG

const selected = ref<number | null>(null)

function onSend() {
  if (!selected.value) return
  emit('send', selected.value, 1)
}
</script>

<style scoped>
.gift-panel {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 12px;
  background: var(--bg-primary);
  border-top: 1px solid var(--border);
}
.gift-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: border-color 0.15s;
}
.gift-item:hover { border-color: var(--border); }
.gift-item.active { border-color: var(--accent-gold); background: rgba(255, 214, 102, 0.08); }
.gift-icon { font-size: 24px; }
.gift-name { font-size: 12px; color: var(--text-secondary); }
.gift-price { font-size: 11px; color: var(--accent-gold); }
.gift-send { grid-column: span 4; padding: 8px; }
</style>
