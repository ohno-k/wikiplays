<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ModeLayout from './ModeLayout.vue'
import GenrePicker from '../../components/GenrePicker.vue'
import CurrentGenreBadge from '../../components/CurrentGenreBadge.vue'
import CharInputBoard from '../../components/CharInputBoard.vue'
import XpResultCard from '../../components/XpResultCard.vue'
import ResultShareCard from '../../components/ResultShareCard.vue'
import type { Genre, Scope } from '../../types'
import { scoreEmoji } from '../../scoring'
import { fetchCommunityGenres, fetchPlayQuota, recordPlay, type CommunityGenre, type PlayQuota } from '../../api'
import { useAuth } from '../../composables/useAuth'
import { useGameSession } from '../../composables/useGameSession'

const { token, isLoggedIn, refresh: refreshAuth } = useAuth()
const route = useRoute()
const router = useRouter()

type Difficulty = 'relaxed' | 'normal' | 'speed'
interface DifficultyMeta { id: Difficulty; name: string; emoji: string; description: string }
const DIFFICULTIES: DifficultyMeta[] = [
  { id: 'relaxed', name: 'のんびり', emoji: '🌿', description: '20 秒ごとに 1 段落' },
  { id: 'normal',  name: 'ふつう',   emoji: '⏱️', description: '10 秒ごとに 1 段落 (標準)' },
  { id: 'speed',   name: '早押し',   emoji: '⚡', description: '5 秒ごとに 1 段落 (上級者向け)' },
]
const selectedDifficulty = ref<Difficulty>('normal')

const selectedGenre = ref<Genre | null>(null)
const selectedScope = ref<Scope>('jp')
const selectedCommunityGenreId = ref<number | null>(null)
const selectedCommunityGenreName = ref<string>('')
const started = ref(false)
const communityGenres = ref<CommunityGenre[]>([])
const playQuota = ref<PlayQuota | null>(null)

const game = useGameSession({ token, storageKey: 'mode-a' })
const { view, question, loading, busy, error, quotaExceeded, finished, canReveal, timerKey } = game

const totalScore = computed(() => view.value?.totalScore ?? 0)
const maxScore = computed(() => view.value?.maxScore ?? 5000)
const results = computed(() => view.value?.summary?.results ?? [])

async function refreshQuota() {
  try {
    playQuota.value = await fetchPlayQuota(token.value)
  } catch {
    playQuota.value = null
  }
}

async function startSession() {
  started.value = true
  const ok = await game.start({
    mode: 'a',
    genre: selectedCommunityGenreId.value != null ? null : selectedGenre.value,
    scope: selectedCommunityGenreId.value != null ? null : selectedScope.value,
    communityGenreId: selectedCommunityGenreId.value,
    difficulty: selectedDifficulty.value,
  })
  if (!ok && quotaExceeded.value) {
    started.value = false
    refreshQuota()
  }
}

function onGenreSelected(g: Genre | null, s: Scope) {
  selectedGenre.value = g
  selectedScope.value = s
  selectedCommunityGenreId.value = null
  selectedCommunityGenreName.value = ''
  router.replace({ query: { ...route.query, community: undefined, quick: undefined } })
  startSession()
}

function onCommunityGenreSelected(cg: CommunityGenre) {
  selectedGenre.value = null
  selectedCommunityGenreId.value = cg.id
  selectedCommunityGenreName.value = cg.name
  router.replace({ query: { ...route.query, community: String(cg.id), quick: undefined } })
  startSession()
}

function restart() {
  startSession()
}

function changeGenre() {
  game.reset()
  started.value = false
  router.replace({ query: {} })
  refreshQuota()
}

