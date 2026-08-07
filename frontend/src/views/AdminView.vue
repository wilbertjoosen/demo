<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useUsersStore } from '../stores/users'
import { useProductsStore } from '../stores/products'
import { inventoryApi } from '../api/inventory'
import { extractErrorMessage } from '../composables/useApiError'
import UserTable from '../components/admin/UserTable.vue'
import UserFormDialog from '../components/admin/UserFormDialog.vue'
import UserDetailDialog from '../components/admin/UserDetailDialog.vue'
import AdminProductTable from '../components/admin/AdminProductTable.vue'
import ProductFormDialog from '../components/admin/ProductFormDialog.vue'
import ProductDetailDialog from '../components/admin/ProductDetailDialog.vue'
import StockDialog from '../components/admin/StockDialog.vue'
import type { Address, Product, User } from '../models'

const { t } = useI18n()
const auth = useAuthStore()
const users = useUsersStore()
const products = useProductsStore()

const productDialog = ref(false)
const productDetailDialog = ref(false)
const editingProduct = ref<Product | null>(null)
const stockDialog = ref(false)
const stockTarget = ref<Product | null>(null)

const userFormDialog = ref(false)
const userDetailDialog = ref(false)
const editingUser = ref<User | null>(null)

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

function openUserEdit(user: User) {
  editingUser.value = user
  userFormDialog.value = true
}

function openUserDetail(user: User) {
  editingUser.value = user
  userDetailDialog.value = true
}

async function saveUser(payload: { displayName: string; nationalId: string; phone: string; shippingAddress: Address }) {
  if (!editingUser.value) return
  try {
    await users.update(editingUser.value.id, payload)
    ElMessage.success(t('admin.userUpdated'))
    userFormDialog.value = false
  } catch (error) {
    ElMessage.error(extractErrorMessage(error, t('admin.userUpdateError'), t('common.serviceUnavailable')))
  }
}

function openProductCreate() {
  editingProduct.value = null
  productDialog.value = true
}

function openProductEdit(product: Product) {
  editingProduct.value = product
  productDialog.value = true
}

function openProductDetail(product: Product) {
  editingProduct.value = product
  productDetailDialog.value = true
}

async function saveProduct(payload: { sku: string; name: string; price: number }) {
  try {
    if (editingProduct.value) {
      await products.update(editingProduct.value.id, payload)
      ElMessage.success(t('admin.productUpdated'))
    } else {
      await products.create(payload)
      ElMessage.success(t('admin.productCreated'))
    }
    productDialog.value = false
  } catch (error) {
    const fallback = editingProduct.value ? t('admin.productUpdateError') : t('admin.productCreateError')
    ElMessage.error(extractErrorMessage(error, fallback, t('common.serviceUnavailable')))
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
        <UserTable
          :users="users.items"
          :loading="users.loading"
          :current-user-keycloak-id="auth.keycloakId"
          @edit="openUserEdit"
          @detail="openUserDetail"
          @delete="deleteUser"
        />
      </el-tab-pane>

      <el-tab-pane :label="t('admin.products')">
        <div class="mb-3">
          <el-button type="primary" @click="openProductCreate">{{ t('admin.newProduct') }}</el-button>
        </div>
        <AdminProductTable
          :products="products.items"
          :loading="products.loading"
          @add-stock="openStockDialog"
          @edit="openProductEdit"
          @detail="openProductDetail"
          @delete="deleteProduct"
        />
      </el-tab-pane>
    </el-tabs>

    <ProductFormDialog v-model="productDialog" :product="editingProduct" @submit="saveProduct" />
    <ProductDetailDialog v-model="productDetailDialog" :product="editingProduct" />
    <StockDialog v-model="stockDialog" :product="stockTarget" @submit="addStock" />
    <UserFormDialog v-model="userFormDialog" :user="editingUser" @submit="saveUser" />
    <UserDetailDialog v-model="userDetailDialog" :user="editingUser" />
  </div>
</template>
