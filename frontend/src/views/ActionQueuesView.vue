<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import PaymentReviewQueue from '../components/queues/PaymentReviewQueue.vue'
import ShipmentQueue from '../components/queues/ShipmentQueue.vue'
import DeliveryQueue from '../components/queues/DeliveryQueue.vue'

const { t } = useI18n()
const auth = useAuthStore()
</script>

<template>
  <div>
    <h1 class="mb-4 text-xl font-semibold">{{ t('queues.title') }}</h1>
    <el-tabs>
      <el-tab-pane v-if="auth.isAdmin || auth.isFinance" :label="t('queues.paymentReview')" lazy>
        <PaymentReviewQueue />
      </el-tab-pane>
      <el-tab-pane v-if="auth.isAdmin || auth.isWarehouse" :label="t('queues.shipments')" lazy>
        <ShipmentQueue />
      </el-tab-pane>
      <el-tab-pane v-if="auth.isAdmin || auth.isDeliveryAgent" :label="t('queues.deliveries')" lazy>
        <DeliveryQueue />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
