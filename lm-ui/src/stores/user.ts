import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get, post, put, del } from '@/utils/http'
import { getDeviceId } from '@/utils/device'
import { useAuthStore } from './auth'
import type { UserProfile, DeviceInfo, UpdateProfileRequest, UpdatePasswordRequest } from '@/types/user'

export const useUserStore = defineStore('user', () => {
  const balance = ref(0)
  const devices = ref<DeviceInfo[]>([])

  const authStore = useAuthStore()
  const profile = computed(() => authStore.user)
  const nickname = computed(() => profile.value?.nickname || profile.value?.username || '')

  async function fetchProfile() {
    const res = await get<UserProfile>('/user/profile')
    authStore.setUser(res.data)
  }

  async function fetchBalance() {
    const res = await get<number>('/user/balance')
    balance.value = res.data
  }

  async function fetchDevices() {
    const res = await get<DeviceInfo[]>('/user/devices')
    const currentId = getDeviceId()
    devices.value = res.data.map(d => ({ ...d, current: d.deviceId === currentId }))
  }

  async function kickDevice(deviceId: string) {
    await del(`/user/devices/${deviceId}`)
    devices.value = devices.value.filter(d => d.deviceId !== deviceId)
  }

  async function updateProfile(req: UpdateProfileRequest) {
    const res = await put<UserProfile>('/user/profile', req)
    if (res.data) authStore.setUser(res.data)
    else await fetchProfile()
  }

  async function updatePassword(req: UpdatePasswordRequest) {
    await put('/user/password', req)
  }

  return { profile, nickname, balance, devices,
           fetchProfile, fetchBalance, fetchDevices, kickDevice, updateProfile, updatePassword }
})
