<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormItemRule } from 'element-plus'
import type { Address, Country } from '../models'

const props = withDefaults(defineProps<{ modelValue: Address; propPrefix: string; required?: boolean, countries: Country[] | null, }>(), {
  required: true,
})
defineEmits<{ 'update:modelValue': [value: Address] }>()

const { t } = useI18n()

function fieldProp(field: keyof Address): string {
  return `${props.propPrefix}.${field}`
}

const rules = computed<FormItemRule[]>(() => (props.required ? [{ required: true, message: t('validation.required'), trigger: 'blur' }] : []))
</script>

<template>
  <el-form-item :label="t('address.street')" :prop="fieldProp('street')" :rules="rules">
    <el-input :model-value="modelValue.street" @update:model-value="$emit('update:modelValue', { ...modelValue, street: $event })" />
  </el-form-item>
  <el-form-item :label="t('address.city')" :prop="fieldProp('city')" :rules="rules">
    <el-input :model-value="modelValue.city" @update:model-value="$emit('update:modelValue', { ...modelValue, city: $event })" />
  </el-form-item>
  <el-form-item :label="t('address.postalCode')" :prop="fieldProp('postalCode')" :rules="rules">
    <el-input :model-value="modelValue.postalCode" @update:model-value="$emit('update:modelValue', { ...modelValue, postalCode: $event })" />
  </el-form-item>
  <el-form-item :label="t('address.country')" :prop="fieldProp('country')" :rules="rules">
    <el-select
        :model-value="modelValue.country"
        @update:model-value="$emit('update:modelValue', { ...modelValue, country: $event })"
    >
      <el-option
          v-for="country in (props.countries ?? [])"
          :key="country.code"
          :value="country.code"
          :label="country.name"
      />
    </el-select>
  </el-form-item>
</template>
