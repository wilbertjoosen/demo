import { http } from './http'
import { unwrapCollection } from './hal'
import type { Address, User } from '../models'

export interface ProfileUpdate {
  displayName?: string
  shippingAddress?: Address
  nationalId?: string
  phone?: string
  customAttributes?: Record<string, string>
}

export interface CreateUserPayload extends ProfileUpdate {
  keycloakId: string
  username: string
  email: string
}

export const usersApi = {
  async me(): Promise<User> {
    const { data } = await http.get('/api/users/me')
    return data
  },
  async updateMe(payload: ProfileUpdate): Promise<User> {
    const { data } = await http.put('/api/users/me', payload)
    return data
  },
  async list(): Promise<User[]> {
    const { data } = await http.get('/api/users')
    return unwrapCollection<User>(data)
  },
  async create(payload: CreateUserPayload): Promise<User> {
    const { data } = await http.post('/api/users', payload)
    return data
  },
  async get(id: string): Promise<User> {
    const { data } = await http.get(`/api/users/${id}`)
    return data
  },
  async update(id: string, payload: ProfileUpdate): Promise<User> {
    const { data } = await http.put(`/api/users/${id}`, payload)
    return data
  },
  async remove(id: string): Promise<void> {
    await http.delete(`/api/users/${id}`)
  },
}
