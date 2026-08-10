<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { shipmentsApi } from '../../api/shipments'
import { showApiError } from '../../composables/useApiError'
import { agingRowStyle } from '../../composables/useAgingRowStyle'
import type { Shipment } from '../../models'

const { t } = useI18n()
const loading = ref(false)
const items = ref<Shipment[]>([])
const filter = ref<'pending' | 'done' | 'all'>('pending')
const dateRange = ref<[Date, Date] | null>(null)

async function load() {
  loading.value = true
  try {
    items.value = await shipmentsApi.list()
  } catch (error) {
    showApiError(error, t('queues.actionError'), t('common.serviceUnavailable'))
  } finally {
    loading.value = false
  }
}

onMounted(load)

const filtered = computed(() => {
  let result = items.value
  if (filter.value === 'pending') result = result.filter((s) => s.status === 'PENDING_WAREHOUSE')
  else if (filter.value === 'done') result = result.filter((s) => s.status !== 'PENDING_WAREHOUSE')
  if (dateRange.value) {
    const [from, to] = dateRange.value
    result = result.filter((s) => {
      const created = new Date(s.createdAt)
      return created >= from && created <= to
    })
  }
  return result
})

function rowStyle({ row }: { row: Shipment }) {
  return agingRowStyle(row.createdAt, row.status === 'PENDING_WAREHOUSE')
}

async function confirmPicked(shipment: Shipment) {
  try {
    await ElMessageBox.confirm(t('queues.confirmConfirmPicked'), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await shipmentsApi.confirmPicked(shipment.id)
    ElMessage.success(t('queues.actionSuccess'))
    await load()
  } catch (error) {
    showApiError(error, t('queues.actionError'), t('common.serviceUnavailable'))
  }
}

async function reportIssue(shipment: Shipment) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(t('queues.reasonPromptPlaceholder'), t('queues.reasonPromptTitle'), {
      confirmButtonText: t('queues.reportIssue'),
      cancelButtonText: t('common.cancel'),
      inputPlaceholder: t('queues.reasonPromptPlaceholder'),
    })
    reason = result.value
  } catch {
    return
  }
  try {
    await shipmentsApi.reportIssue(shipment.id, reason)
    ElMessage.success(t('queues.actionSuccess'))
    await load()
  } catch (error) {
    showApiError(error, t('queues.actionError'), t('common.serviceUnavailable'))
  }
}
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <el-radio-group v-model="filter" size="small">
        <el-radio-button value="pending">{{ t('queues.filter.pending') }}</el-radio-button>
        <el-radio-button value="done">{{ t('queues.filter.done') }}</el-radio-button>
        <el-radio-button value="all">{{ t('queues.filter.all') }}</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        size="small"
        :placeholder="t('queues.dateRange')"
        :start-placeholder="t('queues.dateRange')"
        :end-placeholder="t('queues.dateRange')"
      />
    </div>
    <el-table v-loading="loading" :data="filtered" stripe :row-style="rowStyle" :empty-text="t('queues.empty')">
      <el-table-column prop="orderId" :label="t('queues.orderId')" width="90" />
      <el-table-column :label="t('orders.shippingCarrier')" width="110">
        <template #default="{ row }">{{ t('shipping.carriers.' + row.carrier) }}</template>
      </el-table-column>
      <el-table-column prop="email" :label="t('queues.email')" />
      <el-table-column prop="quantity" :label="t('queues.quantity')" width="80" />
      <el-table-column :label="t('queues.status')" width="150">
        <template #default="{ row }">{{ t('queues.shipmentStatus.' + row.status) }}</template>
      </el-table-column>
      <el-table-column :label="t('queues.created')" width="180">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column width="110">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING_WAREHOUSE'">
            <el-tooltip :content="t('queues.confirmPicked')">
              <el-button size="small" type="success" :icon="Check" circle @click="confirmPicked(row)" />
            </el-tooltip>
            <el-tooltip :content="t('queues.reportIssue')">
              <el-button size="small" type="danger" :icon="Close" circle @click="reportIssue(row)" />
            </el-tooltip>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
