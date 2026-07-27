export interface UserProfile {
  id: number
  username: string
  nickname: string
  avatar: string
  phone: string
  role: number    // 1=观众 2=主播 3=管理员
  status: number  // 0=封禁 1=正常
}

export interface DeviceInfo {
  deviceId: string
  current: boolean
}

export interface UpdateProfileRequest {
  nickname?: string
  avatar?: string
  phone?: string
}

export interface UpdatePasswordRequest {
  oldPassword: string
  newPassword: string  // ≥8
}
