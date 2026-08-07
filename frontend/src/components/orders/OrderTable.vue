<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { OrderStatus, OrderView } from '../../models'

const props = defineProps<{ orders: OrderView[]; loading: boolean }>()
const emit = defineEmits<{ 'edit-address': [order: OrderView]; cancel: [order: OrderView]; track: [order: OrderView] }>()
const { t } = useI18n()

const statusType: Record<OrderStatus, 'info' | 'success' | 'warning' | 'danger'> = {
  PENDING_PAYMENT: 'info',
  PAID: 'info',
  SHIPPED: 'warning',
  CONFIRMED: 'success',
  CANCELLED: 'danger',
  PAYMENT_FAILED: 'danger',
  SHIPPING_FAILED: 'danger',
  DELIVERY_FAILED: 'danger',
}

const pageSize = 10
const currentPage = ref(1)

const pagedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return props.orders.slice(start, start + pageSize)
})

watch(
  () => props.orders.length,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.orders.length / pageSize))
    if (currentPage.value > maxPage) currentPage.value = maxPage
  },
)
</script>

<template>
  <el-table v-loading="loading" :data="pagedOrders" stripe>
    <el-table-column prop="id" :label="t('orders.id')" width="80" />
    <el-table-column :label="t('orders.status')" width="160">
      <template #default="{ row }">
        <el-tag :type="statusType[row.status as OrderStatus]">{{ row.status }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="quantity" :label="t('orders.quantity')" width="80" />
    <el-table-column :label="t('orders.shippingAddress')">
      <template #default="{ row }">
        <span v-if="row.shippingAddress">{{ row.shippingAddress.street }}, {{ row.shippingAddress.city }}</span>
      </template>
    </el-table-column>
    <el-table-column :label="t('orders.paymentMethod')" width="130">
      <template #default="{ row }">
        <span v-if="row.paymentMethod">{{ t('payment.methods.' + row.paymentMethod) }}</span>
      </template>
    </el-table-column>
    <el-table-column :label="t('orders.shippingCarrier')" width="110">
      <template #default="{ row }">
        <span v-if="row.shippingCarrier">{{ t('shipping.carriers.' + row.shippingCarrier) }}</span>
      </template>
    </el-table-column>
    <el-table-column :label="t('orders.updated')" width="200">
      <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString() }}</template>
    </el-table-column>
    <el-table-column width="260">
      <template #default="{ row }">
        <el-button v-if="row.status !== 'PENDING_PAYMENT' && row.status !== 'CANCELLED'" size="small" @click="emit('track', row)">
          {{ t('orders.track') }}
        </el-button>
        <template v-if="row.status === 'PENDING_PAYMENT'">
          <el-button size="small" @click="emit('edit-address', row)">{{ t('orders.editAddress') }}</el-button>
          <el-button size="small" type="danger" @click="emit('cancel', row)">{{ t('orders.cancelOrder') }}</el-button>
        </template>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    v-if="orders.length > pageSize"
    class="mt-3 justify-end"
    layout="prev, pager, next"
    :page-size="pageSize"
    :total="orders.length"
    v-model:current-page="currentPage"
  />
</template>
