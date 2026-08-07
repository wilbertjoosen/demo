<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AddressForm from '../AddressForm.vue'
import type { Address, User } from '../../models'

const props = defineProps<{ modelValue: boolean; user: User | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [{ displayName: string; nationalId: string; phone: string; shippingAddress: Address }]
}>()
const { t } = useI18n()

const emptyAddress: Address = { street: '', city: '', postalCode: '', country: '' }
const form = reactive({
  displayName: '',
  nationalId: '',
  phone: '',
  shippingAddress: { ...emptyAddress },
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.displayName = props.user?.displayName ?? ''
    form.nationalId = props.user?.nationalId ?? ''
    form.phone = props.user?.phone ?? ''
    form.shippingAddress = props.user?.shippingAddress ? { ...props.user.shippingAddress } : { ...emptyAddress }
  },
)

function submit() {
  emit('submit', { ...form })
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="t('admin.editUserTitle', { username: user?.username ?? '' })" width="420"
    @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top">
      <el-form-item :label="t('admin.displayName')">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item :label="t('profile.nationalId')">
        <el-input v-model="form.nationalId" />
      </el-form-item>
      <el-form-item :label="t('profile.phone')">
        <el-input v-model="form.phone" />
      </el-form-item>
      <AddressForm v-model="form.shippingAddress" />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="submit">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>
