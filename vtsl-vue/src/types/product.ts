export interface ProductDTO {
  id: number
  userId: number
  title: string
  subtitle?: string
  mainImage?: string
  detailImages?: string
  price: number
  stock: number
  status: number
  categoryId?: number
  shareUrl: string
  createdAt: string
  updatedAt: string
}
