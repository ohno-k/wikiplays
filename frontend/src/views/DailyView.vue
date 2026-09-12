<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Genre, Scope } from '../types'
import { GENRES, SCOPE_LABELS } from '../types'
import {
  fetchDailyChallenge,
  fetchDailyChallengeById,
  fetchDailyLeaderboard,
  recordPlay,
  type DailyChallengeResponse,
  type DailyLeaderboardEntry,
} from '../api'
import { scoreEmoji } from '../scoring'
import GenrePicker from '../components/GenrePicker.vue'
import CharInputBoard from '../components/CharInputBoard.vue'
import XpResultCard from '../components/XpResultCard.vue'
import ResultShareCard from '../components/ResultShareCard.vue'
import { useAuth } from '../composables/useAuth'
import { useGameSession } from '../composables/useGameSession'

const route = useRoute()
const router = useRouter()
const { isLoggedIn, isPremium, token, refresh: refreshAuth } = useAuth()

type View = 'pick' | 'ready' | 'play' | 'result'
const view = ref<View>('pick')

const selectedGenre = ref<Genre | null>(null)
const selectedScope = ref<Scope>('jp')
const archiveId = ref<number | null>(null)

const challenge = ref<DailyChallengeResponse | null>(null)
const loadingChallenge = ref(false)
const error = ref<string | null>(null)
const leaderboard = ref<DailyLeaderboardEntry[]>([])

const game = useGameSession({ token })
const { view: session, question, loading, busy, error: gameError, finished, canReveal, timerKey } = game

const totalScore = computed(() => session.value?.totalScore ?? 0)
const maxScore = computed(() => session.value?.maxScore ?? 5000)
const results = computed(() => session.value?.summary?.results ?? [])

const challengeLabel = computed(() => {
  const c = challenge.value
  if (!c) return ''
  if (!c.genre) return '🎲 総合'
  const meta = GENRES.find(g => g.id === c.genre)
  const scope = c.scope ? SCOPE_LABELS[c.scope] : null
  return `${meta?.emoji ?? ''} ${scope ? scope.name + 'の' : ''}${meta?.name ?? c.genre}`
})

/** ジャンルを選ぶ → その日のチャレンジ概要を取得し、既プレイなら結果へ。 */
async function pickChallenge(g: Genre | null, s: Scope) {
  if (!isLoggedIn.value) return
  selectedGenre.value = g
  selectedScope.value = s
  archiveId.value = null
  await loadChallenge(() => fetchDailyChallenge(g, g ? s : null, token.value))
}

async function loadArchive(id: number) {
  archiveId.value = id
  await loadChallenge(() => fetchDailyChallengeById(id, token.value))
}

