<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUsersStore } from '../stores/users'
import { useProductsStore } from '../stores/products'
import { inventoryApi } from '../api/inventory'
import { extractErrorMessage } from '../composables/useApiError'
import UserTable from '../components/admin/UserTable.vue'
import AdminProductTable from '../components/admin/AdminProductTable.vue'
import ProductFormDialog from '../components/admin/ProductFormDialog.vue'
import StockDialog from '../components/admin/StockDialog.vue'
import type { Product, User } from '../models'

const { t } = useI18n()
const users = useUsersStore()
const products = useProductsStore()

const productDialog = ref(false)
const stockDialog = ref(false)
const stockTarget = ref<Product | null>(null)

async function deleteUser(user: User) {
  try {
    await ElMessageBox.confirm(t('admin.confirmDeleteUser', { username: user.username }), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await users.remove(user.id)
    ElMessage.success(t('admin.userDeleted'))
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, t('admin.userDeleteError'), t('common.serviceUnavailable')))
  }
}

async function createProduct(payload: { sku: string; name: string; price: number }) {
  try {
    await products.create(payload)
    ElMessage.success(t('admin.productCreated'))
    productDialog.value = false
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, t('admin.productCreateError'), t('common.serviceUnavailable')))
  }
}

async function deleteProduct(product: Product) {
  try {
    await ElMessageBox.confirm(t('admin.confirmDeleteProduct', { name: product.name }), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await products.remove(product.id)
    ElMessage.success(t('admin.productDeleted'))
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, t('admin.productDeleteError'), t('common.serviceUnavailable')))
  }
}

function openStockDialog(product: Product) {
  stockTarget.value = product
  stockDialog.value = true
}

async function addStock(payload: { warehouseId: string; quantity: number }) {
  if (!stockTarget.value) return
  try {
    await inventoryApi.addStock(stockTarget.value.id, payload.warehouseId, payload.quantity)
    ElMessage.success(t('admin.stockAdded'))
    stockDialog.value = false
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, t('admin.stockAddError'), t('common.serviceUnavailable')))
  }
}

onMounted(() => {
  users.load()
  products.load()
})
</script>

<template>
  <div>
    <h1 class="mb-4 text-xl font-semibold">{{ t('admin.title') }}</h1>
    <el-tabs>
      <el-tab-pane :label="t('admin.users')">
        <UserTable :users="users.items" :loading="users.loading" @delete="deleteUser" />
      </el-tab-pane>

      <el-tab-pane :label="t('admin.products')">
        <div class="mb-3">
          <el-button type="primary" @click="productDialog = true">{{ t('admin.newProduct') }}</el-button>
        </div>
        <AdminProductTable :products="products.items" :loading="products.loading" @add-stock="openStockDialog" @delete="deleteProduct" />
      </el-tab-pane>
    </el-tabs>

    <ProductFormDialog v-model="productDialog" @submit="createProduct" />
    <StockDialog v-model="stockDialog" :product="stockTarget" @submit="addStock" />
  </div>
</template>
