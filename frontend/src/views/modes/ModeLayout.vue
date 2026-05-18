<script setup lang="ts">
import type { ModeTheme } from '../../types'

defineProps<{
  modeName: string
  shortName: string
  emoji: string
  theme?: ModeTheme
  gradient?: string
}>()
</script>

<template>
  <section class="space-y-5 animate-fade-in" :data-theme="theme ?? 'blue'">
    <router-link
      to="/"
      class="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700 transition group">
      <span class="inline-block transition-transform group-hover:-translate-x-0.5">←</span>
      モード選択に戻る
    </router-link>

    <header class="glass-card overflow-hidden">
      <div :class="['h-1 bg-gradient-to-r', gradient ?? 'from-sky-500 to-indigo-600']"></div>
      <div class="flex items-center gap-4 p-5">
        <div
          :class="[
            'inline-flex items-center justify-center w-14 h-14 rounded-xl text-3xl text-white shadow-md bg-gradient-to-br',
            gradient ?? 'from-sky-500 to-indigo-600',
          ]">
          {{ emoji }}
        </div>
        <div>
          <div class="text-xs font-mono text-slate-500 tracking-wide">{{ shortName }}</div>
          <h1 class="text-2xl font-bold tracking-tight">{{ modeName }}</h1>
        </div>
      </div>
    </header>

    <slot />
  </section>
</template>
