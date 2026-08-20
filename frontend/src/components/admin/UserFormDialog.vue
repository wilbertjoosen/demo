<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import AddressForm from '../AddressForm.vue'
import { useCommonStore } from '../../stores/common'
import type { Address, User } from '../../models'

const props = defineProps<{ modelValue: boolean; user: User | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [
    {
      username?: string
      email?: string
      firstName?: string
      lastName?: string
      password?: string
      nationalId: string
      phone: string
      shippingAddress: Address
    },
  ]
}>()
const { t } = useI18n()
const common = useCommonStore()

const isCreate = computed(() => !props.user)

const formRef = ref<FormInstance>()
const emptyAddress: Address = { street: '', city: '', postalCode: '', country: '' }
const form = reactive({
  username: '',
  email: '',
  firstName: '',
  lastName: '',
  password: '',
  nationalId: '',
  phone: '',
  shippingAddress: { ...emptyAddress },
})

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  email: [
    { required: true, message: t('validation.required'), trigger: 'blur' },
    { type: 'email', message: t('validation.email'), trigger: 'blur' },
  ],
  firstName: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  lastName: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  ...(isCreate.value ? { password: [{ required: true, message: t('validation.required'), trigger: 'blur' }] } : {}),
}))

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    if (common.countries.length === 0) common.loadCountries()
    form.username = props.user?.username ?? ''
    form.email = props.user?.email ?? ''
    form.firstName = props.user?.firstName ?? ''
    form.lastName = props.user?.lastName ?? ''
    form.password = ''
    form.nationalId = props.user?.nationalId ?? ''
    form.phone = props.user?.phone ?? ''
    form.shippingAddress = props.user?.shippingAddress ? { ...props.user.shippingAddress } : { ...emptyAddress }
    formRef.value?.clearValidate()
  },
)

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', {
    username: form.username,
    email: form.email,
    firstName: form.firstName,
    lastName: form.lastName,
    ...(isCreate.value ? { password: form.password } : {}),
    nationalId: form.nationalId,
    phone: form.phone,
    shippingAddress: form.shippingAddress,
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isCreate ? t('admin.newUser') : t('admin.editUserTitle', { username: user?.username ?? '' })"
    width="420"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-alert type="info" :closable="false" :title="t('admin.identityStoredInKeycloak')" class="mb-4" />
      <el-form-item :label="t('admin.username')" prop="username">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item :label="t('admin.email')" prop="email">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item :label="t('admin.firstName')" prop="firstName">
        <el-input v-model="form.firstName" />
      </el-form-item>
      <el-form-item :label="t('admin.lastName')" prop="lastName">
        <el-input v-model="form.lastName" />
      </el-form-item>
      <el-form-item v-if="isCreate" :label="t('admin.temporaryPassword')" prop="password">
        <el-input v-model="form.password" show-password />
      </el-form-item>
      <el-form-item :label="t('profile.nationalId')">
        <el-input v-model="form.nationalId" />
      </el-form-item>
      <el-form-item :label="t('profile.phone')">
        <el-input v-model="form.phone" />
      </el-form-item>
      <AddressForm v-model="form.shippingAddress" :countries="common.countries" prop-prefix="shippingAddress" :required="false" />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="submit">{{ isCreate ? t('common.create') : t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>
