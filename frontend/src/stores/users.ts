import { defineStore } from 'pinia'
import { usersApi, type ProfileUpdate } from '../api/users'
import type { User } from '../models'

export const useUsersStore = defineStore('users', {
  state: () => ({
    items: [] as User[],
    loading: false,
    me: null as User | null,
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
    async loadMe() {
      this.me = await usersApi.me()
    },
    async updateMe(payload: ProfileUpdate) {
      this.me = await usersApi.updateMe(payload)
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
