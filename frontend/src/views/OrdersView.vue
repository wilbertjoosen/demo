<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useOrdersStore } from '../stores/orders'
import { extractErrorMessage } from '../composables/useApiError'
import OrderTable from '../components/orders/OrderTable.vue'
import OrderAddressDialog from '../components/orders/OrderAddressDialog.vue'
import ShipmentTrackingDialog from '../components/orders/ShipmentTrackingDialog.vue'
import type { Address, OrderView } from '../models'

const { t } = useI18n()
const orders = useOrdersStore()

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<OrderView | null>(null)
const trackingVisible = ref(false)
const tracking = ref<OrderView | null>(null)

function openTracking(order: OrderView) {
  tracking.value = order
  trackingVisible.value = true
}

async function cancelOrder(order: OrderView) {
  try {
    await ElMessageBox.confirm(t('orders.confirmCancel', { id: order.id }), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await orders.cancel(order.id)
    ElMessage.success(t('orders.cancelledSuccess'))
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, t('orders.cancelError'), t('common.serviceUnavailable')))
  }
}

function openEdit(order: OrderView) {
  editing.value = order
  dialogVisible.value = true
}

async function saveAddress(address: Address) {
  if (!editing.value) return
  saving.value = true
  try {
    await orders.updateAddress(editing.value.id, address)
    ElMessage.success(t('orders.addressUpdated'))
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, t('orders.addressUpdateError'), t('common.serviceUnavailable')))
  } finally {
    saving.value = false
  }
}

onMounted(() => orders.load())
</script>

<template>
  <div>
    <h1 class="mb-4 text-xl font-semibold">{{ t('orders.title') }}</h1>
    <OrderTable :orders="orders.items" :loading="orders.loading" @edit-address="openEdit" @cancel="cancelOrder" @track="openTracking" />
    <OrderAddressDialog
      v-model="dialogVisible"
      :initial-address="editing?.shippingAddress ?? null"
      :saving="saving"
      @submit="saveAddress"
    />
    <ShipmentTrackingDialog v-model="trackingVisible" :order-id="tracking?.id ?? null" />
  </div>
</template>
