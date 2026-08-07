<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import type { Product } from '../../models'

const props = defineProps<{ modelValue: boolean; product?: Product | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [{ sku: string; name: string; price: number }]
}>()
const { t } = useI18n()

const isEdit = computed(() => !!props.product)

const formRef = ref<FormInstance>()
const form = reactive({ sku: '', name: '', price: 0 })

const rules: FormRules = {
  sku: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  name: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  price: [{ type: 'number', min: 0.01, message: t('validation.positive'), trigger: 'change' }],
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.sku = props.product?.sku ?? ''
    form.name = props.product?.name ?? ''
    form.price = props.product?.price ?? 0
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
  <el-dialog :model-value="modelValue" :title="isEdit ? t('admin.editProduct') : t('admin.newProduct')" width="420"
    @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item :label="t('products.sku')" prop="sku">
        <el-input v-model="form.sku" />
      </el-form-item>
      <el-form-item :label="t('products.name')" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('products.price')" prop="price">
        <el-input-number v-model="form.price" :min="0" :precision="2" :step="0.5" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="submit">{{ isEdit ? t('common.save') : t('common.create') }}</el-button>
    </template>
  </el-dialog>
</template>
