<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [{ sku: string; name: string; price: number }]
}>()
const { t } = useI18n()

const form = reactive({ sku: '', name: '', price: 0 })

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.sku = ''
    form.name = ''
    form.price = 0
  },
)
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('admin.newProduct')" width="420" @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top">
      <el-form-item :label="t('products.sku')">
        <el-input v-model="form.sku" />
      </el-form-item>
      <el-form-item :label="t('products.name')">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="t('products.price')">
        <el-input-number v-model="form.price" :min="0" :precision="2" :step="0.5" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="emit('submit', { ...form })">{{ t('common.create') }}</el-button>
    </template>
  </el-dialog>
</template>
