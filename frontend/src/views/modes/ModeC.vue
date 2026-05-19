<script setup lang="ts">
import { ref, computed } from 'vue'
import ModeLayout from './ModeLayout.vue'
import GenrePicker from '../../components/GenrePicker.vue'
import CurrentGenreBadge from '../../components/CurrentGenreBadge.vue'
import type { ArticleData, YearKind, Genre, Scope } from '../../types'
import { YEAR_KIND_LABELS } from '../../types'
import { scoreEmoji } from '../../scoring'
import { maskTitle, maskYears } from '../../masking'
import { useArticleQueue } from '../../composables/useArticleQueue'

const MIN_YEAR = -500
const MAX_YEAR = 2025
const ROUGH_STEP = 25                  // 粗いスライダーの刻み
const FINE_RANGE = 200                 // ズーム後の半径 (粗推測 ±200 年)
const MAX_SCORE = 1000
const DECAY_RANGE = 100                // 誤差 100 年で 0 点

const TOTAL_QUESTIONS = 5

type Phase = 'rough' | 'fine' | 'answered'

const loading = ref(false)
const error = ref<string | null>(null)
const article = ref<ArticleData | null>(null)
const phase = ref<Phase>('rough')

/** 答えるべき年の種類ラベル (例: 「生年」「設立年」)。 */
const yearKindLabel = computed(() => {
  const kind = article.value?.extractedYearKind as YearKind | null | undefined
  return kind && YEAR_KIND_LABELS[kind] ? YEAR_KIND_LABELS[kind] : '年代'
})
const roughGuess = ref(1000)          // フェーズ1で決定した値
const fineGuess = ref(1000)           // フェーズ2の最終回答
const currentQ = ref(1)
const results = ref<{ title: string; truth: number; rough: number; guess: number; score: number }[]>([])

const finished = computed(() => results.value.length >= TOTAL_QUESTIONS)
const totalScore = computed(() => results.value.reduce((s, r) => s + r.score, 0))

/** フェーズ2のスライダー範囲 (粗推測の周辺)。MIN/MAX_YEAR を超えないように clamp。 */
const fineMin = computed(() => Math.max(MIN_YEAR, roughGuess.value - FINE_RANGE))
const fineMax = computed(() => Math.min(MAX_YEAR, roughGuess.value + FINE_RANGE))

function maskedIntro(a: ArticleData): string {
  return maskYears(maskTitle(a.introExtract, a.title))
}

function safeCategories(a: ArticleData): string[] {
  return a.categories.filter(c => !/\d/.test(c) && !/世紀|年代|存命人物/.test(c))
}

const selectedGenre = ref<Genre | null>(null)
const selectedScope = ref<Scope>('jp')
const started = ref(false)

const queue = useArticleQueue<ArticleData>({
  genre: selectedGenre,
  scope: selectedScope,
  prepare: (a) => {
    if (a.extractedYear === null || a.extractedYearKind === null) return null
    if (a.introExtract.length < 100) return null
    return a
  },
  maxAttempts: 25
})

function onGenreSelected(g: Genre | null, s: Scope) {
  selectedGenre.value = g
  selectedScope.value = s
  results.value = []
  currentQ.value = 1
  queue.reset()
  started.value = true
  loadNext()
}

function changeGenre() {
  started.value = false
  results.value = []
  currentQ.value = 1
  queue.reset()
}

