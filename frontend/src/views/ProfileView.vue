<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUsersStore } from '../stores/users'
import { showApiError } from '../composables/useApiError'
import AddressForm from '../components/AddressForm.vue'
import type { Address } from '../models'

const { t } = useI18n()
const users = useUsersStore()
const loading = ref(false)
const saving = ref(false)

const form = reactive({
  phone: '',
  nationalId: '',
  address: { street: '', city: '', postalCode: '', country: '' } as Address,
})

watch(
  () => users.me,
  (me) => {
    if (!me) return
    form.phone = me.phone ?? ''
    form.nationalId = me.nationalId ?? ''
    if (me.shippingAddress) form.address = { ...me.shippingAddress }
  },
)

async function load() {
  loading.value = true
  try {
    await users.loadMe();
    await users.loadCountries();
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await users.updateMe({
      nationalIdCountry: form.address.country,
      phone: form.phone,
      nationalId: form.nationalId,
      shippingAddress: form.address,
    })
    ElMessage.success(t('profile.updated'))
  } catch (error) {
    showApiError(error, t('profile.updateError'), t('common.serviceUnavailable'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="max-w-lg">
    <h1 class="mb-4 text-xl font-semibold">{{ t('profile.title') }}</h1>
    <el-descriptions v-if="users.me" :column="1" border class="mb-6">
      <el-descriptions-item :label="t('admin.username')">{{ users.me.username }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.email')">{{ users.me.email }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.firstName')">{{ users.me.firstName ?? '—' }}</el-descriptions-item>
      <el-descriptions-item :label="t('admin.lastName')">{{ users.me.lastName ?? '—' }}</el-descriptions-item>
    </el-descriptions>
    <p class="mb-4 text-sm text-gray-500">{{ t('profile.identityFromKeycloak') }}</p>
    <el-form v-loading="loading" label-position="top">
      <el-form-item :label="t('profile.phone')">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item :label="t('profile.nationalId')">
        <el-input v-model="form.nationalId" />
      </el-form-item>
      <el-divider>{{ t('profile.shippingAddress') }}</el-divider>
      <AddressForm v-model="form.address" :countries="users.countries" prop-prefix="address" :required="false " />
      <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
    </el-form>
  </div>
</template>
