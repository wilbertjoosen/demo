<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { keycloak } from '../auth/keycloak'
import { useAuthStore } from '../stores/auth'
import { useMessagesStore } from '../stores/messages'
import { conversationsApi } from '../api/conversations'
import { usersApi } from '../api/users'
import { showApiError } from '../composables/useApiError'
import { resolveWsUrl } from '../lib/ws'
import type { DirectMessage, DirectoryEntry } from '../models'

const { t } = useI18n()
const auth = useAuthStore()
const inbox = useMessagesStore()

const conversationsLoading = ref(false)
const activeId = ref<string | null>(null)
const activeConversation = computed(() => inbox.conversations.find((c) => c.id === activeId.value) ?? null)

const messages = ref<DirectMessage[]>([])
const messagesLoading = ref(false)
const draft = ref('')
const sending = ref(false)
const threadEl = ref<HTMLElement | null>(null)
const otherTyping = ref(false)

let socket: WebSocket | null = null
let typingTimeout: ReturnType<typeof setTimeout> | null = null
let lastTypingSentAt = 0

/** Client-facing shape of the WS envelope every frame (chat message, receipt update, typing ping) arrives as. */
interface WsEnvelope {
  type: 'MESSAGE' | 'MESSAGE_UPDATED' | 'TYPING'
  payload: unknown
}
interface TypingPayload {
  senderId: string
  senderUsername: string
}

async function loadConversations() {
  conversationsLoading.value = true
  try {
    await inbox.refresh()
  } catch (error) {
    showApiError(error, t('messages.loadError'), t('common.serviceUnavailable'))
  } finally {
    conversationsLoading.value = false
  }
}

function clearTypingIndicator() {
  otherTyping.value = false
  if (typingTimeout) {
    clearTimeout(typingTimeout)
    typingTimeout = null
  }
}

function closeSocket() {
  if (socket) {
    socket.onclose = null
    socket.close()
    socket = null
  }
  clearTypingIndicator()
}

async function selectConversation(id: string) {
  if (activeId.value === id) return
  activeId.value = id
  closeSocket()
  sending.value = false
  messages.value = []
  messagesLoading.value = true
  try {
    messages.value = await conversationsApi.messages(id)
    await scrollToBottom()
  } catch (error) {
    showApiError(error, t('messages.loadError'), t('common.serviceUnavailable'))
  } finally {
    messagesLoading.value = false
  }
  inbox.markConversationRead(id)
  connectSocket(id)
}

function connectSocket(conversationId: string) {
  const base = resolveWsUrl(import.meta.env.VITE_CHAT_WS_BASE_URL, '')
  const url = `${base}/ws/conversations/${conversationId}?token=${encodeURIComponent(keycloak.token ?? '')}`
  const ws = new WebSocket(url)

  ws.onopen = () => sendReadReceipt(ws)

  ws.onmessage = (event) => {
    let envelope: WsEnvelope
    try {
      envelope = JSON.parse(event.data)
    } catch {
      return
    }
    if (envelope.type === 'MESSAGE') {
      const message = envelope.payload as DirectMessage
      messages.value.push(message)
      scrollToBottom()
      updateInboxPreview(message)
      if (message.senderId === auth.keycloakId) {
        sending.value = false
      } else {
        clearTypingIndicator()
        if (activeId.value === message.conversationId) {
          sendReadReceipt(ws)
          inbox.markConversationRead(message.conversationId)
        }
      }
    } else if (envelope.type === 'MESSAGE_UPDATED') {
      const updated = envelope.payload as DirectMessage
      const existing = messages.value.find((m) => m.id === updated.id)
      if (existing) {
        existing.deliveredAt = updated.deliveredAt
        existing.readAt = updated.readAt
      }
    } else if (envelope.type === 'TYPING') {
      const typing = envelope.payload as TypingPayload
      if (typing.senderId !== auth.keycloakId) {
        otherTyping.value = true
        if (typingTimeout) clearTimeout(typingTimeout)
        typingTimeout = setTimeout(() => {
          otherTyping.value = false
        }, 3000)
      }
    }
  }

  ws.onclose = () => {
    if (socket === ws) socket = null
    sending.value = false
  }
  socket = ws
}

/** Only ever called for the currently-open conversation (its WS is the only one connected here) —
 * unread counts for OTHER conversations are refreshed via the shared store's polling instead. */
function updateInboxPreview(message: DirectMessage) {
  const conversation = inbox.conversations.find((c) => c.id === message.conversationId)
  if (conversation) {
    conversation.lastMessageAt = message.createdAt
    conversation.lastMessagePreview = message.body
  }
}

function sendReadReceipt(ws: WebSocket) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'READ' }))
  }
}

async function scrollToBottom() {
  await nextTick()
  threadEl.value?.scrollTo({ top: threadEl.value.scrollHeight })
}

function send() {
  const body = draft.value.trim()
  if (!body || !socket || socket.readyState !== WebSocket.OPEN) return
  sending.value = true
  socket.send(JSON.stringify({ type: 'MESSAGE', body }))
  draft.value = ''
}

/** Pinged at most once every 2s while the user types, so every keystroke doesn't round-trip. */
function onDraftInput() {
  if (!socket || socket.readyState !== WebSocket.OPEN) return
  const now = Date.now()
  if (now - lastTypingSentAt > 2000) {
    socket.send(JSON.stringify({ type: 'TYPING' }))
    lastTypingSentAt = now
  }
}

function messageStatus(message: DirectMessage): 'sent' | 'delivered' | 'read' {
  if (message.readAt) return 'read'
  if (message.deliveredAt) return 'delivered'
  return 'sent'
}

