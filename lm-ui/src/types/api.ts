/** 统一响应包装（对应后端 Result<T>） */
export interface ApiResponse<T = null> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages?: number
}

/** 登录/刷新响应 */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
}
