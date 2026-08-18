<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ECElementEvent } from 'echarts'
import { reportsApi } from '../../../api/reports'
import { showApiError } from '../../../composables/useApiError'
import type { UserDrillDownItem, UserGrowthReport } from '../../../models'
import ReportDrillDownModal from './ReportDrillDownModal.vue'

const { t } = useI18n()
const loading = ref(true)
const report = ref<UserGrowthReport | null>(null)

const drillDownOpen = ref(false)
const drillDownLoading = ref(false)
const drillDownTitle = ref('')
const drillDownRows = ref<UserDrillDownItem[]>([])

async function onDailyRegistrationsClick(event: ECElementEvent) {
  const date = String(event.name)
  drillDownTitle.value = t('reports.drillDown.usersTitle', { label: date })
  drillDownOpen.value = true
  drillDownLoading.value = true
  try {
    drillDownRows.value = await reportsApi.usersDrillDown({ date })
  } catch (error) {
    showApiError(error, t('reports.drillDown.loadError'))
  } finally {
    drillDownLoading.value = false
  }
}

const dailyRegistrationsOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 16, top: 24, bottom: 24 },
  xAxis: { type: 'category', data: report.value?.dailyRegistrations.map((d) => d.date) ?? [] },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{ type: 'line', data: report.value?.dailyRegistrations.map((d) => d.count) ?? [], smooth: true, color: '#67c23a' }],
}))

onMounted(async () => {
  try {
    report.value = await reportsApi.userGrowth()
  } catch (error) {
    showApiError(error, t('reports.loadError'))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <h2 class="mb-3 text-lg font-semibold">{{ t('reports.userGrowth.title') }}</h2>
    <div v-if="report" class="mb-4">
      <el-statistic :title="t('reports.userGrowth.totalNewUsers')" :value="report.totalNewUsers" />
    </div>
    <div v-if="report">
      <p class="mb-1 text-sm text-gray-500">{{ t('reports.userGrowth.dailyRegistrations') }}</p>
      <v-chart style="height: 256px" autoresize :option="dailyRegistrationsOption" @click="onDailyRegistrationsClick" />
    </div>
    <ReportDrillDownModal
      v-model="drillDownOpen"
      kind="users"
      :loading="drillDownLoading"
      :title="drillDownTitle"
      :order-rows="[]"
      :user-rows="drillDownRows"
    />
  </div>
</template>
