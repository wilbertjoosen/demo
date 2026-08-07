<script setup lang="ts">
import { Bell } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useNotificationsStore } from '../stores/notifications'

const store = useNotificationsStore()
const { t } = useI18n()
</script>

<template>
  <el-popover trigger="click" width="320" @show="store.markAllRead">
    <template #reference>
      <el-badge :value="store.unread" :hidden="store.unread === 0" class="mr-2">
        <el-button :icon="Bell" circle />
      </el-badge>
    </template>
    <div class="max-h-80 overflow-y-auto">
      <p v-if="store.events.length === 0" class="text-sm text-gray-400">{{ t('notifications.empty') }}</p>
      <div v-for="(event, i) in store.events" :key="i" class="border-b py-2 text-sm last:border-b-0">
        <div class="font-medium">{{ event.eventType.replaceAll('_', ' ') }}</div>
        <div v-if="event.orderId" class="text-gray-500">Order {{ event.orderId }}</div>
      </div>
    </div>
  </el-popover>
</template>
