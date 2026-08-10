<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { reportsApi } from '../../../api/reports'
import { showApiError } from '../../../composables/useApiError'
import type { OrdersRevenueReport } from '../../../models'

const { t } = useI18n()
const loading = ref(true)
const report = ref<OrdersRevenueReport | null>(null)

const dailyOrdersOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 16, top: 24, bottom: 24 },
  xAxis: { type: 'category', data: report.value?.dailyOrderCounts.map((d) => d.date) ?? [] },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{ type: 'bar', data: report.value?.dailyOrderCounts.map((d) => d.count) ?? [], color: '#409eff' }],
}))

const byStatusOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['40%', '70%'],
      data: Object.entries(report.value?.byStatus ?? {}).map(([name, value]) => ({ name, value })),
    },
  ],
}))

onMounted(async () => {
  try {
    report.value = await reportsApi.ordersRevenue()
  } catch (error) {
    showApiError(error, t('reports.loadError'))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <h2 class="mb-3 text-lg font-semibold">{{ t('reports.ordersRevenue.title') }}</h2>
    <div v-if="report" class="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-2">
      <el-statistic :title="t('reports.ordersRevenue.totalOrders')" :value="report.totalOrders" />
      <el-statistic :title="t('reports.ordersRevenue.totalRevenue')" :value="report.totalRevenue" :precision="2" prefix="$" />
    </div>
    <div v-if="report" class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <div>
        <p class="mb-1 text-sm text-gray-500">{{ t('reports.ordersRevenue.dailyOrders') }}</p>
        <v-chart class="h-64" autoresize :option="dailyOrdersOption" />
      </div>
      <div>
        <p class="mb-1 text-sm text-gray-500">{{ t('reports.ordersRevenue.byStatus') }}</p>
        <v-chart class="h-64" autoresize :option="byStatusOption" />
      </div>
    </div>
  </div>
</template>
