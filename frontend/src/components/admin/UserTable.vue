<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { View, Edit, Switch, Delete, Clock } from '@element-plus/icons-vue'
import type { User } from '../../models'

const props = defineProps<{ users: User[]; loading: boolean; currentUserKeycloakId: string }>()
const emit = defineEmits<{
  edit: [user: User]
  detail: [user: User]
  delete: [user: User]
  impersonate: [user: User]
  history: [user: User]
}>()
const { t } = useI18n()

const pageSize = 10
const currentPage = ref(1)

const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return props.users.slice(start, start + pageSize)
})

watch(
  () => props.users.length,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.users.length / pageSize))
    if (currentPage.value > maxPage) currentPage.value = maxPage
  },
)
</script>

<template>
  <el-table v-loading="loading" :data="pagedUsers" stripe>
    <el-table-column prop="username" :label="t('admin.username')" />
    <el-table-column prop="email" :label="t('admin.email')" />
    <el-table-column :label="t('admin.name')">
      <template #default="{ row }">{{ [row.firstName, row.lastName].filter(Boolean).join(' ') }}</template>
    </el-table-column>
    <el-table-column width="210">
      <template #default="{ row }">
        <el-tooltip :content="t('common.detail')">
          <el-button size="small" :icon="View" circle @click="emit('detail', row)" />
        </el-tooltip>
        <el-tooltip :content="t('common.edit')">
          <el-button size="small" :icon="Edit" circle @click="emit('edit', row)" />
        </el-tooltip>
        <el-tooltip :content="t('admin.history')">
          <el-button size="small" :icon="Clock" circle @click="emit('history', row)" />
        </el-tooltip>
        <el-tooltip v-if="row.keycloakId !== currentUserKeycloakId" :content="t('admin.impersonate')">
          <el-button size="small" :icon="Switch" circle @click="emit('impersonate', row)" />
        </el-tooltip>
        <el-tooltip :content="row.keycloakId === currentUserKeycloakId ? t('admin.cannotDeleteSelf') : t('common.delete')">
          <el-button
            size="small"
            type="danger"
            :icon="Delete"
            circle
            :disabled="row.keycloakId === currentUserKeycloakId"
            @click="emit('delete', row)"
          />
        </el-tooltip>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    v-if="users.length > pageSize"
    class="mt-3 justify-end"
    layout="prev, pager, next"
    :page-size="pageSize"
    :total="users.length"
    v-model:current-page="currentPage"
  />
</template>
