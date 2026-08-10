<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from './stores/auth'
import { useMessagesStore } from './stores/messages'
import { useNotifications } from './composables/useNotifications'
import { useBackendHealth } from './composables/useBackendHealth'
import NotificationBell from './components/NotificationBell.vue'
import { SUPPORTED_LOCALES, setLocale } from './i18n'

const auth = useAuthStore()
const messages = useMessagesStore()
const { locale, t } = useI18n()
const { connect } = useNotifications()
const { available: backendAvailable, startPolling, stopPolling } = useBackendHealth()

onMounted(() => {
  startPolling()
  if (auth.isAuthenticated) {
    connect()
    messages.startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
  messages.stopPolling()
})
</script>

<template>
  <div v-if="!auth.isAuthenticated" class="flex min-h-screen items-center justify-center bg-gray-50">
    <el-card class="w-96 text-center">
      <h1 class="mb-2 text-2xl font-semibold">{{ t('nav.home') }}</h1>
      <p class="mb-6 text-gray-600">{{ t('home.description') }}</p>
      <el-button type="primary" size="large" class="w-full" @click="auth.login">{{ t('nav.login') }}</el-button>
    </el-card>
  </div>

  <div v-else class="min-h-screen bg-gray-50">
    <div class="flex items-center border-b border-gray-200 bg-white pr-6">
      <el-menu mode="horizontal" router :ellipsis="false" class="!border-b-0 flex-1">
        <el-menu-item index="/">{{ t('nav.home') }}</el-menu-item>
        <el-menu-item index="/products">{{ t('nav.products') }}</el-menu-item>
        <el-menu-item index="/orders">{{ t('nav.orders') }}</el-menu-item>
        <el-menu-item index="/profile">{{ t('nav.profile') }}</el-menu-item>
        <el-menu-item index="/messages">
          {{ t('nav.messages') }}
          <el-badge v-if="messages.totalUnread > 0" :value="messages.totalUnread" class="ml-1" />
        </el-menu-item>
        <el-menu-item
          v-if="auth.isAdmin || auth.isFinance || auth.isWarehouse || auth.isDeliveryAgent"
          index="/queues"
        >
          {{ t('nav.queues') }}
        </el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin">{{ t('nav.admin') }}</el-menu-item>
      </el-menu>
      <div class="flex items-center gap-4">
        <el-select :model-value="locale" size="small" class="!w-28" @update:model-value="setLocale">
          <el-option v-for="l in SUPPORTED_LOCALES" :key="l.code" :value="l.code" :label="l.label" />
        </el-select>
        <NotificationBell />
        <span class="text-sm text-gray-500">{{ auth.username }}</span>
        <el-button text @click="auth.logout">{{ t('nav.logout') }}</el-button>
      </div>
    </div>

    <el-alert v-if="!backendAvailable" type="warning" :closable="false" show-icon class="!rounded-none">
      <template #title>{{ t('common.backendUnavailable') }}</template>
    </el-alert>
    <el-alert v-if="auth.impersonatorUsername" type="info" :closable="false" show-icon class="!rounded-none">
      <template #title>{{ t('admin.impersonating', { username: auth.username, admin: auth.impersonatorUsername }) }}</template>
    </el-alert>

    <main class="mx-auto max-w-5xl px-4 py-6">
      <router-view />
    </main>
  </div>
</template>