const pickerOpen = ref(false)
const directory = ref<DirectoryEntry[]>([])
const directoryLoading = ref(false)
const directorySearch = ref('')

const filteredDirectory = computed(() => {
  const query = directorySearch.value.trim().toLowerCase()
  if (!query) return directory.value
  return directory.value.filter((entry) => entry.username.toLowerCase().includes(query))
})

async function openPicker() {
  pickerOpen.value = true
  directoryLoading.value = true
  try {
    directory.value = await usersApi.directory()
  } catch (error) {
    showApiError(error, t('messages.directoryLoadError'), t('common.serviceUnavailable'))
  } finally {
    directoryLoading.value = false
  }
}

async function startConversation(entry: DirectoryEntry) {
  try {
    const conversation = await conversationsApi.start(entry.keycloakId, entry.username)
    pickerOpen.value = false
    await inbox.refresh()
    await selectConversation(conversation.id)
  } catch (error) {
    showApiError(error, t('messages.startError'), t('common.serviceUnavailable'))
  }
}

onMounted(loadConversations)
onBeforeUnmount(closeSocket)

watch(
  () => auth.keycloakId,
  () => {
    closeSocket()
    activeId.value = null
    messages.value = []
  },
)
</script>

<template>
  <div>
    <h1 class="mb-4 text-xl font-semibold">{{ t('messages.title') }}</h1>
    <div class="flex gap-4" style="height: 65vh">
      <el-card class="flex flex-col" style="width: 280px" body-class="flex-1 overflow-y-auto p-0">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-medium">{{ t('messages.inbox') }}</span>
            <el-button size="small" type="primary" @click="openPicker">{{ t('messages.newConversation') }}</el-button>
          </div>
        </template>
        <div v-loading="conversationsLoading">
          <el-empty v-if="!conversationsLoading && inbox.conversations.length === 0" :description="t('messages.emptyInbox')" />
          <div
            v-for="conversation in inbox.conversations"
            :key="conversation.id"
            class="cursor-pointer border-b px-4 py-3 hover:bg-gray-50"
            :class="{ 'bg-blue-50': conversation.id === activeId }"
            @click="selectConversation(conversation.id)"
          >
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-2 font-medium">
                {{ conversation.otherParticipantUsername }}
                <el-badge v-if="conversation.unreadCount > 0" :value="conversation.unreadCount" />
              </span>
              <span v-if="conversation.lastMessageAt" class="text-xs text-gray-400">
                {{ new Date(conversation.lastMessageAt).toLocaleString() }}
              </span>
            </div>
            <p class="truncate text-sm text-gray-500">{{ conversation.lastMessagePreview ?? t('messages.noMessagesYet') }}</p>
          </div>
        </div>
      </el-card>

      <el-card class="flex flex-1 flex-col" body-class="flex flex-1 flex-col overflow-hidden p-0">
        <template #header>
          <div v-if="activeConversation" class="flex items-center gap-2">
            <span class="font-medium">{{ activeConversation.otherParticipantUsername }}</span>
            <span v-if="otherTyping" class="text-xs italic text-gray-400">{{ t('messages.typing') }}</span>
          </div>
        </template>
        <div v-if="!activeConversation" class="flex flex-1 items-center justify-center text-gray-400">
          {{ t('messages.selectConversation') }}
        </div>
        <template v-else>
          <div ref="threadEl" v-loading="messagesLoading" class="flex-1 space-y-2 overflow-y-auto p-4">
            <div
              v-for="message in messages"
              :key="message.id"
              class="flex flex-col"
              :class="message.senderId === auth.keycloakId ? 'items-end' : 'items-start'"
            >
              <div
                class="max-w-[70%] rounded-lg px-3 py-2 text-sm"
                :class="message.senderId === auth.keycloakId ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-800'"
              >
                {{ message.body }}
              </div>
              <span class="mt-1 flex items-center gap-1 text-xs text-gray-400">
                {{ new Date(message.createdAt).toLocaleTimeString() }}
                <template v-if="message.senderId === auth.keycloakId">
                  <span
                    v-if="messageStatus(message) === 'sent'"
                    :title="t('messages.statusSent')"
                  >&check;</span>
                  <span
                    v-else
                    :class="messageStatus(message) === 'read' ? 'text-blue-500' : 'text-gray-400'"
                    :title="messageStatus(message) === 'read' ? t('messages.statusRead') : t('messages.statusDelivered')"
                  >&check;&check;</span>
                </template>
              </span>
            </div>
          </div>
          <div class="flex gap-2 border-t p-3">
            <el-input
              v-model="draft"
              :placeholder="t('messages.placeholder')"
              :disabled="sending"
              @input="onDraftInput"
              @keyup.enter="send"
            />
            <el-button type="primary" :loading="sending" @click="send">{{ t('messages.send') }}</el-button>
          </div>
        </template>
      </el-card>
    </div>

    <el-dialog v-model="pickerOpen" :title="t('messages.newConversation')" width="420">
      <el-input v-model="directorySearch" :placeholder="t('messages.searchUsers')" class="mb-3" clearable />
      <div v-loading="directoryLoading" style="max-height: 320px; overflow-y: auto">
        <el-empty v-if="!directoryLoading && filteredDirectory.length === 0" :description="t('messages.noUsersFound')" />
        <div
          v-for="entry in filteredDirectory"
          :key="entry.keycloakId"
          class="flex cursor-pointer items-center justify-between border-b px-2 py-2 hover:bg-gray-50"
          @click="startConversation(entry)"
        >
          <span>{{ entry.username }}</span>
          <el-button size="small">{{ t('messages.message') }}</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="pickerOpen = false">{{ t('common.cancel') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