/** ?community=ID / ?quick=1 で来た場合は自動開始。それ以外は進行中セッションの復帰を試みる。 */
async function tryStartFromQuery() {
  const cid = route.query.community
  if (cid && typeof cid === 'string') {
    const id = parseInt(cid, 10)
    if (!isNaN(id)) {
      try {
        if (communityGenres.value.length === 0) communityGenres.value = await fetchCommunityGenres()
        const found = communityGenres.value.find(g => g.id === id)
        if (found) { onCommunityGenreSelected(found); return }
      } catch {
        // 取得失敗時は通常のジャンル選択画面に
      }
    }
  }
  if (route.query.quick === '1') {
    onGenreSelected(null, 'jp')
  } else if (await game.resume()) {
    started.value = true
    selectedGenre.value = view.value?.genre ?? null
    selectedScope.value = view.value?.scope ?? 'jp'
    selectedDifficulty.value = view.value?.difficulty ?? 'normal'
    if (view.value?.communityGenreId != null) {
      selectedCommunityGenreId.value = view.value.communityGenreId
      try {
        if (communityGenres.value.length === 0) communityGenres.value = await fetchCommunityGenres()
        selectedCommunityGenreName.value = communityGenres.value.find(g => g.id === view.value!.communityGenreId)?.name ?? 'コミュニティ'
      } catch { selectedCommunityGenreName.value = 'コミュニティ' }
    }
  }
  fetchCommunityGenres().then(list => { communityGenres.value = list }).catch(() => {})
}

const shareText = computed(() => {
  const emojis = results.value.map(r => scoreEmoji(r.score, 1000)).join('')
  return `Wikiplays Aモード ${totalScore.value}/${maxScore.value}\n${emojis}\nhttps://wikiplays.me/mode/a`
})

/** セッション終了時: ローカル履歴に残し、XP 表示のためにユーザー情報を更新。 */
watch(finished, (v) => {
  if (!v || !view.value) return
  recordPlay({
    mode: 'a',
    genre: selectedGenre.value,
    scope: selectedGenre.value ? selectedScope.value : null,
    communityGenreId: selectedCommunityGenreId.value ?? undefined,
    communityGenreName: selectedCommunityGenreName.value || undefined,
    score: totalScore.value,
    maxScore: maxScore.value,
    difficulty: selectedDifficulty.value,
  })
  if (view.value.summary?.xp) refreshAuth()
  refreshQuota()
})

onMounted(() => { refreshQuota(); tryStartFromQuery() })
</script>

