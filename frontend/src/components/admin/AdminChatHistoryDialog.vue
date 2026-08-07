<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { chatApi } from '../../api/chat'
import { showApiError } from '../../composables/useApiError'
import type { ChatMessage, Product } from '../../models'

const props = defineProps<{ modelValue: boolean; product: Product | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()

const loading = ref(false)
const messages = ref<ChatMessage[]>([])

const pageSize = 10
const currentPage = ref(1)

/** Newest first for browsing. */
const orderedMessages = computed(() => [...messages.value].reverse())
const pagedMessages = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return orderedMessages.value.slice(start, start + pageSize)
})

async function load() {
  if (!props.product) return
  loading.value = true
  try {
    messages.value = await chatApi.history(props.product.id)
  } catch (error) {
    showApiError(error, t('chat.loadError'), t('common.serviceUnavailable'))
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.product],
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
    :title="t('chat.historyTitle', { name: product?.name ?? '' })"
    width="560"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" style="min-height: 120px">
      <el-empty v-if="!loading && messages.length === 0" :description="t('chat.empty')" />
      <div v-else class="flex flex-col gap-3">
        <div v-for="message in pagedMessages" :key="message.id" class="border-b pb-2 last:border-0">
          <div class="flex items-center justify-between">
            <span class="font-medium">{{ message.username }}</span>
            <span class="text-xs text-gray-400">{{ new Date(message.createdAt).toLocaleString() }}</span>
          </div>
          <p class="text-sm text-gray-700">{{ message.body }}</p>
        </div>
      </div>
      <el-pagination
        v-if="messages.length > pageSize"
        class="mt-3 justify-end"
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="messages.length"
        v-model:current-page="currentPage"
      />
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>
