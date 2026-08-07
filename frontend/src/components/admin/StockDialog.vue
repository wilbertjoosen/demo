<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Product } from '../../models'

const props = defineProps<{ modelValue: boolean; product: Product | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [{ warehouseId: string; quantity: number }]
}>()
const { t } = useI18n()

const form = reactive({ warehouseId: 'MAIN', quantity: 0 })

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.warehouseId = 'MAIN'
    form.quantity = 0
  },
)
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('admin.addStockTitle', { name: product?.name ?? '' })" width="360"
    @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top">
      <el-form-item :label="t('admin.warehouse')">
        <el-input v-model="form.warehouseId" />
      </el-form-item>
      <el-form-item :label="t('products.quantity')">
        <el-input-number v-model="form.quantity" :min="1" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="emit('submit', { ...form })">{{ t('common.add') }}</el-button>
    </template>
  </el-dialog>
</template>
