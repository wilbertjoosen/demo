export interface Product {
  id: string
  sku: string
  name: string
  price: number
  createdAt: string | null
  updatedAt: string | null
  createdBy: string | null
  lastModifiedBy: string | null
  deleted: boolean
}
