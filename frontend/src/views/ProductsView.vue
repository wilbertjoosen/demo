<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useProductsStore } from '../stores/products'
import { useOrdersStore } from '../stores/orders'
import { useUsersStore } from '../stores/users'
import { extractErrorMessage } from '../composables/useApiError'
import ProductTable from '../components/products/ProductTable.vue'
import OrderDialog from '../components/products/OrderDialog.vue'
import type { Address, PaymentMethod, Product, ShippingCarrier } from '../models'

const { t } = useI18n()
const products = useProductsStore()
const orders = useOrdersStore()
const users = useUsersStore()

const dialogVisible = ref(false)
const placing = ref(false)
const selected = ref<Product | null>(null)

async function openOrderDialog(product: Product) {
  selected.value = product
  if (!users.me) {
    try {
      await users.loadMe()
    } catch {
      // fall back to blank address
    }
  }
  dialogVisible.value = true
}

async function placeOrder(payload: {
  quantity: number
  address: Address
  paymentMethod: PaymentMethod
  shippingCarrier: ShippingCarrier
}) {
  if (!selected.value) return
  placing.value = true
  try {
    await orders.place({
      productId: selected.value.id,
      quantity: payload.quantity,
      shippingAddress: payload.address,
      paymentMethod: payload.paymentMethod,
      shippingCarrier: payload.shippingCarrier,
    })
    ElMessage.success(t('products.placedSuccess'))
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, t('products.placeError'), t('common.serviceUnavailable')))
  } finally {
    placing.value = false
  }
}

onMounted(() => products.load())
</script>

<template>
  <div>
    <h1 class="mb-4 text-xl font-semibold">{{ t('products.title') }}</h1>
    <ProductTable :products="products.items" :loading="products.loading" @order="openOrderDialog" />
    <OrderDialog
      v-model="dialogVisible"
      :product="selected"
      :initial-address="users.me?.shippingAddress ?? null"
      :submitting="placing"
      @submit="placeOrder"
    />
  </div>
</template>
