<template>
  <div class="pusher card">
    <!-- 预览区 -->
    <div class="preview">
      <video
        ref="videoRef"
        autoplay
        playsinline
        muted
        :class="{ mirrored: pusher.mirrored }"
      />
      <div v-if="!pusher.localStream" class="preview-empty">
        <span class="cam-icon">📷</span>
        <p>{{ emptyText }}</p>
      </div>
      <div class="status-badge" :class="statusClass">{{ statusText }}</div>
      <div v-if="pusher.status === 'PUSHING'" class="duration">{{ durationText }}</div>
    </div>

    <!-- 控制区 -->
    <div class="controls">
      <template v-if="pusher.status === 'IDLE' || pusher.status === 'ERROR'">
        <button class="btn-primary" :disabled="initializing" @click="onInitCamera">
          {{ initializing ? '初始化中...' : '开启摄像头' }}
        </button>
      </template>
      <template v-else>
        <button class="btn-ghost" @click="pusher.switchCamera()">🔄 翻转</button>
        <button class="btn-ghost" @click="pusher.mirrored = !pusher.mirrored">
          {{ pusher.mirrored ? '关闭镜像' : '开启镜像' }}
        </button>
        <button v-if="!pushing" class="btn-primary" @click="emit('start')">开始直播</button>
        <button v-else class="btn-stop" @click="emit('stop')">结束直播</button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { usePusherStore } from '@/stores/pusher'
import { showToast } from '@/utils/toast'

const props = defineProps<{ pushing: boolean }>()
const emit = defineEmits<{ start: []; stop: [] }>()

const pusher = usePusherStore()
const videoRef = ref<HTMLVideoElement>()
const initializing = ref(false)

const emptyText = computed(() =>
  pusher.status === 'ERROR' ? '摄像头异常，请检查权限后重试' : '摄像头未开启',
)

const statusText = computed(() => ({
  IDLE: '未开播',
  CAMERA_INIT: '初始化中',
  READY: '待开播',
  CONNECTING: '连接中',
  PUSHING: '● 直播中',
  ERROR: '异常',
} as Record<string, string>)[pusher.status])

const statusClass = computed(() => ({
  PUSHING: 'st-live',
  ERROR: 'st-error',
} as Record<string, string>)[pusher.status] ?? 'st-idle')

const durationText = computed(() => {
  const s = pusher.pushDuration
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`
})

// stream → video 绑定
watch([() => pusher.localStream, videoRef], ([stream, video]) => {
  if (video && stream) video.srcObject = stream
}, { immediate: true, flush: 'post' })

async function onInitCamera() {
  initializing.value = true
  try {
    await pusher.initCamera()
  } catch (e) {
    pusher.markError()
    if (e instanceof DOMException && e.name === 'NotAllowedError') {
      showToast('请允许摄像头权限后重试', 'warning')
    } else if (e instanceof DOMException && e.name === 'NotFoundError') {
      showToast('未检测到摄像头设备', 'error')
    } else {
      showToast('摄像头初始化失败', 'error')
    }
  } finally {
    initializing.value = false
  }
}

onUnmounted(() => {
  pusher.stopTracks()
})
</script>

<style scoped>
.pusher { overflow: hidden; }
.preview {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #000;
}
video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
video.mirrored { transform: scaleX(-1); } /* 仅本地预览镜像，不影响推流 */
.preview-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-secondary);
}
.cam-icon { font-size: 40px; opacity: 0.4; }
.preview-empty p { font-size: 13px; }

.status-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.6);
}
.st-live { color: var(--accent-red); animation: blink 1.2s infinite; }
.st-error { color: var(--accent-gold); }
.st-idle { color: var(--text-secondary); }
@keyframes blink { 50% { opacity: 0.4; } }

.duration {
  position: absolute;
  top: 12px;
  right: 12px;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--accent-gold);
  background: rgba(0, 0, 0, 0.6);
  padding: 4px 10px;
  border-radius: 4px;
}

.controls {
  display: flex;
  gap: 10px;
  padding: 14px;
  justify-content: center;
}
.controls .btn-ghost { padding: 8px 16px; font-size: 13px; }
.btn-stop {
  background: var(--accent-red);
  color: #fff;
  border-radius: 8px;
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
}
.btn-stop:hover { opacity: 0.9; }
</style>
