<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { reportsApi } from '../../../api/reports'
import { showApiError } from '../../../composables/useApiError'
import type { TopProductsReport } from '../../../models'

const { t } = useI18n()
const loading = ref(true)
const report = ref<TopProductsReport | null>(null)

onMounted(async () => {
  try {
    report.value = await reportsApi.topProducts()
  } catch (error) {
    showApiError(error, t('reports.loadError'))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <h2 class="mb-3 text-lg font-semibold">{{ t('reports.topProducts.title') }}</h2>
    <el-table v-if="report" :data="report.products" stripe :empty-text="t('reports.topProducts.empty')">
      <el-table-column :label="t('reports.topProducts.product')">
        <template #default="{ row }">
          {{ row.name }}
          <el-tag v-if="!row.active" size="small" type="info" class="ml-1">{{ t('reports.topProducts.inactive') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sku" :label="t('reports.topProducts.sku')" width="140" />
      <el-table-column :label="t('reports.topProducts.quantityOrdered')" width="130" align="right">
        <template #default="{ row }">{{ row.totalQuantityOrdered }}</template>
      </el-table-column>
      <el-table-column prop="orderCount" :label="t('reports.topProducts.orderCount')" width="100" align="right" />
      <el-table-column :label="t('reports.topProducts.revenue')" width="120" align="right">
        <template #default="{ row }">${{ row.revenue.toFixed(2) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>
