<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AddressForm from '../AddressForm.vue'
import type { Address } from '../../models'

const props = defineProps<{
  modelValue: boolean
  initialAddress: Address | null
  saving: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [Address] }>()
const { t } = useI18n()

const form = reactive<Address>({ street: '', city: '', postalCode: '', country: '' })

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.assign(form, props.initialAddress ?? { street: '', city: '', postalCode: '', country: '' })
  },
)
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('orders.updateAddressTitle')" width="420"
    @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top">
      <AddressForm :model-value="form" @update:model-value="Object.assign(form, $event)" />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="emit('submit', { ...form })">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>
