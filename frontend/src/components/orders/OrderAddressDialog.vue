<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import AddressForm from '../AddressForm.vue'
import type { Address } from '../../models'

const props = defineProps<{
  modelValue: boolean
  initialAddress: Address | null
  saving: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [Address] }>()
const { t } = useI18n()

const formRef = ref<FormInstance>()
const form = reactive<{ address: Address }>({ address: { street: '', city: '', postalCode: '', country: '' } })

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.assign(form.address, props.initialAddress ?? { street: '', city: '', postalCode: '', country: '' })
    formRef.value?.clearValidate()
  },
)

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form.address })
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('orders.updateAddressTitle')" width="420"
    @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" label-position="top">
      <AddressForm :model-value="form.address" prop-prefix="address" @update:model-value="Object.assign(form.address, $event)" />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>
