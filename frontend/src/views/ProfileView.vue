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
  displayName: '',
  phone: '',
  nationalId: '',
  address: { street: '', city: '', postalCode: '', country: '' } as Address,
})

watch(
  () => users.me,
  (me) => {
    if (!me) return
    form.displayName = me.displayName ?? ''
    form.phone = me.phone ?? ''
    form.nationalId = me.nationalId ?? ''
    if (me.shippingAddress) form.address = { ...me.shippingAddress }
  },
)

async function load() {
  loading.value = true
  try {
    await users.loadMe()
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await users.updateMe({
      displayName: form.displayName,
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
    <el-form v-loading="loading" label-position="top">
      <el-form-item :label="t('profile.displayName')">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item :label="t('profile.phone')">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item :label="t('profile.nationalId')">
        <el-input v-model="form.nationalId" />
      </el-form-item>
      <el-divider>{{ t('profile.shippingAddress') }}</el-divider>
      <AddressForm v-model="form.address" prop-prefix="address" :required="false" />
      <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
    </el-form>
  </div>
</template>
