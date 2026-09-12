<script setup lang="ts">
import { computed } from 'vue'
import { FAME_TIERS, fameTierMeta, type FameTier } from '../fameTier'

const props = defineProps<{
  modelValue: FameTier | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: FameTier | null]
}>()

const current = computed(() => fameTierMeta(props.modelValue))
</script>

<template>
  <div class="glass-card p-3 flex flex-wrap items-center gap-2">
    <span class="text-xs font-mono text-slate-500 mr-1">記事の知名度:</span>
    <button v-for="t in FAME_TIERS" :key="String(t.id)"
      type="button"
      @click="emit('update:modelValue', t.id)"
      :title="t.description"
      :aria-pressed="modelValue === t.id"
      :class="[
        'px-3 py-1 rounded-md text-xs flex items-center gap-1 transition',
        modelValue === t.id
          ? 'theme-bg-soft theme-text font-bold ring-1 theme-border'
          : 'bg-slate-100 hover:bg-slate-200 text-slate-600',
      ]">
      <span>{{ t.emoji }}</span>
      <span>{{ t.name }}</span>
    </button>
    <span class="text-xs text-slate-400 ml-auto">{{ current.description }}</span>
  </div>
</template>
