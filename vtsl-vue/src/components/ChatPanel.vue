<template>
  <div class="chat-panel">
    <!-- 消息列表 -->
    <div ref="listRef" class="msg-list" @scroll="onScroll">
      <TransitionGroup name="msg">
        <div v-for="(m, i) in messages" :key="m.timestamp + '-' + i" class="msg-item" :class="m.kind">
          <template v-if="m.kind === 'barrage'">
            <span class="msg-user">{{ m.username }}：</span>
            <span class="msg-content">{{ m.content }}</span>
          </template>
          <template v-else>
            <span class="msg-gift">
              🎁 {{ m.username }} 送出 {{ m.giftName }} x{{ m.quantity }}
            </span>
          </template>
        </div>
      </TransitionGroup>
      <div v-if="messages.length === 0" class="msg-empty">暂无弹幕，来聊两句吧</div>
    </div>

    <div v-if="!pinned" class="new-tip" @click="scrollToBottom(true)">有新消息 ↓</div>

    <!-- 输入区 -->
    <div class="input-area">
      <input
        v-model="inputText"
        class="input-dark"
        :placeholder="authenticated ? '发个弹幕吧...' : '登录后参与互动'"
        :disabled="!authenticated"
        maxlength="100"
        @keyup.enter="onSend"
      />
      <button class="icon-btn" title="礼物" @click="showGifts = !showGifts">🎁</button>
      <button class="btn-primary send-btn" :disabled="!authenticated || !inputText.trim()" @click="onSend">发送</button>
    </div>

    <GiftPanel v-if="showGifts" @send="onGift" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import GiftPanel from './GiftPanel.vue'
import type { ChatItem } from '@/types/ws'

const props = defineProps<{
  messages: ChatItem[]
  authenticated: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
  sendGift: [giftId: number, quantity: number]
}>()

const inputText = ref('')
const showGifts = ref(false)
const listRef = ref<HTMLElement>()
/** 是否贴底（用户上翻历史时暂停自动滚底） */
const pinned = ref(true)

function onSend() {
  const text = inputText.value.trim()
  if (!text) return
  emit('send', text)
  inputText.value = ''
  scrollToBottom(true)
}

function onGift(giftId: number, quantity: number) {
  emit('sendGift', giftId, quantity)
  showGifts.value = false
}

function onScroll() {
  const el = listRef.value
  if (!el) return
  pinned.value = el.scrollHeight - el.scrollTop - el.clientHeight < 40
}

function scrollToBottom(force = false) {
  if (!force && !pinned.value) return
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
    pinned.value = true
  })
}

watch(() => props.messages.length, () => scrollToBottom())
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
.msg-item {
  font-size: 13px;
  line-height: 1.6;
  padding: 3px 8px;
  border-radius: 6px;
  word-break: break-all;
}
.msg-item.gift {
  color: var(--accent-gold);
  background: rgba(255, 214, 102, 0.06);
}
.msg-user { color: #7db8ff; }
.msg-empty {
  text-align: center;
  color: var(--text-secondary);
  font-size: 12px;
  padding: 40px 0;
}

.msg-enter-active { transition: all 0.25s ease; }
.msg-enter-from { opacity: 0; transform: translateY(12px); }

.new-tip {
  position: absolute;
  bottom: 64px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  background: var(--accent-red);
  padding: 4px 12px;
  border-radius: 12px;
  cursor: pointer;
}

.input-area {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--border);
  align-items: center;
}
.icon-btn { font-size: 18px; padding: 4px; }
.send-btn { padding: 8px 16px; white-space: nowrap; }
</style>
