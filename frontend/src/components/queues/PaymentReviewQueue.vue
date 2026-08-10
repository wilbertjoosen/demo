<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { paymentsApi } from '../../api/payments'
import { showApiError } from '../../composables/useApiError'
import { agingRowStyle } from '../../composables/useAgingRowStyle'
import type { Payment } from '../../models'

const { t } = useI18n()
const loading = ref(false)
const items = ref<Payment[]>([])
const filter = ref<'pending' | 'done' | 'all'>('pending')
const dateRange = ref<[Date, Date] | null>(null)

const PENDING_STATUSES = ['PENDING', 'AWAITING_REVIEW']

async function load() {
  loading.value = true
  try {
    items.value = await paymentsApi.list()
  } catch (error) {
    showApiError(error, t('queues.actionError'), t('common.serviceUnavailable'))
  } finally {
    loading.value = false
  }
}

onMounted(load)

const filtered = computed(() => {
  let result = items.value
  if (filter.value === 'pending') result = result.filter((p) => PENDING_STATUSES.includes(p.status))
  else if (filter.value === 'done') result = result.filter((p) => !PENDING_STATUSES.includes(p.status))
  if (dateRange.value) {
    const [from, to] = dateRange.value
    result = result.filter((p) => {
      const created = new Date(p.createdAt)
      return created >= from && created <= to
    })
  }
  return result
})

function rowStyle({ row }: { row: Payment }) {
  return agingRowStyle(row.createdAt, row.status === 'AWAITING_REVIEW')
}

async function approve(payment: Payment) {
  try {
    await ElMessageBox.confirm(t('queues.confirmApprove'), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await paymentsApi.approve(payment.id)
    ElMessage.success(t('queues.actionSuccess'))
    await load()
  } catch (error) {
    showApiError(error, t('queues.actionError'), t('common.serviceUnavailable'))
  }
}

async function reject(payment: Payment) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(t('queues.reasonPromptPlaceholder'), t('queues.reasonPromptTitle'), {
      confirmButtonText: t('queues.reject'),
      cancelButtonText: t('common.cancel'),
      inputPlaceholder: t('queues.reasonPromptPlaceholder'),
    })
    reason = result.value
  } catch {
    return
  }
  try {
    await paymentsApi.reject(payment.id, reason)
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
      <el-table-column :label="t('queues.method')" width="140">
        <template #default="{ row }">{{ t('payment.methods.' + row.method) }}</template>
      </el-table-column>
      <el-table-column prop="email" :label="t('queues.email')" />
      <el-table-column prop="quantity" :label="t('queues.quantity')" width="80" />
      <el-table-column :label="t('queues.status')" width="140">
        <template #default="{ row }">{{ t('payment.status.' + row.status) }}</template>
      </el-table-column>
      <el-table-column :label="t('queues.created')" width="180">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column width="110">
        <template #default="{ row }">
          <template v-if="row.status === 'AWAITING_REVIEW'">
            <el-tooltip :content="t('queues.approve')">
              <el-button size="small" type="success" :icon="Check" circle @click="approve(row)" />
            </el-tooltip>
            <el-tooltip :content="t('queues.reject')">
              <el-button size="small" type="danger" :icon="Close" circle @click="reject(row)" />
            </el-tooltip>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
