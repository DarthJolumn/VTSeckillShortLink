<template>
  <div class="live-player">
    <video
      ref="videoRef"
      class="player-video"
      autoplay
      playsinline
      controls
    />
    <div v-if="loading" class="player-overlay">
      <span class="loading-icon">▶</span>
      <p>正在加载直播流...</p>
    </div>
    <div v-if="error" class="player-overlay error">
      <span class="error-icon">⚠</span>
      <p>{{ error }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import Hls from 'hls.js'

const props = defineProps<{
  streamUrl: string
}>()

const videoRef = ref<HTMLVideoElement>()
const loading = ref(true)
const error = ref<string | null>(null)
let hls: Hls | null = null

function initPlayer() {
  if (!videoRef.value) return

  // 浏览器原生支持 HLS (Safari)
  if (videoRef.value.canPlayType('application/vnd.apple.mpegurl')) {
    videoRef.value.src = props.streamUrl
    loading.value = false
    return
  }

  // 不支持 HLS
  if (!Hls.isSupported()) {
    error.value = '浏览器不支持 HLS 播放'
    loading.value = false
    return
  }

  // hls.js 播放
  hls = new Hls({
    enableWorker: true,
    lowLatencyMode: true,
  })

  hls.loadSource(props.streamUrl)
  hls.attachMedia(videoRef.value)

  hls.on(Hls.Events.MANIFEST_PARSED, () => {
    loading.value = false
    videoRef.value?.play().catch(() => {
      // 自动播放被阻止，显示 controls 让用户手动点
    })
  })

  hls.on(Hls.Events.ERROR, (_, data) => {
    if (data.fatal) {
      switch (data.type) {
        case Hls.ErrorTypes.NETWORK_ERROR:
          error.value = '直播流加载失败（网络错误）'
          break
        case Hls.ErrorTypes.MEDIA_ERROR:
          error.value = '播放异常，尝试恢复...'
          hls?.recoverMediaError()
          break
        default:
          error.value = '播放失败'
          hls?.destroy()
          break
      }
      loading.value = false
    }
  })
}

watch(() => props.streamUrl, (newUrl) => {
  if (hls) {
    hls.destroy()
    hls = null
  }
  error.value = null
  loading.value = true
  initPlayer()
})

onMounted(initPlayer)

onUnmounted(() => {
  if (hls) {
    hls.destroy()
    hls = null
  }
})
</script>

<style scoped>
.live-player {
  position: relative;
  width: 100%;
  height: 100%;
  background: #000;
  overflow: hidden;
}

.player-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.player-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  gap: 12px;
}

.loading-icon {
  font-size: 48px;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

.error {
  background: rgba(255, 44, 85, 0.9);
}

.error-icon {
  font-size: 48px;
}
</style>
