<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { User } from '../../models'

defineProps<{ users: User[]; loading: boolean }>()
const emit = defineEmits<{ delete: [user: User] }>()
const { t } = useI18n()
</script>

<template>
  <el-table v-loading="loading" :data="users" stripe>
    <el-table-column prop="username" :label="t('admin.username')" />
    <el-table-column prop="email" :label="t('admin.email')" />
    <el-table-column prop="displayName" :label="t('admin.displayName')" />
    <el-table-column width="100">
      <template #default="{ row }">
        <el-button size="small" type="danger" @click="emit('delete', row)">{{ t('common.delete') }}</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>
