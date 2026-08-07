import { defineStore } from 'pinia'
import { productsApi } from '../api/products'
import type { Product } from '../models'

export const useProductsStore = defineStore('products', {
  state: () => ({
    items: [] as Product[],
    loading: false,
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.items = await productsApi.list()
      } finally {
        this.loading = false
      }
    },
    async create(payload: { sku: string; name: string; price: number }) {
      await productsApi.create(payload)
      await this.load()
    },
    async remove(id: string) {
      await productsApi.remove(id)
      await this.load()
    },
  },
})
