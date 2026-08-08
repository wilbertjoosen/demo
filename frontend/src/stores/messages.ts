import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import { conversationsApi } from '../api/conversations'
import type { ConversationSummary } from '../models'

/**
 * Shared inbox state, polled independently of whether MessagesView is mounted, so the nav badge and
 * new-message toasts work from anywhere in the app — mirrors useBackendHealth's module-level polling.
 */
export const useMessagesStore = defineStore('messages', {
  state: () => ({
    conversations: [] as ConversationSummary[],
    pollHandle: null as ReturnType<typeof setInterval> | null,
  }),
  getters: {
    totalUnread: (state) => state.conversations.reduce((sum, c) => sum + c.unreadCount, 0),
  },
  actions: {
    async refresh() {
      const previous = this.conversations
      const next = await conversationsApi.list()
      for (const conversation of next) {
        const before = previous.find((c) => c.id === conversation.id)
        const previousUnread = before?.unreadCount ?? 0
        if (conversation.unreadCount > previousUnread) {
          ElNotification({
            title: conversation.otherParticipantUsername,
            message: conversation.lastMessagePreview ?? '',
            type: 'info',
          })
        }
      }
      this.conversations = next
    },
    /** Optimistic local update so a conversation's badge clears the instant it's opened, without waiting for the next poll. */
    markConversationRead(conversationId: string) {
      const conversation = this.conversations.find((c) => c.id === conversationId)
      if (conversation) conversation.unreadCount = 0
    },
    startPolling(intervalMs = 8000) {
      if (this.pollHandle) return
      this.refresh()
      this.pollHandle = setInterval(() => this.refresh(), intervalMs)
    },
    stopPolling() {
      if (this.pollHandle) {
        clearInterval(this.pollHandle)
        this.pollHandle = null
      }
    },
  },
})
