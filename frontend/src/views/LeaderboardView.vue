<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { fetchLeaderboard, type LeaderboardRow } from '../api'
import { GENRES, type Genre, type Scope } from '../types'

type Period = 'today' | 'week' | 'month' | 'all'

const period = ref<Period>('all')
const genre = ref<Genre | ''>('')
const scope = ref<Scope | ''>('')
const mode = ref<'a' | 'daily' | ''>('')

const rows = ref<LeaderboardRow[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const PERIODS: { id: Period; name: string }[] = [
  { id: 'today', name: '今日' },
  { id: 'week',  name: '今週' },
  { id: 'month', name: '今月' },
  { id: 'all',   name: '全期間' },
]

async function load() {
  loading.value = true
  error.value = null
  try {
    rows.value = await fetchLeaderboard({
      period: period.value,
      mode: mode.value || undefined,
      genre: genre.value || null,
      scope: scope.value || null,
      limit: 50,
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

const topRanks = computed(() => rows.value.slice(0, 3))
const restRanks = computed(() => rows.value.slice(3))

watch([period, mode, genre, scope], load)
onMounted(load)
</script>

<template>
  <section class="space-y-5 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-5 space-y-2">
      <div class="text-xs font-mono text-slate-500">LEADERBOARD</div>
      <h1 class="text-2xl font-bold tracking-tight">🏆 ランキング</h1>
      <p class="text-sm text-slate-600">
        ログインユーザーのスコアを集計しています。アカウント登録するとランキングに参加できます。
      </p>
    </div>

    <!-- フィルタ -->
    <div class="glass-card p-4 space-y-3">
      <div>
        <div class="text-xs font-bold text-slate-600 mb-1">期間</div>
        <div class="flex flex-wrap gap-1">
          <button v-for="p in PERIODS" :key="p.id"
            @click="period = p.id"
            :class="[
              'px-3 py-1 rounded-full text-xs transition',
              period === p.id ? 'bg-blue-600 text-white' : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
            ]">
            {{ p.name }}
          </button>
        </div>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <div>
          <div class="text-xs font-bold text-slate-600 mb-1">モード</div>
          <select v-model="mode" class="w-full border border-slate-300 rounded px-2 py-1 text-sm">
            <option value="">すべて</option>
            <option value="a">A モード</option>
            <option value="daily">デイリー</option>
          </select>
        </div>
        <div>
          <div class="text-xs font-bold text-slate-600 mb-1">ジャンル</div>
          <select v-model="genre" class="w-full border border-slate-300 rounded px-2 py-1 text-sm">
            <option value="">すべて</option>
            <option v-for="g in GENRES" :key="g.id" :value="g.id">{{ g.emoji }} {{ g.name }}</option>
          </select>
        </div>
        <div>
          <div class="text-xs font-bold text-slate-600 mb-1">スコープ</div>
          <select v-model="scope" class="w-full border border-slate-300 rounded px-2 py-1 text-sm">
            <option value="">すべて</option>
            <option value="jp">🇯🇵 日本</option>
            <option value="world">🌍 世界</option>
          </select>
        </div>
      </div>
    </div>

    <!-- ランキング -->
    <div v-if="loading" class="text-slate-500 text-center py-8">読み込み中…</div>
    <div v-else-if="error" class="text-red-600">{{ error }}</div>

    <div v-else-if="rows.length === 0" class="text-center text-slate-500 py-8">
      該当する記録がまだありません
    </div>

    <div v-else class="space-y-3">
      <!-- TOP3 -->
      <div v-if="topRanks.length" class="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div v-for="(r, i) in topRanks" :key="r.rank"
          class="glass-card p-4 text-center relative overflow-hidden"
          :class="[
            i === 0 ? 'sm:order-2 ring-2 ring-amber-400' : '',
            i === 1 ? 'sm:order-1' : '',
            i === 2 ? 'sm:order-3' : '',
          ]">
          <div class="text-3xl">{{ ['🥇', '🥈', '🥉'][i] }}</div>
          <div class="text-base font-bold mt-1 truncate">{{ r.displayName }}</div>
          <div class="number-display text-2xl mt-1 text-blue-700">{{ r.totalScore.toLocaleString() }}</div>
          <div class="text-xs text-slate-500 mt-1">
            最高 {{ r.bestScore.toLocaleString() }} / {{ r.playCount }} プレイ
          </div>
        </div>
      </div>

      <!-- 4 位以下 -->
      <div v-if="restRanks.length" class="glass-card p-2">
        <table class="w-full text-sm">
          <thead>
            <tr class="text-xs text-slate-500 border-b border-slate-200">
              <th class="text-left py-1 px-2">順位</th>
              <th class="text-left py-1 px-2">プレイヤー</th>
              <th class="text-right py-1 px-2">累計</th>
              <th class="text-right py-1 px-2">最高</th>
              <th class="text-right py-1 px-2">回数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in restRanks" :key="r.rank" class="border-b border-slate-100 last:border-0">
              <td class="py-1.5 px-2 number-display text-slate-500">#{{ r.rank }}</td>
              <td class="py-1.5 px-2 truncate">{{ r.displayName }}</td>
              <td class="py-1.5 px-2 text-right number-display font-bold">{{ r.totalScore.toLocaleString() }}</td>
              <td class="py-1.5 px-2 text-right number-display">{{ r.bestScore.toLocaleString() }}</td>
              <td class="py-1.5 px-2 text-right number-display text-slate-500">{{ r.playCount }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
