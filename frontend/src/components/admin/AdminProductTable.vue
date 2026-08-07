<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Product } from '../../models'

const props = defineProps<{ products: Product[]; loading: boolean }>()
const emit = defineEmits<{
  'add-stock': [product: Product]
  edit: [product: Product]
  detail: [product: Product]
  delete: [product: Product]
}>()
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
    <el-table-column :label="t('products.price')" width="100">
      <template #default="{ row }">${{ row.price.toFixed(2) }}</template>
    </el-table-column>
    <el-table-column width="340">
      <template #default="{ row }">
        <el-button size="small" @click="emit('detail', row)">{{ t('common.detail') }}</el-button>
        <el-button size="small" @click="emit('edit', row)">{{ t('common.edit') }}</el-button>
        <el-button size="small" @click="emit('add-stock', row)">{{ t('admin.addStock') }}</el-button>
        <el-button size="small" type="danger" @click="emit('delete', row)">{{ t('common.delete') }}</el-button>
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
