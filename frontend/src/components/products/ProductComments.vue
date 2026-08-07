<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete } from '@element-plus/icons-vue'
import { commentsApi } from '../../api/comments'
import { showApiError } from '../../composables/useApiError'
import { useAuthStore } from '../../stores/auth'
import type { Comment } from '../../models'

const props = defineProps<{ productId: string | null }>()
const { t } = useI18n()
const auth = useAuthStore()

const comments = ref<Comment[]>([])
const loading = ref(false)
const newBody = ref('')
const posting = ref(false)

async function load() {
  if (!props.productId) return
  loading.value = true
  try {
    comments.value = await commentsApi.listByProduct(props.productId)
  } catch {
    comments.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.productId, load, { immediate: true })

async function post() {
  if (!props.productId || !newBody.value.trim()) return
  posting.value = true
  try {
    await commentsApi.create({ productId: props.productId, body: newBody.value.trim() })
    newBody.value = ''
    await load()
  } catch (error) {
    showApiError(error, t('comments.postError'), t('common.serviceUnavailable'))
  } finally {
    posting.value = false
  }
}

async function remove(comment: Comment) {
  try {
    await commentsApi.remove(comment.id)
    await load()
  } catch (error) {
    showApiError(error, t('comments.deleteError'), t('common.serviceUnavailable'))
  }
}
</script>

<template>
  <div v-loading="loading">
    <p v-if="!loading && comments.length === 0" class="text-gray-400">{{ t('comments.empty') }}</p>
    <div v-for="comment in comments" :key="comment.id" class="mb-3 border-b pb-2 last:border-0">
      <div class="flex items-center justify-between">
        <span class="font-medium">{{ comment.authorName }}</span>
        <div class="flex items-center gap-2">
          <span class="text-xs text-gray-400">{{ new Date(comment.createdAt).toLocaleString() }}</span>
          <el-button
            v-if="auth.isAdmin || comment.keycloakUserId === auth.keycloakId"
            size="small"
            text
            type="danger"
            :icon="Delete"
            @click="remove(comment)"
          />
        </div>
      </div>
      <p class="text-sm text-gray-700">{{ comment.body }}</p>
    </div>
    <el-form class="mt-4" @submit.prevent="post">
      <el-form-item>
        <el-input v-model="newBody" type="textarea" :rows="2" :placeholder="t('comments.placeholder')" />
      </el-form-item>
    </el-form>
    <el-button type="primary" :loading="posting" :disabled="!newBody.trim()" @click="post">{{ t('comments.post') }}</el-button>
  </div>
</template>
