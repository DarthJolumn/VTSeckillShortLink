import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import type { PushStatus, CameraDevice } from '@/types/webrtc'

/**
 * 推流/摄像头状态（B 端主播工作台）。
 * ⚠️ WebRTC 信令（WHEP SDP 握手）需媒体服务器联调后接入，
 * 当前 startPush/stopPush 只管理本地状态机 + 摄像头生命周期。
 */
export const usePusherStore = defineStore('pusher', () => {
  const status = ref<PushStatus>('IDLE')
  const pushDuration = ref(0) // 开播时长(s)
  // shallowRef：MediaStream 是原生对象，不能被 Vue 深度代理
  const localStream = shallowRef<MediaStream | null>(null)
  const devices = ref<CameraDevice[]>([])
  const currentDeviceId = ref('')
  const mirrored = ref(true) // 本地预览镜像（不影响推流画面）
  const sessionId = ref('')

  let pushTimer: ReturnType<typeof setInterval> | undefined

  /** 初始化摄像头（localhost 下无需 HTTPS；权限被拒抛 NotAllowedError） */
  async function initCamera(facingMode: 'user' | 'environment' = 'user') {
    status.value = 'CAMERA_INIT'
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720, facingMode },
      audio: { echoCancellation: true, noiseSuppression: true },
    })
    localStream.value = stream

    const deviceList = await navigator.mediaDevices.enumerateDevices()
    devices.value = deviceList
      .filter(d => d.kind === 'videoinput')
      .map((d, i) => ({
        deviceId: d.deviceId,
        label: d.label || `摄像头 ${i + 1}`,
        facing: facingMode,
      }))
    currentDeviceId.value = devices.value[0]?.deviceId || ''
    status.value = 'READY'
  }

  async function switchCamera() {
    if (!localStream.value) return
    stopTracks()
    await initCamera(devices.value[0]?.facing === 'user' ? 'environment' : 'user')
  }

  function stopTracks() {
    localStream.value?.getTracks().forEach(t => t.stop())
    localStream.value = null
  }

  function startTimer() {
    stopTimer()
    pushDuration.value = 0
    pushTimer = setInterval(() => pushDuration.value++, 1000)
  }

  function stopTimer() {
    if (pushTimer) { clearInterval(pushTimer); pushTimer = undefined }
    pushDuration.value = 0
  }

  function markPushing(sid = '') {
    sessionId.value = sid
    status.value = 'PUSHING'
    startTimer()
  }

  function markError() {
    status.value = 'ERROR'
    stopTimer()
  }

  function reset() {
    stopTracks()
    stopTimer()
    status.value = 'IDLE'
    sessionId.value = ''
  }

  return { status, pushDuration, localStream, devices, currentDeviceId, mirrored, sessionId,
           initCamera, switchCamera, stopTracks, startTimer, stopTimer,
           markPushing, markError, reset }
})
