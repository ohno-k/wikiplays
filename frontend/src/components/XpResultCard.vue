<script setup lang="ts">
import { levelTitle } from '../scoring'

defineProps<{
  xp: {
    xpGained?: number | null
    xpCapped?: boolean | null
    leveledUp?: boolean | null
    level?: number | null
    xpIntoLevel?: number | null
    xpForNextLevel?: number | null
    dailyRemaining?: number | null
    streakDays?: number | null
  }
}>()
</script>

<template>
  <div v-if="xp.xpGained != null"
    :class="[
      'rounded-lg p-4 space-y-2 border',
      xp.leveledUp
        ? 'bg-gradient-to-r from-amber-50 to-rose-50 border-amber-300'
        : 'bg-emerald-50 border-emerald-200'
    ]">
    <div class="flex items-baseline justify-between">
      <div class="text-sm font-mono text-slate-500">EXPERIENCE</div>
      <div v-if="xp.leveledUp" class="text-sm font-bold text-amber-600">
        ✨ LEVEL UP! → Lv.{{ xp.level }} {{ levelTitle(xp.level ?? 1) }}
      </div>
      <div v-else class="text-sm font-bold text-emerald-700">Lv.{{ xp.level }} {{ levelTitle(xp.level ?? 1) }}</div>
    </div>
    <div class="text-2xl font-bold text-slate-800">
      +{{ xp.xpGained }} XP
      <span v-if="xp.xpCapped" class="text-xs font-normal text-slate-500 ml-2">(本日のキャップに到達)</span>
    </div>
    <div v-if="xp.xpForNextLevel != null && xp.xpIntoLevel != null" class="h-2 bg-slate-200 rounded-full overflow-hidden">
      <div class="h-full bg-gradient-to-r from-sky-400 to-emerald-400 transition-all"
        :style="{ width: `${Math.min(100, (xp.xpIntoLevel / xp.xpForNextLevel) * 100)}%` }"></div>
    </div>
    <div class="text-xs text-slate-500 flex flex-wrap justify-between gap-x-3">
      <span>{{ xp.xpIntoLevel }} / {{ xp.xpForNextLevel }} XP</span>
      <span v-if="xp.streakDays != null && xp.streakDays > 0">🔥 {{ xp.streakDays }} 日連続プレイ</span>
      <span v-if="xp.dailyRemaining != null">本日の残り獲得可能: {{ xp.dailyRemaining }} XP</span>
    </div>
  </div>
</template>
