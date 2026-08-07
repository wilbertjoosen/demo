<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Product } from '../../models'

defineProps<{ modelValue: boolean; product: Product | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString() : '—'
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('admin.productDetailTitle', { sku: product?.sku ?? '' })" width="420"
    @update:model-value="emit('update:modelValue', $event)">
    <el-descriptions v-if="product" :column="1" border>
      <el-descriptions-item :label="t('products.sku')">{{ product.sku }}</el-descriptions-item>
      <el-descriptions-item :label="t('products.name')">{{ product.name }}</el-descriptions-item>
      <el-descriptions-item :label="t('products.price')">${{ product.price.toFixed(2) }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.createdAt')">{{ formatDate(product.createdAt) }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.updatedAt')">{{ formatDate(product.updatedAt) }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.createdBy')">{{ product.createdBy ?? '—' }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.lastModifiedBy')">{{ product.lastModifiedBy ?? '—' }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>