<template>
  <ModeLayout mode-name="じわじわ開示" short-name="A モード" emoji="⏱️" theme="blue" gradient="from-sky-500 to-indigo-600">
    <!-- Quota 表示 (Free のみ) -->
    <div v-if="!started && playQuota && !playQuota.unlimited"
      class="glass-card p-3 flex items-center justify-between"
      :class="playQuota.remaining === 0 ? 'border-red-300' : ''">
      <div class="text-sm">
        <div class="text-xs font-mono text-slate-500">FREE プラン</div>
        <div class="font-bold">今日のプレイ可能数: {{ playQuota.remaining }} / {{ playQuota.limit }}</div>
      </div>
      <router-link v-if="playQuota.remaining < 3" to="/account"
        class="text-xs px-3 py-1 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded-full font-bold hover:opacity-90">
        ⭐ アップグレード
      </router-link>
    </div>

    <!-- 制限到達時の警告 -->
    <div v-if="!started && ((playQuota && !playQuota.unlimited && playQuota.remaining === 0) || quotaExceeded)"
      class="glass-card p-5 text-center space-y-2 ring-1 ring-red-200">
      <div class="text-3xl">🛑</div>
      <div class="font-bold">今日のプレイ上限に達しました</div>
      <div class="text-sm text-slate-600">
        フリープランは通常モード合計で 1 日 5 セッションまでです。プレミアムにアップグレードすると無制限に遊べます。
        デイリーチャレンジは上限に関係なく挑戦できます。
      </div>
      <div class="flex flex-wrap gap-2 justify-center">
        <router-link to="/daily" class="inline-block px-4 py-2 bg-amber-500 text-white rounded font-bold hover:bg-amber-600">
          ⭐ デイリーに挑戦
        </router-link>
        <router-link v-if="!isLoggedIn" to="/login" class="inline-block px-4 py-2 bg-blue-600 text-white rounded font-bold hover:bg-blue-700">
          ログイン / 登録
        </router-link>
        <router-link v-else to="/account"
          class="inline-block px-4 py-2 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded font-bold hover:opacity-90">
          ⭐ プレミアムにアップグレード
        </router-link>
      </div>
    </div>

    <!-- ジャンル選択画面 -->
    <template v-if="!started && !quotaExceeded && (!playQuota || playQuota.unlimited || playQuota.remaining > 0)">
      <div class="glass-card p-3 flex flex-wrap items-center gap-2">
        <span class="text-xs font-mono text-slate-500 mr-1">難易度:</span>
        <button v-for="d in DIFFICULTIES" :key="d.id"
          @click="selectedDifficulty = d.id"
          :title="d.description"
          :class="[
            'px-3 py-1 rounded-md text-xs flex items-center gap-1 transition',
            selectedDifficulty === d.id
              ? 'theme-bg-soft theme-text font-bold ring-1 theme-border'
              : 'bg-slate-100 hover:bg-slate-200 text-slate-600',
          ]">
          <span>{{ d.emoji }}</span>
          <span>{{ d.name }}</span>
        </button>
        <span class="text-xs text-slate-400 ml-auto">{{ DIFFICULTIES.find(d => d.id === selectedDifficulty)?.description }}</span>
      </div>
      <GenrePicker
        mode-name="A モード"
        theme-gradient="from-sky-500 to-indigo-600"
        :community-genres="communityGenres"
        @select="onGenreSelected"
        @select-community="onCommunityGenreSelected" />
    </template>

    <template v-else-if="started">
      <CurrentGenreBadge
        :genre="selectedGenre"
        :scope="selectedScope"
        :community-genre-name="selectedCommunityGenreName || undefined"
        @change="changeGenre" />

      <p class="text-sm text-slate-600">
        記事の末尾から段落が自動で追加されます。答えは 4 択で 1 文字ずつ。
        <span class="font-bold text-emerald-700">最初の 1 文字を選ぶとタイマーが止まり</span>、その時点の段落数でスコアが決まります。
        ミスは <span class="font-bold text-rose-600">1 回だけ</span> 許されます (その問題のスコアは半減)。
      </p>

      <!-- 終了画面 -->
      <div v-if="finished && view" class="space-y-4">
        <div class="bg-blue-50 border border-blue-200 rounded p-4">
          <div class="text-sm text-blue-900">最終スコア</div>
          <div class="text-3xl font-bold text-blue-700">{{ totalScore }} / {{ maxScore }}</div>
        </div>
        <XpResultCard v-if="view.summary?.xp" :xp="view.summary.xp" />
        <div v-else-if="!isLoggedIn" class="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded p-3">
          ログインすると XP とレベルが貯まり、ランキングに参加できます。
          <router-link to="/login" class="text-blue-600 hover:underline ml-1">ログイン / 登録</router-link>
        </div>
        <div class="bg-white border border-slate-200 rounded p-4 space-y-2">
          <div v-for="(r, i) in results" :key="i" class="flex justify-between text-sm gap-2">
            <span>{{ i + 1 }}. {{ r.title }}</span>
            <span class="text-slate-600 text-right shrink-0">
              {{ r.correct ? `${r.revealedCount} 段落で正解` : `${r.correctChars}/${r.totalInputChars} 文字` }}
              → {{ r.score }}点
            </span>
          </div>
        </div>
        <ResultShareCard
          title="A モード"
          :subtitle="selectedCommunityGenreName || '通常プレイ'"
          :total-score="totalScore"
          :max-score="maxScore"
          :results="results"
          theme-gradient="from-sky-500 via-indigo-500 to-purple-600"
          :share-text="shareText" />
        <button @click="restart" class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">もう一度</button>
      </div>

      <div v-else>
        <div v-if="loading" class="text-slate-500">問題を準備中…</div>
        <div v-else-if="error && !question" class="space-y-3">
          <div class="text-red-600">エラー: {{ error }}</div>
          <button @click="startSession" class="px-3 py-1 bg-slate-200 rounded hover:bg-slate-300 text-xs">もう一度試す</button>
        </div>
        <template v-else-if="view && question">
          <div v-if="error" class="text-xs text-red-600 mb-2">{{ error }}</div>
          <CharInputBoard
            :question="question"
            :index="view.index"
            :total="view.totalQuestions"
            :interval-ms="view.intervalMs"
            :timer-key="timerKey"
            :busy="busy"
            :can-reveal="canReveal"
            accent="blue"
            :allow-manual-reveal="true"
            @answer="game.answer"
            @reveal="game.reveal"
            @give-up="game.giveUp"
            @next="game.next" />
        </template>
      </div>
    </template>
  </ModeLayout>
</template>
