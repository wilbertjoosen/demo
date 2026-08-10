<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { OrderDrillDownItem, UserDrillDownItem } from '../../../models'

defineProps<{
  modelValue: boolean
  loading: boolean
  title: string
  kind: 'orders' | 'users'
  orderRows: OrderDrillDownItem[]
  userRows: UserDrillDownItem[]
}>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
const { t } = useI18n()
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="700" @update:model-value="emit('update:modelValue', $event)">
    <el-table
      v-if="kind === 'orders'"
      v-loading="loading"
      :data="orderRows"
      stripe
      max-height="400"
      :empty-text="t('reports.drillDown.empty')"
    >
      <el-table-column prop="orderId" :label="t('reports.drillDown.orderId')" width="100" />
      <el-table-column prop="email" :label="t('queues.email')" />
      <el-table-column :label="t('reports.drillDown.quantity')" width="70" align="right">
        <template #default="{ row }">{{ row.quantity }}</template>
      </el-table-column>
      <el-table-column :label="t('orders.status')" width="150">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
      <el-table-column :label="t('orders.paymentMethod')" width="130">
        <template #default="{ row }">{{ row.paymentMethod ? t('payment.methods.' + row.paymentMethod) : '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('reports.drillDown.created')" width="170">
        <template #default="{ row }">{{ new Date(row.orderCreatedAt).toLocaleString() }}</template>
      </el-table-column>
    </el-table>
    <el-table v-else v-loading="loading" :data="userRows" stripe max-height="400" :empty-text="t('reports.drillDown.empty')">
      <el-table-column prop="username" :label="t('reports.drillDown.user')" />
      <el-table-column prop="email" :label="t('queues.email')" />
      <el-table-column :label="t('reports.drillDown.created')" width="170">
        <template #default="{ row }">{{ new Date(row.registeredAt).toLocaleString() }}</template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>
