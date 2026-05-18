<script setup lang="ts">
import { computed } from 'vue'
import { GENRES, SCOPE_LABELS, type Genre, type Scope } from '../types'

const props = defineProps<{
  genre: Genre | null
  scope: Scope
  communityGenreName?: string
}>()

defineEmits<{ change: [] }>()

const genreMeta = computed(() => GENRES.find(g => g.id === props.genre) ?? null)
const scopeMeta = computed(() => SCOPE_LABELS[props.scope])
</script>

<template>
  <div class="glass-card px-3 py-2 flex items-center justify-between gap-3 text-sm">
    <div class="flex items-center gap-2">
      <span class="text-xs text-slate-500 font-mono">挑戦中:</span>
      <span v-if="communityGenreName" class="font-bold flex items-center gap-1">
        <span>🏷️</span>
        <span>{{ communityGenreName }} (コミュニティ)</span>
      </span>
      <span v-else-if="genreMeta" class="font-bold flex items-center gap-1">
        <span>{{ genreMeta.emoji }}</span>
        <span>{{ scopeMeta.emoji }} {{ scopeMeta.name }}の{{ genreMeta.name }}</span>
      </span>
      <span v-else class="font-bold flex items-center gap-1">
        <span>🎲</span>
        <span>総合 (おまかせ)</span>
      </span>
    </div>
    <button
      @click="$emit('change')"
      class="text-xs text-blue-600 hover:underline">
      ジャンル変更
    </button>
  </div>
</template>
