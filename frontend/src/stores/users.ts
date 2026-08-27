import { defineStore } from 'pinia'
import { usersApi, type CreateUserPayload, type ProfileUpdate } from '../api/users'
import type { RealmRole, User } from '../models'

export const useUsersStore = defineStore('users', {
  state: () => ({
    items: [] as User[],
    loading: false,
    me: null as User | null,
    roles: [] as RealmRole[],
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.items = await usersApi.list()
      } finally {
        this.loading = false
      }
    },
    async loadRoles() {
      if (this.roles.length === 0) this.roles = await usersApi.roles()
    },
    async loadMe() {
      this.me = await usersApi.me()
    },
    async updateMe(payload: ProfileUpdate) {
      this.me = await usersApi.updateMe(payload)
    },
    async create(payload: CreateUserPayload) {
      await usersApi.create(payload)
      await this.load()
    },
    async update(id: string, payload: ProfileUpdate) {
      await usersApi.update(id, payload)
      await this.load()
    },
    async remove(id: string) {
      await usersApi.remove(id)
      await this.load()
    },
  },
})
