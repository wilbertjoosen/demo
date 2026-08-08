export interface ConversationSummary {
  id: string
  otherParticipantId: string
  otherParticipantUsername: string
  lastMessageAt: string | null
  lastMessagePreview: string | null
  unreadCount: number
}

export interface DirectMessage {
  id: string
  conversationId: string
  senderId: string
  senderUsername: string
  body: string
  createdAt: string
  deliveredAt: string | null
  readAt: string | null
}

export interface DirectoryEntry {
  keycloakId: string
  username: string
}
