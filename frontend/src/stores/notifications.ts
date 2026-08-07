import { defineStore } from 'pinia'

export interface NotificationEvent {
  eventType: string
  orderId?: string
  payload?: Record<string, unknown>
  timestamp?: string
}

const MAX_HISTORY = 50

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    events: [] as NotificationEvent[],
    unread: 0,
  }),
  actions: {
    push(event: NotificationEvent) {
      this.events.unshift(event)
      if (this.events.length > MAX_HISTORY) this.events.pop()
      this.unread++
    },
    markAllRead() {
      this.unread = 0
    },
  },
})
