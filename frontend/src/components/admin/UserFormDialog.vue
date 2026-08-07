<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import AddressForm from '../AddressForm.vue'
import type { Address, User } from '../../models'

const props = defineProps<{ modelValue: boolean; user: User | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [
    {
      keycloakId?: string
      username?: string
      email?: string
      displayName: string
      nationalId: string
      phone: string
      shippingAddress: Address
    },
  ]
}>()
const { t } = useI18n()

const isCreate = computed(() => !props.user)

const formRef = ref<FormInstance>()
const emptyAddress: Address = { street: '', city: '', postalCode: '', country: '' }
const form = reactive({
  keycloakId: '',
  username: '',
  email: '',
  displayName: '',
  nationalId: '',
  phone: '',
  shippingAddress: { ...emptyAddress },
})

const rules: FormRules = {
  keycloakId: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  username: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  email: [
    { required: true, message: t('validation.required'), trigger: 'blur' },
    { type: 'email', message: t('validation.email'), trigger: 'blur' },
  ],
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.keycloakId = ''
    form.username = props.user?.username ?? ''
    form.email = props.user?.email ?? ''
    form.displayName = props.user?.displayName ?? ''
    form.nationalId = props.user?.nationalId ?? ''
    form.phone = props.user?.phone ?? ''
    form.shippingAddress = props.user?.shippingAddress ? { ...props.user.shippingAddress } : { ...emptyAddress }
    formRef.value?.clearValidate()
  },
)

async function submit() {
  if (isCreate.value) {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
  }
  emit('submit', {
    ...(isCreate.value ? { keycloakId: form.keycloakId, username: form.username, email: form.email } : {}),
    displayName: form.displayName,
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
    <el-form ref="formRef" :model="form" :rules="isCreate ? rules : {}" label-position="top">
      <template v-if="isCreate">
        <el-form-item :label="t('admin.keycloakId')" prop="keycloakId">
          <el-input v-model="form.keycloakId" :placeholder="t('admin.keycloakIdHint')" />
        </el-form-item>
        <el-form-item :label="t('admin.username')" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item :label="t('admin.email')" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
      </template>
      <el-form-item :label="t('admin.displayName')">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item :label="t('profile.nationalId')">
        <el-input v-model="form.nationalId" />
      </el-form-item>
      <el-form-item :label="t('profile.phone')">
        <el-input v-model="form.phone" />
      </el-form-item>
      <AddressForm v-model="form.shippingAddress" prop-prefix="shippingAddress" :required="false" />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="submit">{{ isCreate ? t('common.create') : t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>
