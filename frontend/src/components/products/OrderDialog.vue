<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AddressForm from '../AddressForm.vue'
import type { Address, Product } from '../../models'

const props = defineProps<{
  modelValue: boolean
  product: Product | null
  initialAddress: Address | null
  submitting: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [{ quantity: number; address: Address }]
}>()
const { t } = useI18n()

const form = reactive({
  quantity: 1,
  address: { street: '', city: '', postalCode: '', country: '' } as Address,
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.quantity = 1
    form.address = props.initialAddress ? { ...props.initialAddress } : { street: '', city: '', postalCode: '', country: '' }
  },
)

function submit() {
  emit('submit', { quantity: form.quantity, address: form.address })
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('products.orderDialogTitle', { name: product?.name ?? '' })" width="420"
    @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top">
      <el-form-item :label="t('products.quantity')">
        <el-input-number v-model="form.quantity" :min="1" />
      </el-form-item>
      <AddressForm v-model="form.address" />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">{{ t('products.placeOrder') }}</el-button>
    </template>
  </el-dialog>
</template>
