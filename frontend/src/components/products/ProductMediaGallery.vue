<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { mediaApi } from '../../api/media'
import type { MediaAsset } from '../../models'

const props = defineProps<{ productId: string | null }>()

const items = ref<MediaAsset[]>([])
const loading = ref(false)

const photos = computed(() => items.value.filter((m) => m.type === 'PHOTO'))
const photoUrls = computed(() => photos.value.map((m) => m.url))
const videos = computed(() => items.value.filter((m) => m.type === 'VIDEO'))
const documents = computed(() => items.value.filter((m) => m.type === 'DOCUMENT'))

function isPdf(url: string): boolean {
  return url.toLowerCase().endsWith('.pdf')
}

function filenameOf(url: string): string {
  return url.split('/').pop() ?? url
}

async function load() {
  if (!props.productId) return
  loading.value = true
  try {
    items.value = await mediaApi.listByProduct(props.productId)
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.productId, load)
onMounted(load)

defineExpose({ reload: load })
</script>

<template>
  <div v-if="items.length || loading" v-loading="loading">
    <div v-if="photos.length" class="flex gap-2 overflow-x-auto pb-2">
      <el-image
        v-for="(photo, index) in photos"
        :key="photo.id"
        :src="photo.url"
        :preview-src-list="photoUrls"
        :initial-index="index"
        fit="cover"
        class="h-20 w-20 flex-shrink-0 rounded border border-gray-200"
        :title="photo.caption ?? ''"
        preview-teleported
      />
    </div>
    <div v-if="videos.length" class="mt-3 flex flex-col gap-2">
      <video v-for="video in videos" :key="video.id" :src="video.url" controls class="w-full rounded" />
    </div>
    <div v-if="documents.length" class="mt-3 flex flex-col gap-2">
      <template v-for="doc in documents" :key="doc.id">
        <iframe v-if="isPdf(doc.url)" :src="doc.url" class="h-80 w-full rounded border border-gray-200" />
        <a
          v-else
          :href="doc.url"
          target="_blank"
          rel="noopener"
          class="flex items-center gap-2 rounded border border-gray-200 p-2 text-sm hover:bg-gray-50"
        >
          <span class="text-xl">📄</span>
          <span>{{ doc.caption || filenameOf(doc.url) }}</span>
        </a>
      </template>
    </div>
  </div>
</template>
