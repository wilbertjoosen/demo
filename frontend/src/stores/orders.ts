import { defineStore } from 'pinia'
import { ordersApi } from '../api/orders'
import type { Address, OrderView, PaymentMethod, ShippingCarrier } from '../models'

export const useOrdersStore = defineStore('orders', {
  state: () => ({
    items: [] as OrderView[],
    loading: false,
  }),
  actions: {
    async load() {
      this.loading = true
      try {
        this.items = await ordersApi.list()
      } finally {
        this.loading = false
      }
    },
    async place(payload: {
      productId: string
      quantity: number
      shippingAddress: Address
      paymentMethod: PaymentMethod
      shippingCarrier: ShippingCarrier
    }) {
      await ordersApi.place(payload)
      await this.load()
    },
    async cancel(id: string) {
      await ordersApi.cancel(id)
      await this.load()
    },
    async updateAddress(id: string, address: Address) {
      await ordersApi.updateAddress(id, address)
      await this.load()
    },
  },
})
