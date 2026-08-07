<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { shipmentsApi } from '../../api/shipments'
import type { Shipment } from '../../models'

const props = defineProps<{ modelValue: boolean; orderId: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()

const shipment = ref<Shipment | null>(null)
const loading = ref(false)
const notFound = ref(false)

watch(
  () => [props.modelValue, props.orderId],
  async ([open, orderId]) => {
    if (!open || !orderId) return
    shipment.value = null
    notFound.value = false
    loading.value = true
    try {
      shipment.value = await shipmentsApi.tracking(orderId as string)
    } catch {
      notFound.value = true
    } finally {
      loading.value = false
    }
  },
)
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('orders.trackingTitle', { id: orderId })" width="440"
    @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading">
      <template v-if="shipment">
        <p class="mb-3 text-sm text-gray-500">
          {{ t('shipping.carriers.' + shipment.carrier) }} — ${{ shipment.cost.toFixed(2) }}
        </p>
        <el-steps direction="vertical" :active="shipment.trackingHistory.length" finish-status="success">
          <el-step
            v-for="event in shipment.trackingHistory"
            :key="event.status"
            :title="t('orders.trackingStatus.' + event.status)"
            :description="new Date(event.timestamp).toLocaleString()"
          />
        </el-steps>
      </template>
      <p v-else-if="notFound && !loading" class="text-gray-400">{{ t('orders.noTrackingYet') }}</p>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
    </template>
  </el-dialog>
</template>
