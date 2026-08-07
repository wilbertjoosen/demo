<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Address } from '../models'

defineProps<{ modelValue: Address }>()
defineEmits<{ 'update:modelValue': [value: Address] }>()

const { t } = useI18n()
</script>

<template>
  <el-form-item :label="t('address.street')">
    <el-input :model-value="modelValue.street" @update:model-value="$emit('update:modelValue', { ...modelValue, street: $event })" />
  </el-form-item>
  <el-form-item :label="t('address.city')">
    <el-input :model-value="modelValue.city" @update:model-value="$emit('update:modelValue', { ...modelValue, city: $event })" />
  </el-form-item>
  <el-form-item :label="t('address.postalCode')">
    <el-input :model-value="modelValue.postalCode" @update:model-value="$emit('update:modelValue', { ...modelValue, postalCode: $event })" />
  </el-form-item>
  <el-form-item :label="t('address.country')">
    <el-input :model-value="modelValue.country" @update:model-value="$emit('update:modelValue', { ...modelValue, country: $event })" />
  </el-form-item>
</template>
