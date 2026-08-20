import { http } from './http'
import { unwrapCollection } from './hal'
import type {Address, DirectoryEntry, User, Country} from '../models'

export interface ProfileUpdate {
  shippingAddress?: Address
  nationalId?: string
  nationalIdCountry?: string
  phone?: string
  customAttributes?: Record<string, string>
}

export interface CreateUserPayload extends ProfileUpdate {
  username: string
  email: string
  firstName: string
  lastName: string
  password: string
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
  /** Any authenticated user — minimal, PII-free list for picking someone to message. */
  async directory(): Promise<DirectoryEntry[]> {
    const { data } = await http.get('/api/users/directory')
    return data
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
  async countries(): Promise<Country[]> {
    const { data } = await http.get(`/api/users/countries`);
    return unwrapCollection<Country>(data)
  },
}