async function loadNext() {
  loading.value = true
  error.value = null
  phase.value = 'rough'
  roughGuess.value = 1000
  fineGuess.value = 1000
  try {
    article.value = await queue.pull()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

function confirmRough() {
  // 粗推測を中心に細かいスライダーへ
  fineGuess.value = roughGuess.value
  phase.value = 'fine'
}

function calcScore(truth: number, g: number): number {
  const diff = Math.abs(truth - g)
  return Math.round(MAX_SCORE * Math.max(0, 1 - diff / DECAY_RANGE))
}

function submit() {
  if (!article.value || article.value.extractedYear === null) return
  const truth = article.value.extractedYear
  const score = calcScore(truth, fineGuess.value)
  results.value.push({
    title: article.value.title,
    truth,
    rough: roughGuess.value,
    guess: fineGuess.value,
    score,
  })
  phase.value = 'answered'
}

function nextQuestion() {
  if (currentQ.value < TOTAL_QUESTIONS) {
    currentQ.value++
    loadNext()
  }
}

function restart() {
  results.value = []
  currentQ.value = 1
  queue.reset()
  loadNext()
}

const shareText = computed(() => {
  const emojis = results.value.map(r => scoreEmoji(r.score, MAX_SCORE)).join('')
  return `Wikiplays Cモード ${totalScore.value}/${MAX_SCORE * TOTAL_QUESTIONS}\n${emojis}`
})

function copyShare() {
  navigator.clipboard.writeText(shareText.value)
}

function yearLabel(y: number): string {
  return y < 0 ? `紀元前 ${-y} 年` : `${y} 年`
}

</script>

<template>
  <ModeLayout mode-name="座標推定型" short-name="C モード" emoji="🎯" theme="emerald" gradient="from-emerald-500 to-teal-600">
    <GenrePicker
      v-if="!started"
      mode-name="C モード"
      theme-gradient="from-emerald-500 to-teal-600"
      @select="onGenreSelected" />

    <template v-else>
      <CurrentGenreBadge :genre="selectedGenre" :scope="selectedScope" @change="changeGenre" />

      <p class="text-sm text-slate-600">
        ① まず粗いスライダーで世紀のあたりをつけ、② その周辺で細かく微調整します。
        具体的な年代は本文中で <span class="font-mono">■■■■</span> にマスクされています。
      </p>

    <!-- 何の年を答えるかを大きく明示 -->
    <div v-if="!loading && !error && article && !finished" class="glass-card p-4 flex items-center justify-between">
      <div>
        <div class="text-xs font-mono text-slate-500">この問題で答えるのは</div>
        <div class="text-2xl font-bold theme-text mt-1">{{ yearKindLabel }}</div>
      </div>
      <div class="text-3xl">📅</div>
    </div>

    <!-- セッション終了 -->
    <div v-if="finished" class="space-y-4">
      <div class="bg-blue-50 border border-blue-200 rounded p-4">
        <div class="text-sm text-blue-900">最終スコア</div>
        <div class="text-3xl font-bold text-blue-700">{{ totalScore }} / {{ MAX_SCORE * TOTAL_QUESTIONS }}</div>
      </div>
      <div class="bg-white border border-slate-200 rounded p-4 space-y-2">
        <div v-for="(r, i) in results" :key="i" class="text-sm">
          <div class="flex justify-between">
            <span>{{ i + 1 }}. {{ r.title }}</span>
            <span class="text-slate-600">{{ r.score }}点</span>
          </div>
          <div class="text-xs text-slate-500">
            正解 {{ yearLabel(r.truth) }} / 粗 {{ yearLabel(r.rough) }} → 最終 {{ yearLabel(r.guess) }}
          </div>
        </div>
      </div>
      <div class="flex gap-2">
        <button @click="restart" class="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">もう一度</button>
        <button @click="copyShare" class="px-4 py-2 bg-slate-200 rounded hover:bg-slate-300">結果をコピー</button>
      </div>
      <pre class="text-xs bg-slate-100 p-3 rounded whitespace-pre-wrap">{{ shareText }}</pre>
    </div>

    <!-- 問題中 -->
    <div v-else>
      <div class="text-sm text-slate-500 mb-2">第 {{ currentQ }} 問 / {{ TOTAL_QUESTIONS }} 問</div>

      <div v-if="loading" class="text-slate-500">読み込み中…</div>
      <div v-else-if="error" class="text-red-600">エラー: {{ error }}</div>

      <div v-else-if="article" class="space-y-4">
        <article
          class="no-copy bg-white border border-slate-200 rounded p-4 leading-relaxed whitespace-pre-wrap text-sm max-h-60 overflow-y-auto"
          @copy.prevent
          @cut.prevent
          @contextmenu.prevent
          @dragstart.prevent>
          {{ maskedIntro(article) }}
        </article>

        <div v-if="safeCategories(article).length" class="no-copy text-xs text-slate-500" @copy.prevent @cut.prevent @contextmenu.prevent @dragstart.prevent>
          <span class="font-bold">カテゴリ:</span>
          <span v-for="c in safeCategories(article)" :key="c" class="inline-block px-2 py-0.5 mx-1 bg-slate-100 rounded">{{ c }}</span>
        </div>

        <!-- フェーズ1: 粗いスライダー -->
        <div v-if="phase === 'rough'" class="bg-white border border-slate-200 rounded p-4 space-y-3">
          <div class="text-xs text-slate-500 font-bold">STEP 1: だいたいの{{ yearKindLabel }}を決める</div>
          <div class="text-center text-3xl font-bold">{{ yearLabel(roughGuess) }}</div>
          <input
            v-model.number="roughGuess"
            type="range"
            :min="MIN_YEAR"
            :max="MAX_YEAR"
            :step="ROUGH_STEP"
            class="w-full" />
          <div class="flex justify-between text-xs text-slate-500">
            <span>紀元前500年</span>
            <span>{{ ROUGH_STEP }}年刻み</span>
            <span>2025年</span>
          </div>
          <button @click="confirmRough" class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
            この時代で決定 → 細かく合わせる
          </button>
        </div>

        <!-- フェーズ2: 細かいスライダー (ズーム) -->
        <div v-else-if="phase === 'fine'" class="bg-white border border-blue-300 rounded p-4 space-y-3 ring-1 ring-blue-200">
          <div class="text-xs text-slate-500 font-bold flex justify-between">
            <span>STEP 2: {{ yearKindLabel }}を 1 年刻みで微調整</span>
            <span class="text-slate-400">(粗推測: {{ yearLabel(roughGuess) }})</span>
          </div>
          <div class="text-center text-3xl font-bold text-blue-700">{{ yearLabel(fineGuess) }}</div>
          <input
            v-model.number="fineGuess"
            type="range"
            :min="fineMin"
            :max="fineMax"
            step="1"
            class="w-full" />
          <div class="flex justify-between text-xs text-slate-500">
            <span>{{ yearLabel(fineMin) }}</span>
            <span>1年刻み</span>
            <span>{{ yearLabel(fineMax) }}</span>
          </div>
          <button @click="submit" class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
            この年で最終決定
          </button>
        </div>

        <!-- 結果 -->
        <div v-else class="space-y-3">
          <div class="bg-slate-100 rounded p-3">
            <div class="text-sm">答え: <span class="font-bold">{{ article.title }}</span></div>
            <div class="text-sm">正解の{{ yearKindLabel }}: <span class="font-bold">{{ yearLabel(article.extractedYear!) }}</span></div>
            <div class="text-sm">あなたの最終回答: {{ yearLabel(fineGuess) }} (誤差 {{ Math.abs(article.extractedYear! - fineGuess) }} 年)</div>
            <div class="text-xs text-slate-500">粗推測: {{ yearLabel(roughGuess) }}</div>
            <div class="text-lg font-bold text-blue-700 mt-1">スコア: {{ results[results.length - 1].score }}</div>
          </div>
          <a :href="article.pageUrl" target="_blank" rel="noopener" class="text-xs text-blue-600 hover:underline">
            Wikipedia で記事を開く
          </a>
          <button @click="nextQuestion" class="w-full px-4 py-2 bg-emerald-600 text-white rounded hover:bg-emerald-700">
            次の問題へ
          </button>
        </div>
      </div>
    </div>
    </template>
  </ModeLayout>
</template>
