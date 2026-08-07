<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { User } from '../../models'

defineProps<{ modelValue: boolean; user: User | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function formatAddress(user: User): string {
  const a = user.shippingAddress
  return a ? `${a.street}, ${a.city}, ${a.postalCode}, ${a.country}` : '—'
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('admin.userDetailTitle', { username: user?.username ?? '' })" width="440"
    @update:model-value="emit('update:modelValue', $event)">
    <el-descriptions v-if="user" :column="1" border>
      <el-descriptions-item :label="t('admin.username')">{{ user.username }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.email')">{{ user.email }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.displayName')">{{ user.displayName }}</el-descriptions-item>
      <el-descriptions-item :label="t('profile.nationalId')">{{ user.nationalId ?? '—' }}</el-descriptions-item>
      <el-descriptions-item :label="t('profile.phone')">{{ user.phone ?? '—' }}</el-descriptions-item>
      <el-descriptions-item :label="t('profile.shippingAddress')">{{ formatAddress(user) }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.createdAt')">{{ formatDate(user.createdAt) }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.updatedAt')">{{ formatDate(user.updatedAt) }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>
