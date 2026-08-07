<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ShoppingCart, View } from '@element-plus/icons-vue'
import type { Product } from '../../models'

const props = defineProps<{ products: Product[]; loading: boolean }>()
const emit = defineEmits<{ order: [product: Product]; detail: [product: Product] }>()
const { t } = useI18n()

const pageSize = 10
const currentPage = ref(1)

const pagedProducts = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return props.products.slice(start, start + pageSize)
})

watch(
  () => props.products.length,
  () => {
    const maxPage = Math.max(1, Math.ceil(props.products.length / pageSize))
    if (currentPage.value > maxPage) currentPage.value = maxPage
  },
)
</script>

<template>
  <el-table v-loading="loading" :data="pagedProducts" stripe>
    <el-table-column prop="sku" :label="t('products.sku')" width="140" />
    <el-table-column prop="name" :label="t('products.name')" />
    <el-table-column :label="t('products.price')" width="120">
      <template #default="{ row }">${{ row.price.toFixed(2) }}</template>
    </el-table-column>
    <el-table-column width="110">
      <template #default="{ row }">
        <el-tooltip :content="t('products.order')">
          <el-button type="primary" size="small" :icon="ShoppingCart" circle @click="emit('order', row)" />
        </el-tooltip>
        <el-tooltip :content="t('common.detail')">
          <el-button size="small" :icon="View" circle @click="emit('detail', row)" />
        </el-tooltip>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    v-if="products.length > pageSize"
    class="mt-3 justify-end"
    layout="prev, pager, next"
    :page-size="pageSize"
    :total="products.length"
    v-model:current-page="currentPage"
  />
</template>
