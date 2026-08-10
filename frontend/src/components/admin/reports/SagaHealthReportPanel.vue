<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { reportsApi } from '../../../api/reports'
import { showApiError } from '../../../composables/useApiError'
import type { SagaHealthReport } from '../../../models'

const { t } = useI18n()
const loading = ref(true)
const report = ref<SagaHealthReport | null>(null)

function stageLabel(stage: string): string {
  return stage === 'USER_CANCELLED_BEFORE_PAYMENT' ? t('reports.sagaHealth.userCancelledBeforePayment') : stage
}

const failuresByStageOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['40%', '70%'],
      data: Object.entries(report.value?.failuresByStage ?? {}).map(([name, value]) => ({ name: stageLabel(name), value })),
    },
  ],
}))

onMounted(async () => {
  try {
    report.value = await reportsApi.sagaHealth()
  } catch (error) {
    showApiError(error, t('reports.loadError'))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <h2 class="mb-3 text-lg font-semibold">{{ t('reports.sagaHealth.title') }}</h2>
    <div v-if="report" class="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
      <el-statistic :title="t('reports.sagaHealth.inProgress')" :value="report.inProgressCount" />
      <el-statistic :title="t('reports.sagaHealth.completed')" :value="report.completedCount" />
      <el-statistic :title="t('reports.sagaHealth.cancelled')" :value="report.cancelledCount" />
      <el-statistic :title="t('reports.sagaHealth.cancellationRate')" :value="report.cancellationRate * 100" :precision="1" suffix="%" />
      <el-statistic
        :title="t('reports.sagaHealth.avgTimeToConfirmation')"
        :value="report.avgTimeToConfirmationMinutes ?? 0"
        :precision="1"
      >
        <template #suffix>{{ report.avgTimeToConfirmationMinutes === null ? '' : 'min' }}</template>
      </el-statistic>
    </div>
    <p v-if="report && report.avgTimeToConfirmationMinutes === null" class="mb-4 text-sm text-gray-400">
      {{ t('reports.sagaHealth.noCompletedOrders') }}
    </p>
    <div v-if="report && report.cancelledCount > 0">
      <p class="mb-1 text-sm text-gray-500">{{ t('reports.sagaHealth.failuresByStage') }}</p>
      <v-chart class="h-64" autoresize :option="failuresByStageOption" />
    </div>
  </div>
</template>
