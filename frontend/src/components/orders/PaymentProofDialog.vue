<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { paymentsApi } from '../../api/payments'
import { showApiError } from '../../composables/useApiError'
import type { Payment } from '../../models'

const props = defineProps<{ modelValue: boolean; orderId: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()

const payment = ref<Payment | null>(null)
const loading = ref(false)
const uploading = ref(false)
const notFound = ref(false)
const paymentProof = ref<string | null>(null)
const isProofImage = ref(false)

async function loadProof(proofUrl: string) {
  try {
    const blob = await paymentsApi.getProof(proofUrl)
    paymentProof.value = URL.createObjectURL(blob)
    // Check if the blob type is an image (e.g. image/jpeg, image/png, etc.)
    isProofImage.value = blob.type.startsWith('image/')
  } catch {
    paymentProof.value = null
    isProofImage.value = false
  }
}

async function load() {
  if (!props.orderId) return
  payment.value = null
  notFound.value = false
  loading.value = true
  paymentProof.value = null
  isProofImage.value = false
  try {
    payment.value = await paymentsApi.getByOrderId(props.orderId)
    if (payment.value.proofOfPaymentUrl) {
      await loadProof(payment.value.proofOfPaymentUrl)
    }
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

watch(
    () => [props.modelValue, props.orderId],
    ([open]) => {
      if (open) load()
    },
)

async function uploadFile(rawFile: File) {
  if (!payment.value) return false
  uploading.value = true
  try {
    payment.value = await paymentsApi.uploadProof(payment.value.id, rawFile)
    if (payment.value.proofOfPaymentUrl) {
      await loadProof(payment.value.proofOfPaymentUrl)
    }
    ElMessage.success(t('payment.proofUploaded'))
  } catch (error) {
    showApiError(error, t('payment.proofUploadError'), t('common.serviceUnavailable'))
  } finally {
    uploading.value = false
  }
  return false
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('payment.proofDialogTitle', { id: orderId })" width="440"
             @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading">
      <template v-if="payment">
        <p class="mb-3 text-sm text-gray-500">
          {{ t('payment.methods.' + payment.method) }} — {{ t('payment.status.' + payment.status) }}
        </p>
        <div v-if="paymentProof" class="mb-4">
          <el-image
              v-if="isProofImage"
              :src="paymentProof"
              fit="contain"
              class="max-h-60 w-full rounded border"
          />
          <a v-else :href="paymentProof" target="_blank" rel="noopener" class="text-blue-600 underline">
            {{ t('payment.viewProofFile') }}
          </a>
        </div>
        <p v-else class="mb-4 text-gray-400">{{ t('payment.noProofYet') }}</p>
        <el-upload :show-file-list="false" :before-upload="uploadFile" accept="image/*,.pdf">
          <el-button type="primary" :loading="uploading">
            {{ payment.proofOfPaymentUrl ? t('payment.replaceProof') : t('payment.uploadProof') }}
          </el-button>
        </el-upload>
      </template>
      <p v-else-if="notFound && !loading" class="text-gray-400">{{ t('payment.noPaymentYet') }}</p>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>