export interface RegisterRequest {
  username: string   // 4~50
  password: string   // ≥8
  phone?: string     // ^1[3-9]\d{9}$
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken: string
}
