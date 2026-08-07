<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import type { Product } from '../../models'

const props = defineProps<{ modelValue: boolean; product: Product | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [{ warehouseId: string; quantity: number }]
}>()
const { t } = useI18n()

const formRef = ref<FormInstance>()
const form = reactive({ warehouseId: 'MAIN', quantity: 0 })

const rules: FormRules = {
  warehouseId: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  quantity: [{ type: 'number', min: 1, message: t('validation.positive'), trigger: 'change' }],
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.warehouseId = 'MAIN'
    form.quantity = 0
    formRef.value?.clearValidate()
  },
)

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form })
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('admin.addStockTitle', { name: product?.name ?? '' })" width="360"
    @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item :label="t('admin.warehouse')" prop="warehouseId">
        <el-input v-model="form.warehouseId" />
      </el-form-item>
      <el-form-item :label="t('products.quantity')" prop="quantity">
        <el-input-number v-model="form.quantity" :min="1" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="submit">{{ t('common.add') }}</el-button>
    </template>
  </el-dialog>
</template>
