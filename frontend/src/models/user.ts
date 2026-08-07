import type { Address } from './address'

export interface User {
  id: string
  keycloakId: string
  username: string
  email: string
  firstName: string | null
  lastName: string | null
  shippingAddress: Address | null
  nationalId: string | null
  phone: string | null
  customAttributes: Record<string, string> | null
  createdAt: string | null
  updatedAt: string | null
  deleted: boolean
}
