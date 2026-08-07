<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-2xl font-semibold">{{ t('home.welcome', { name: auth.username || '...' }) }}</h1>
    <p class="text-gray-600">{{ t('home.description') }}</p>
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <router-link to="/products">
        <el-card shadow="hover" class="cursor-pointer">
          <template #header>{{ t('home.browseProducts') }}</template>
          {{ t('home.browseProductsDesc') }}
        </el-card>
      </router-link>
      <router-link to="/orders">
        <el-card shadow="hover" class="cursor-pointer">
          <template #header>{{ t('home.myOrders') }}</template>
          {{ t('home.myOrdersDesc') }}
        </el-card>
      </router-link>
      <router-link v-if="auth.isAdmin" to="/admin">
        <el-card shadow="hover" class="cursor-pointer">
          <template #header>{{ t('home.admin') }}</template>
          {{ t('home.adminDesc') }}
        </el-card>
      </router-link>
    </div>
  </div>
</template>
