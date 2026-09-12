<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getPlayHistory, clearPlayHistory, getDisplayName, setDisplayName, type PlayRecord } from '../api'
import { GENRES, SCOPE_LABELS, PLAY_MODE_LABELS } from '../types'

const history = ref<PlayRecord[]>([])
const displayName = ref(getDisplayName())

function load() {
  history.value = getPlayHistory()
}

onMounted(load)

const totalPlays = computed(() => history.value.length)
const totalScore = computed(() => history.value.reduce((s, r) => s + r.score, 0))
const averageScore = computed(() =>
  totalPlays.value > 0 ? Math.round(totalScore.value / totalPlays.value) : 0
)
const bestRecord = computed(() => {
  if (history.value.length === 0) return null
  return [...history.value].sort((a, b) => b.score - a.score)[0]
})

interface GenreStat {
  key: string
  emoji: string
  label: string
  count: number
  totalScore: number
  best: number
  avg: number
}

const genreStats = computed<GenreStat[]>(() => {
  const map = new Map<string, GenreStat>()
  for (const r of history.value) {
    let key: string
    let emoji: string
    let label: string
    if (r.communityGenreName) {
      key = `c:${r.communityGenreId}`
      emoji = '🏷️'
      label = r.communityGenreName + ' (コミュニティ)'
    } else if (r.genre) {
      const meta = GENRES.find(g => g.id === r.genre)
      key = `g:${r.genre}:${r.scope ?? ''}`
      emoji = meta?.emoji ?? '🎲'
      const scopeLabel = r.scope ? SCOPE_LABELS[r.scope].name : ''
      label = `${scopeLabel}${scopeLabel ? 'の' : ''}${meta?.name ?? r.genre}`
    } else {
      key = 'random'
      emoji = '🎲'
      label = '総合 (おまかせ)'
    }
    if (!map.has(key)) {
      map.set(key, { key, emoji, label, count: 0, totalScore: 0, best: 0, avg: 0 })
    }
    const stat = map.get(key)!
    stat.count++
    stat.totalScore += r.score
    if (r.score > stat.best) stat.best = r.score
  }
  for (const s of map.values()) s.avg = Math.round(s.totalScore / s.count)
  return Array.from(map.values()).sort((a, b) => b.count - a.count)
})

const recentHistory = computed(() => history.value.slice(0, 20))

function formatDate(iso: string): string {
  const d = new Date(iso)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function recordLabel(r: PlayRecord): { emoji: string; text: string } {
  if (r.communityGenreName) return { emoji: '🏷️', text: r.communityGenreName }
  if (r.genre) {
    const meta = GENRES.find(g => g.id === r.genre)
    const scopeLabel = r.scope ? SCOPE_LABELS[r.scope].name : ''
    return { emoji: meta?.emoji ?? '🎲', text: `${scopeLabel}${scopeLabel ? 'の' : ''}${meta?.name ?? r.genre}` }
  }
  return { emoji: '🎲', text: '総合' }
}

function saveName() {
  setDisplayName(displayName.value)
}

function reset() {
  if (confirm('プレイ履歴を全て削除します。よろしいですか?')) {
    clearPlayHistory()
    load()
  }
}
</script>

<template>
  <section class="space-y-5 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-5 space-y-2">
      <div class="text-xs font-mono text-slate-500">MY STATS</div>
      <h1 class="text-2xl font-bold tracking-tight">マイ統計</h1>
      <p class="text-sm text-slate-600">
        このブラウザに保存されたプレイ履歴から、あなたの記録を集計します。
        (アカウント登録なし)
      </p>
    </div>

    <!-- プロフィール (名前のみ) -->
    <div class="glass-card p-4 flex items-center gap-3">
      <span class="text-sm font-bold text-slate-600">名前:</span>
      <input v-model="displayName" maxlength="32" placeholder="名無し"
        class="flex-1 border border-slate-300 rounded px-3 py-2 text-sm" />
      <button @click="saveName" class="px-3 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 text-sm">保存</button>
    </div>

    <!-- サマリー -->
    <div class="grid grid-cols-3 gap-3">
      <div class="glass-card p-4 text-center">
        <div class="text-xs text-slate-500">累計プレイ</div>
        <div class="number-display text-2xl mt-1">{{ totalPlays }}</div>
      </div>
      <div class="glass-card p-4 text-center">
        <div class="text-xs text-slate-500">平均スコア</div>
        <div class="number-display text-2xl mt-1 text-blue-700">{{ averageScore }}</div>
      </div>
      <div class="glass-card p-4 text-center">
        <div class="text-xs text-slate-500">最高記録</div>
        <div class="number-display text-2xl mt-1 brand-text">{{ bestRecord?.score ?? 0 }}</div>
      </div>
    </div>

    <!-- ジャンル別 -->
    <div v-if="genreStats.length" class="glass-card p-4 space-y-2">
      <h2 class="font-bold text-sm">ジャンル別</h2>
      <table class="w-full text-sm">
        <thead>
          <tr class="text-xs text-slate-500 border-b border-slate-200">
            <th class="text-left py-1">ジャンル</th>
            <th class="text-right py-1">回数</th>
            <th class="text-right py-1">平均</th>
            <th class="text-right py-1">最高</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in genreStats" :key="s.key" class="border-b border-slate-100 last:border-0">
            <td class="py-1.5">{{ s.emoji }} {{ s.label }}</td>
            <td class="text-right number-display">{{ s.count }}</td>
            <td class="text-right number-display text-slate-600">{{ s.avg }}</td>
            <td class="text-right number-display font-bold">{{ s.best }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 履歴 -->
    <div v-if="recentHistory.length" class="glass-card p-4">
      <h2 class="font-bold text-sm mb-2">最近のプレイ ({{ Math.min(20, totalPlays) }} / {{ totalPlays }})</h2>
      <ol class="space-y-1 text-sm">
        <li v-for="(r, i) in recentHistory" :key="i" class="flex items-center justify-between gap-2 border-b border-slate-100 last:border-0 pb-1">
          <span class="text-xs text-slate-500 w-16 shrink-0 number-display">{{ formatDate(r.date) }}</span>
          <span class="flex-1 truncate">
            {{ recordLabel(r).emoji }} {{ recordLabel(r).text }}
            <span :class="['ml-1 px-1.5 py-0.5 rounded text-xs', r.mode === 'daily' ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600']">
              {{ PLAY_MODE_LABELS[r.mode] ?? r.mode }}
            </span>
            <span v-if="r.difficulty" class="ml-1 text-xs text-slate-400">{{ r.difficulty }}</span>
          </span>
          <span class="number-display font-bold">{{ r.score }}</span>
        </li>
      </ol>
    </div>

    <div v-else class="text-center text-slate-500 py-8">
      まだプレイ履歴がありません。<router-link to="/" class="text-blue-600 hover:underline">ホームに戻ってプレイ</router-link>
    </div>

    <div v-if="totalPlays > 0" class="text-right">
      <button @click="reset" class="text-xs text-red-500 hover:underline">履歴をすべて削除</button>
    </div>
  </section>
</template>