async function loadChallenge(fetcher: () => Promise<DailyChallengeResponse>) {
  loadingChallenge.value = true
  error.value = null
  game.reset()
  try {
    challenge.value = await fetcher()
    if (challenge.value.myScore != null) {
      view.value = 'result'
      loadLeaderboard()
    } else {
      view.value = 'ready'
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '読み込み失敗'
    view.value = 'pick'
  } finally {
    loadingChallenge.value = false
  }
}

async function startChallenge() {
  const c = challenge.value
  if (!c || c.myScore != null) return
  view.value = 'play'
  const ok = await game.start({
    mode: 'daily',
    dailyChallengeId: archiveId.value ?? c.id,
    genre: archiveId.value ? null : selectedGenre.value,
    scope: archiveId.value ? null : (selectedGenre.value ? selectedScope.value : null),
  })
  if (!ok) {
    error.value = gameError.value
    view.value = 'ready'
  }
}

function backToPick() {
  game.reset()
  challenge.value = null
  archiveId.value = null
  error.value = null
  view.value = 'pick'
  router.replace({ query: {} })
}

async function loadLeaderboard() {
  if (!challenge.value) return
  try {
    leaderboard.value = await fetchDailyLeaderboard(challenge.value.id)
  } catch {
    leaderboard.value = []
  }
}

watch(finished, (v) => {
  if (!v || !session.value || !challenge.value) return
  view.value = 'result'
  recordPlay({
    mode: 'daily',
    genre: challenge.value.genre,
    scope: challenge.value.scope,
    score: totalScore.value,
    maxScore: maxScore.value,
  })
  if (session.value.summary?.xp) refreshAuth()
  loadLeaderboard()
})

const shareText = computed(() => {
  if (!challenge.value) return ''
  const emojis = results.value.map(r => scoreEmoji(r.score, 1000)).join('')
  return `Wikiplays デイリー ${challenge.value.date} ${challengeLabel.value}\n${totalScore.value}/${maxScore.value}\n${emojis}\nhttps://wikiplays.me/daily`
})

onMounted(() => {
  const a = route.query.archiveId
  if (a && typeof a === 'string' && !isNaN(parseInt(a, 10)) && isLoggedIn.value) {
    loadArchive(parseInt(a, 10))
  }
})
</script>

<template>
  <section class="space-y-5 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-5 space-y-2">
      <div class="text-xs font-mono text-slate-500">DAILY CHALLENGE</div>
      <h1 class="text-2xl font-bold tracking-tight">今日の 5 問チャレンジ</h1>
      <p class="text-sm text-slate-600">
        全プレイヤーが同じ問題に挑戦します。総合とジャンル別があり、それぞれ 1 日 1 回。
        フリープランの回数制限にはカウントされません。
      </p>
    </div>

    <!-- 未ログイン: ログイン誘導 -->
    <div v-if="!isLoggedIn" class="glass-card p-6 text-center space-y-4">
      <div class="text-5xl">🔒</div>
      <div class="text-lg font-bold">デイリーチャレンジはアカウント登録が必要です</div>
      <p class="text-sm text-slate-600 leading-relaxed">
        全プレイヤー共通の問題のため、SNS 等での回答共有を防ぐ目的でログインを必須にしています。<br />
        無料登録で今日のチャレンジに参加できます。
      </p>
      <div class="flex flex-col sm:flex-row gap-2 justify-center pt-2">
        <router-link to="/login"
          class="px-5 py-2 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded-lg font-bold shadow-md hover:shadow-lg transition">
          ログイン
        </router-link>
        <router-link to="/register"
          class="px-5 py-2 bg-white border border-amber-400 text-amber-700 rounded-lg font-bold hover:bg-amber-50 transition">
          無料で新規登録
        </router-link>
      </div>
    </div>

    <!-- ジャンル選択 -->
    <div v-else-if="view === 'pick'" class="space-y-4">
      <div v-if="loadingChallenge" class="text-center text-slate-500 py-6">読み込み中…</div>
      <template v-else>
        <div v-if="error" class="text-red-600 text-sm">{{ error }}</div>
        <GenrePicker mode-name="デイリー" theme-gradient="from-amber-500 to-rose-500" @select="pickChallenge" />
        <div class="text-center">
          <router-link to="/daily-archive" class="text-xs text-amber-700 hover:underline">
            ⭐ 過去のデイリーに挑戦 (プレミアム)
          </router-link>
        </div>
      </template>
    </div>

    <!-- 開始画面 -->
    <div v-else-if="view === 'ready' && challenge" class="space-y-4">
      <div class="glass-card p-5 space-y-3">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-xs font-mono text-slate-500">{{ challenge.date }}{{ archiveId ? ' (アーカイブ)' : '' }}</div>
            <div class="text-xl font-bold">{{ challengeLabel }}</div>
          </div>
          <button @click="backToPick" class="text-xs text-blue-600 hover:underline">別のジャンルにする</button>
        </div>
        <div class="grid grid-cols-3 gap-2 text-center text-sm">
          <div class="bg-slate-50 rounded p-2"><div class="text-xs text-slate-500">問題数</div><div class="font-bold">{{ challenge.questionCount }}</div></div>
          <div class="bg-slate-50 rounded p-2"><div class="text-xs text-slate-500">挑戦者</div><div class="font-bold">{{ challenge.playerCount }} 人</div></div>
          <div class="bg-slate-50 rounded p-2"><div class="text-xs text-slate-500">トップ</div><div class="font-bold">{{ challenge.topScore }}</div></div>
        </div>
        <p class="text-xs text-slate-500">
          10 秒ごとに段落が開示されます。最初の 1 文字を選ぶとタイマーが止まり、ミスは 1 回だけ許されます。
          このチャレンジは 1 回しか挑戦できません。
        </p>
        <button @click="startChallenge" :disabled="loading"
          class="w-full px-4 py-3 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded-lg font-bold shadow-md hover:shadow-lg transition disabled:opacity-60">
          {{ loading ? '準備中…' : '挑戦を始める' }}
        </button>
        <div v-if="error" class="text-red-600 text-sm">{{ error }}</div>
      </div>
    </div>

    <!-- プレイ画面 -->
    <div v-else-if="view === 'play'">
      <div v-if="loading" class="text-slate-500">問題を準備中…</div>
      <template v-else-if="session && question">
        <div class="text-xs font-mono text-slate-500 mb-2">{{ challengeLabel }} / 累計 {{ session.totalScore }}</div>
        <div v-if="gameError" class="text-xs text-red-600 mb-2">{{ gameError }}</div>
        <CharInputBoard
          :question="question"
          :index="session.index"
          :total="session.totalQuestions"
          :interval-ms="session.intervalMs"
          :timer-key="timerKey"
          :busy="busy"
          :can-reveal="canReveal"
          accent="amber"
          :allow-manual-reveal="false"
          @answer="game.answer"
          @reveal="game.reveal"
          @give-up="game.giveUp"
          @next="game.next" />
      </template>
    </div>

    <!-- 結果画面 -->
    <div v-else-if="view === 'result' && challenge" class="space-y-4">
      <div class="glass-card p-5 text-center">
        <div class="text-xs font-mono text-slate-500">{{ challenge.date }} {{ challengeLabel }}</div>
        <div class="text-sm text-slate-500 mt-1">{{ session ? 'あなたのスコア' : 'このチャレンジのあなたのスコア' }}</div>
        <div class="text-5xl font-bold brand-text mt-2">{{ session ? totalScore : (challenge.myScore ?? 0) }}</div>
        <div class="text-xs text-slate-400">/ {{ maxScore }}</div>
        <div v-if="!session" class="text-xs text-slate-500 mt-2">
          ⏰ このチャレンジは挑戦済みです。他のジャンルにも挑戦できます。
        </div>
      </div>

      <XpResultCard v-if="session?.summary?.xp" :xp="session.summary.xp" />

      <ResultShareCard
        v-if="results.length > 0"
        :title="`デイリーチャレンジ ${challenge.date}`"
        :subtitle="challengeLabel"
        :total-score="totalScore"
        :max-score="maxScore"
        :results="results"
        theme-gradient="from-amber-500 via-orange-500 to-rose-500"
        :share-text="shareText" />

      <div v-if="results.length > 0" class="bg-white border border-slate-200 rounded p-4 space-y-2">
        <div v-for="(r, i) in results" :key="i" class="flex justify-between text-sm gap-2">
          <span>{{ i + 1 }}. {{ r.title }}</span>
          <span class="text-slate-600 shrink-0">{{ r.correct ? `${r.revealedCount} 段落で正解` : '不正解' }} → {{ r.score }}点</span>
        </div>
      </div>

      <div v-if="leaderboard.length" class="glass-card p-4">
        <div class="text-sm font-bold mb-2">ランキング (TOP {{ leaderboard.length }})</div>
        <ol class="space-y-1 text-sm">
          <li v-for="e in leaderboard" :key="e.rank" class="flex justify-between">
            <span><span class="font-mono text-slate-500">#{{ e.rank }}</span> {{ e.displayName }}</span>
            <span class="font-bold">{{ e.score }}</span>
          </li>
        </ol>
      </div>

      <div class="flex flex-wrap gap-2">
        <button @click="backToPick" class="px-4 py-2 bg-amber-500 text-white rounded font-bold hover:bg-amber-600">
          別のジャンルのデイリーに挑戦
        </button>
        <router-link v-if="isPremium" to="/daily-archive" class="px-4 py-2 bg-slate-200 rounded hover:bg-slate-300">
          過去のデイリー
        </router-link>
      </div>
    </div>
  </section>
</template>
