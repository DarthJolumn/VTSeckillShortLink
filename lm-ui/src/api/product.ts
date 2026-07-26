import { get, post, put, del } from '@/utils/http'
import type { ProductDTO } from '@/types/product'
import type { PageResult } from '@/types/api'

export interface ProductListParams {
  page?: number
  size?: number
  userId?: number
  categoryId?: number
  sortBy?: 'createdAt' | 'price'
  sortDir?: 'asc' | 'desc'
}

export interface ProductPublishCmd {
  title: string
  subtitle?: string
  mainImage?: string
  detailImages?: string
  price: number
  stock: number
  categoryId?: number
}

export interface ProductUpdateCmd {
  title?: string
  subtitle?: string
  mainImage?: string
  detailImages?: string
  price?: number
  stock?: number
  categoryId?: number
}

export function listProducts(params?: ProductListParams) {
  return get<PageResult<ProductDTO>>('/product/list', { params })
}

export function getProductById(id: number) {
  return get<ProductDTO>(`/product/${id}`)
}

export function publishProduct(cmd: ProductPublishCmd) {
  return post<number>('/product/publish', cmd)
}

export function updateProduct(id: number, cmd: ProductUpdateCmd) {
  return put<void>(`/product/${id}`, cmd)
}

export function updateProductStatus(id: number, status: 0 | 1) {
  return put<void>(`/product/${id}/status`, { status })
}

export function deleteProduct(id: number) {
  return del<void>(`/product/${id}`)
}
