<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete, Edit } from '@element-plus/icons-vue'
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

const editingId = ref<string | null>(null)
const editBody = ref('')
const savingEdit = ref(false)

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

function startEdit(comment: Comment) {
  editingId.value = comment.id
  editBody.value = comment.body
}

function cancelEdit() {
  editingId.value = null
  editBody.value = ''
}

async function saveEdit(comment: Comment) {
  if (!editBody.value.trim()) return
  savingEdit.value = true
  try {
    await commentsApi.update(comment.id, editBody.value.trim())
    editingId.value = null
    await load()
  } catch (error) {
    showApiError(error, t('comments.editError'), t('common.serviceUnavailable'))
  } finally {
    savingEdit.value = false
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
          <span class="text-xs text-gray-400">
            {{ new Date(comment.createdAt).toLocaleString() }}
            <template v-if="comment.updatedAt !== comment.createdAt">· {{ t('comments.edited') }}</template>
          </span>
          <template v-if="auth.isAdmin || comment.keycloakUserId === auth.keycloakId">
            <el-button size="small" text :icon="Edit" @click="startEdit(comment)" />
            <el-button size="small" text type="danger" :icon="Delete" @click="remove(comment)" />
          </template>
        </div>
      </div>
      <div v-if="editingId === comment.id" class="mt-1">
        <el-input v-model="editBody" type="textarea" :rows="2" />
        <div class="mt-1 flex justify-end gap-2">
          <el-button size="small" @click="cancelEdit">{{ t('common.cancel') }}</el-button>
          <el-button size="small" type="primary" :loading="savingEdit" @click="saveEdit(comment)">{{ t('common.save') }}</el-button>
        </div>
      </div>
      <p v-else class="text-sm text-gray-700">{{ comment.body }}</p>
    </div>
    <el-form class="mt-4" @submit.prevent="post">
      <el-form-item>
        <el-input v-model="newBody" type="textarea" :rows="2" :placeholder="t('comments.placeholder')" />
      </el-form-item>
    </el-form>
    <el-button type="primary" :loading="posting" :disabled="!newBody.trim()" @click="post">{{ t('comments.post') }}</el-button>
  </div>
</template>
