<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Product } from '../../models'

defineProps<{ products: Product[]; loading: boolean }>()
const emit = defineEmits<{ order: [product: Product] }>()
const { t } = useI18n()
</script>

<template>
  <el-table v-loading="loading" :data="products" stripe>
    <el-table-column prop="sku" :label="t('products.sku')" width="140" />
    <el-table-column prop="name" :label="t('products.name')" />
    <el-table-column :label="t('products.price')" width="120">
      <template #default="{ row }">${{ row.price.toFixed(2) }}</template>
    </el-table-column>
    <el-table-column width="140">
      <template #default="{ row }">
        <el-button type="primary" size="small" @click="emit('order', row)">{{ t('products.order') }}</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>
