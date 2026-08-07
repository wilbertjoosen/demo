<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { auditApi } from '../../api/audit'
import { showApiError } from '../../composables/useApiError'
import type { RecordHistoryAction, RecordHistoryEntry } from '../../models'

const props = defineProps<{ modelValue: boolean; recordId: string | null; title?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()

const loading = ref(false)
const entries = ref<RecordHistoryEntry[]>([])

const pageSize = 10
const currentPage = ref(1)

/** Newest first for browsing — the backend computes each entry's diff against its own predecessor
 * regardless of the order this displays them in, so reversing here doesn't affect the diffs shown. */
const orderedEntries = computed(() => [...entries.value].reverse())
const pagedEntries = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return orderedEntries.value.slice(start, start + pageSize)
})

const actionColor: Record<RecordHistoryAction, string> = {
  CREATED: '#67c23a',
  UPDATED: '#409eff',
  NO_CHANGE: '#909399',
  VIEWED: '#c0c4cc',
  FAILED: '#f56c6c',
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return t('audit.empty')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

async function load() {
  if (!props.recordId) return
  loading.value = true
  entries.value = []
  try {
    entries.value = await auditApi.recordHistory(props.recordId)
  } catch (error) {
    showApiError(error, t('audit.loadError'), t('common.serviceUnavailable'))
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.recordId],
  ([open]) => {
    if (open) {
      currentPage.value = 1
      load()
    }
  },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title ?? t('audit.historyTitle')"
    width="640"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" style="min-height: 120px">
      <el-empty v-if="!loading && entries.length === 0" :description="t('audit.noHistory')" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="(entry, index) in pagedEntries"
          :key="index"
          :timestamp="new Date(entry.timestamp).toLocaleString()"
          :color="actionColor[entry.action]"
          placement="top"
        >
          <div class="mb-1 font-medium">
            {{ t(`audit.action.${entry.action}`) }}
            <span class="text-gray-500">— {{ entry.principal }} · {{ entry.type }}</span>
          </div>
          <el-table v-if="entry.changes.length" :data="entry.changes" size="small" border class="mt-2">
            <el-table-column prop="field" :label="t('audit.field')" width="160" />
            <el-table-column :label="t('audit.oldValue')">
              <template #default="{ row }">
                <span class="text-red-500 line-through">{{ formatValue(row.oldValue) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('audit.newValue')">
              <template #default="{ row }">
                <span class="text-green-600">{{ formatValue(row.newValue) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-timeline-item>
      </el-timeline>
      <el-pagination
        v-if="entries.length > pageSize"
        class="mt-3 justify-end"
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="entries.length"
        v-model:current-page="currentPage"
      />
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>
