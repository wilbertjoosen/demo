<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Delete, Clock } from '@element-plus/icons-vue'
import { mediaApi } from '../../api/media'
import { showApiError } from '../../composables/useApiError'
import { uploadMediaDirectToS3 } from '../../composables/usePresignedMediaUpload'
import RecordHistoryDialog from './RecordHistoryDialog.vue'
import { detectMediaType } from '../../models'
import type { MediaAsset, MediaType, Product } from '../../models'

const props = defineProps<{ modelValue: boolean; product: Product | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()

const loading = ref(false)
const uploading = ref(false)
const items = ref<MediaAsset[]>([])

const form = reactive<{ type: MediaType; caption: string }>({ type: 'PHOTO', caption: '' })

async function load() {
  if (!props.product) return
  loading.value = true
  try {
    items.value = await mediaApi.listByProduct(props.product.id)
  } catch (error) {
    showApiError(error, t('media.loadError'), t('common.serviceUnavailable'))
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.product],
  ([open]) => {
    if (open) {
      form.type = 'PHOTO'
      form.caption = ''
      load()
    }
  },
)

/**
 * Upload IS the add action — one file picked attaches directly to the product, no separate URL
 * step. Type is auto-detected from the file's extension, not user-selected.
 *
 * Goes straight to S3 (usePresignedMediaUpload) rather than through this service's own /upload
 * endpoint — the resulting asset is PENDING_VALIDATION, not immediately visible in `load()`'s
 * listing, until product-media-service's MediaValidationListener inspects the actual bytes and
 * promotes it to ACTIVE (usually within a few seconds).
 */
async function uploadFile(rawFile: File) {
  if (!props.product) return false
  const type = detectMediaType(rawFile.name)
  form.type = type
  uploading.value = true
  try {
    await uploadMediaDirectToS3(rawFile, { productId: props.product.id, type, caption: form.caption || undefined })
    ElMessage.success(t('media.uploadQueued'))
    form.caption = ''
    await load()
  } catch (error) {
    showApiError(error, t('media.uploadError'), t('common.serviceUnavailable'))
  } finally {
    uploading.value = false
  }
  return false
}

const itemHistoryDialog = ref(false)
const itemHistoryId = ref<string | null>(null)

function openItemHistory(item: MediaAsset) {
  itemHistoryId.value = item.id
  itemHistoryDialog.value = true
}

async function removeMedia(item: MediaAsset) {
  try {
    await ElMessageBox.confirm(t('media.confirmDelete'), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await mediaApi.remove(item.id)
    ElMessage.success(t('media.deleted'))
    await load()
  } catch (error) {
    showApiError(error, t('media.deleteError'), t('common.serviceUnavailable'))
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('media.manageTitle', { name: product?.name ?? '' })"
    width="600"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-position="top" class="mb-4">
      <div class="flex gap-3">
        <el-form-item :label="t('media.type')" class="w-32">
          <el-select v-model="form.type" disabled>
            <el-option label="PHOTO" value="PHOTO" />
            <el-option label="VIDEO" value="VIDEO" />
            <el-option label="DOCUMENT" value="DOCUMENT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('media.caption')" class="flex-1">
          <el-input v-model="form.caption" />
        </el-form-item>
      </div>
      <p class="mb-2 text-sm text-gray-500">{{ t('media.typeAutoDetected') }}</p>
      <el-upload
        :show-file-list="false"
        :before-upload="uploadFile"
        accept="image/*,video/*,.pdf,.doc,.docx,.xls,.xlsx"
      >
        <el-button type="primary" :loading="uploading">{{ t('media.uploadFile') }}</el-button>
      </el-upload>
    </el-form>

    <el-divider />

    <el-table v-loading="loading" :data="items" size="small">
      <el-table-column :label="t('media.preview')" width="90">
        <template #default="{ row }">
          <el-image v-if="row.type === 'PHOTO'" :src="row.url" fit="cover" class="h-12 w-12 rounded" />
          <span v-else-if="row.type === 'VIDEO'" class="text-2xl">🎬</span>
          <span v-else class="text-2xl">📄</span>
        </template>
      </el-table-column>
      <el-table-column prop="type" :label="t('media.type')" width="80" />
      <el-table-column prop="caption" :label="t('media.caption')" />
      <el-table-column width="100">
        <template #default="{ row }">
          <el-tooltip :content="t('admin.history')">
            <el-button size="small" :icon="Clock" circle @click="openItemHistory(row)" />
          </el-tooltip>
          <el-tooltip :content="t('common.delete')">
            <el-button size="small" type="danger" :icon="Delete" circle @click="removeMedia(row)" />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
  <RecordHistoryDialog v-model="itemHistoryDialog" :record-id="itemHistoryId" />
</template>
