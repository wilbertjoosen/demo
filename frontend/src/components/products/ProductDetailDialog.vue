<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ProductComments from './ProductComments.vue'
import ProductMediaGallery from './ProductMediaGallery.vue'
import type { Product } from '../../models'

const props = defineProps<{ modelValue: boolean; product: Product | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()
</script>

<template>
  <el-dialog :model-value="modelValue" :title="product?.name ?? ''" width="480" @update:model-value="emit('update:modelValue', $event)">
    <el-descriptions v-if="product" :column="1" border class="mb-4">
      <el-descriptions-item :label="t('products.sku')">{{ product.sku }}</el-descriptions-item>
      <el-descriptions-item :label="t('products.price')">${{ product.price.toFixed(2) }}</el-descriptions-item>
    </el-descriptions>
    <ProductMediaGallery :product-id="props.product?.id ?? null" class="mb-4" />
    <el-divider>{{ t('comments.button') }}</el-divider>
    <ProductComments :product-id="props.product?.id ?? null" />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>
