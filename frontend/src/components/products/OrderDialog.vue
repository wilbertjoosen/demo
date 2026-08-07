<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import AddressForm from '../AddressForm.vue'
import { paymentsApi } from '../../api/payments'
import { shipmentsApi } from '../../api/shipments'
import type { Address, PaymentMethod, PaymentMethodAvailability, Product, ShippingCarrier } from '../../models'

const props = defineProps<{
  modelValue: boolean
  product: Product | null
  initialAddress: Address | null
  submitting: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [{ quantity: number; address: Address; paymentMethod: PaymentMethod; shippingCarrier: ShippingCarrier }]
}>()
const { t } = useI18n()

const paymentMethods: PaymentMethod[] = ['CREDIT_CARD', 'DEBIT_CARD', 'PAYPAL', 'PIX', 'BOLETO']
const shippingCarriers: ShippingCarrier[] = ['UPS', 'DHL']

const formRef = ref<FormInstance>()
const form = reactive({
  quantity: 1,
  address: { street: '', city: '', postalCode: '', country: '' } as Address,
  paymentMethod: 'CREDIT_CARD' as PaymentMethod,
  shippingCarrier: 'UPS' as ShippingCarrier,
})

const methodAvailability = ref<PaymentMethodAvailability | null>(null)
const shippingCost = ref<number | null>(null)
const quoting = ref(false)

async function refreshQuote() {
  quoting.value = true
  try {
    const quote = await shipmentsApi.quote(form.shippingCarrier, form.quantity)
    shippingCost.value = quote.cost
  } catch {
    shippingCost.value = null
  } finally {
    quoting.value = false
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    form.quantity = 1
    form.address = props.initialAddress ? { ...props.initialAddress } : { street: '', city: '', postalCode: '', country: '' }
    form.paymentMethod = 'CREDIT_CARD'
    form.shippingCarrier = 'UPS'
    methodAvailability.value = null
    refreshQuote()
    formRef.value?.clearValidate()
    try {
      methodAvailability.value = await paymentsApi.methods()
    } catch {
      methodAvailability.value = null
    }
  },
)

watch(() => [form.shippingCarrier, form.quantity], refreshQuote)

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', {
    quantity: form.quantity,
    address: form.address,
    paymentMethod: form.paymentMethod,
    shippingCarrier: form.shippingCarrier,
  })
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('products.orderDialogTitle', { name: product?.name ?? '' })" width="460"
    @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" label-position="top">
      <el-form-item :label="t('products.quantity')">
        <el-input-number v-model="form.quantity" :min="1" />
      </el-form-item>
      <AddressForm v-model="form.address" prop-prefix="address" />
      <el-form-item :label="t('products.paymentMethod')">
        <el-select v-model="form.paymentMethod" class="w-full">
          <el-option
            v-for="method in paymentMethods"
            :key="method"
            :value="method"
            :disabled="methodAvailability !== null && methodAvailability[method] === false"
          >
            <span>{{ t(`payment.methods.${method}`) }}</span>
            <span v-if="methodAvailability !== null && methodAvailability[method] === false" class="text-gray-400 ml-2">
              ({{ t('payment.unavailable') }})
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item :label="t('products.shippingCarrier')">
        <el-select v-model="form.shippingCarrier" class="w-full">
          <el-option v-for="carrier in shippingCarriers" :key="carrier" :value="carrier" :label="t(`shipping.carriers.${carrier}`)" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('products.shippingCost')">
        <span v-if="quoting">…</span>
        <span v-else-if="shippingCost !== null">${{ shippingCost.toFixed(2) }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">{{ t('products.placeOrder') }}</el-button>
    </template>
  </el-dialog>
</template>
