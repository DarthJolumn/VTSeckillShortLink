export interface ShortLinkVO {
  id: number
  shortCode: string
  shortUrl: string
  title: string
  productId: number
  originalUrl: string
  clickCount: number
  status: number
  createdAt: string
  expireAt: string
}

export interface CreateShortLinkRequest {
  productId: number
  originalUrl: string
  title?: string
}

export interface ShortLinkListData {
  records: ShortLinkVO[]
  total: number
  page: number
  size: number
}
