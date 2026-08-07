<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { User } from '../../models'

const props = defineProps<{ users: User[]; loading: boolean; currentUserKeycloakId: string }>()
const emit = defineEmits<{ edit: [user: User]; detail: [user: User]; delete: [user: User]; impersonate: [user: User] }>()
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
    <el-table-column prop="displayName" :label="t('admin.displayName')" />
    <el-table-column width="320">
      <template #default="{ row }">
        <el-button size="small" @click="emit('detail', row)">{{ t('common.detail') }}</el-button>
        <el-button size="small" @click="emit('edit', row)">{{ t('common.edit') }}</el-button>
        <el-button
          v-if="row.keycloakId !== currentUserKeycloakId"
          size="small"
          @click="emit('impersonate', row)"
        >
          {{ t('admin.impersonate') }}
        </el-button>
        <el-button
          size="small"
          type="danger"
          :disabled="row.keycloakId === currentUserKeycloakId"
          :title="row.keycloakId === currentUserKeycloakId ? t('admin.cannotDeleteSelf') : ''"
          @click="emit('delete', row)"
        >
          {{ t('common.delete') }}
        </el-button>
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
