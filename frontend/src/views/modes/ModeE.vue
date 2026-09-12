<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import ModeLayout from './ModeLayout.vue'
import GenrePicker from '../../components/GenrePicker.vue'
import CurrentGenreBadge from '../../components/CurrentGenreBadge.vue'
import type { ArticleData, Genre, Scope } from '../../types'
import { scoreEmoji } from '../../scoring'
import { useArticleQueue } from '../../composables/useArticleQueue'
import { useSessionRecorder } from '../../composables/useSessionRecorder'
import { useAuth } from '../../composables/useAuth'
import XpResultCard from '../../components/XpResultCard.vue'
import ResultShareCard from '../../components/ResultShareCard.vue'

const TOTAL_QUESTIONS = 5
const SUB_COUNT = 4
const MAX_PER_SUB = 250
const MAX_PER_Q = MAX_PER_SUB * SUB_COUNT // 1000 点
const MAX_TOTAL = MAX_PER_Q * TOTAL_QUESTIONS

/** 著名記事の閾値 (これ未満は予測しづらいのでスキップ) */
const FAMOUS_LANGLINKS_THRESHOLD = 5

interface SubQuestion {
  key: 'length' | 'langlinks' | 'sections' | 'images'
  label: string
  hint: string
  truth: number
  unit: string
  defaultGuess: number
}

const loading = ref(false)
const error = ref<string | null>(null)
const article = ref<ArticleData | null>(null)
const subs = ref<SubQuestion[]>([])
const guesses = ref<number[]>([0, 0, 0, 0])
const answered = ref(false)
const currentQ = ref(1)
const results = ref<{ title: string; score: number; detail: { truth: number; guess: number; score: number; label: string }[] }[]>([])

const finished = computed(() => results.value.length >= TOTAL_QUESTIONS)
const totalScore = computed(() => results.value.reduce((s, r) => s + r.score, 0))

/** 年・世紀含むカテゴリを除いた表示用カテゴリ (上位3個まで)。 */
const hintCategories = computed(() => {
  if (!article.value) return [] as string[]
  return article.value.categories
    .filter(c => !/\d/.test(c) && !/世紀|年代|存命人物/.test(c))
    .slice(0, 3)
})

function buildSubs(a: ArticleData): SubQuestion[] {
  return [
    { key: 'length',    label: '記事の長さ (バイト数)', hint: '〜200000', truth: a.articleLength,   unit: 'バイト', defaultGuess: 20000 },
    { key: 'langlinks', label: '他言語版の数',     hint: '〜200',    truth: a.languageLinkCount,   unit: '言語',   defaultGuess: 30 },
    { key: 'sections',  label: '目次の節数',       hint: '〜30',     truth: a.sections.length,     unit: '節',     defaultGuess: 8 },
    { key: 'images',    label: '記事内の画像枚数', hint: '〜20',     truth: a.imageUrls.length,    unit: '枚',     defaultGuess: 3 },
  ]
}

function subScore(truth: number, guess: number): number {
  if (truth === 0 && guess === 0) return MAX_PER_SUB
  const denom = Math.max(truth, guess, 1)
  const ratio = Math.abs(truth - guess) / denom
  return Math.round(MAX_PER_SUB * Math.max(0, 1 - ratio))
}

const selectedGenre = ref<Genre | null>(null)
const selectedScope = ref<Scope>('jp')
const started = ref(false)

const { token, isLoggedIn } = useAuth()
const recorder = useSessionRecorder('e')

const queue = useArticleQueue<ArticleData>({
  genre: selectedGenre,
  scope: selectedScope,
  token,
  prepare: (a) => {
    if (a.languageLinkCount < FAMOUS_LANGLINKS_THRESHOLD) return null
    return a
  },
  maxAttempts: 15
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
  answered.value = false
  try {
    const a = await queue.pull()
    article.value = a
    const newSubs = buildSubs(a)
    subs.value = newSubs
    guesses.value = newSubs.map(s => s.defaultGuess)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

function submit() {
  if (!article.value) return
  const detail = subs.value.map((sub, i) => ({
    truth: sub.truth,
    guess: guesses.value[i],
    score: subScore(sub.truth, guesses.value[i]),
    label: sub.label,
  }))
  const total = detail.reduce((s, d) => s + d.score, 0)
  results.value.push({ title: article.value.title, score: total, detail })
  answered.value = true
}

function nextQuestion() {
  if (currentQ.value < TOTAL_QUESTIONS) { currentQ.value++; loadNext() }
}
function restart() { results.value = []; currentQ.value = 1; recorder.reset(); queue.reset(); loadNext() }

const shareText = computed(() => {
  const emojis = results.value.map(r => scoreEmoji(r.score, MAX_PER_Q)).join('')
  return `Wikiplays Eモード ${totalScore.value}/${MAX_PER_Q * TOTAL_QUESTIONS}\n${emojis}`
})
watch(finished, (v) => {
  if (!v) return
  recorder.record({ genre: selectedGenre.value, scope: selectedScope.value, score: totalScore.value, maxScore: MAX_TOTAL })
})


/** 誤差倍率を表示するためのテキスト ("実際の2.5倍" 等)。 */
function diffLabel(truth: number, guess: number): string {
  if (truth === 0 && guess === 0) return '完全一致'
  if (truth === 0) return `予測が ${guess} (実際は 0)`
  const ratio = guess / truth
  if (ratio > 1.2) return `予測は実際の ${ratio.toFixed(1)} 倍`
  if (ratio < 0.83) return `予測は実際の 1/${(1 / ratio).toFixed(1)}`
  return '近い'
}

</script>

<template>
  <ModeLayout mode-name="数字あて" short-name="E モード" emoji="🔄" theme="rose" gradient="from-rose-500 to-pink-600">
    <GenrePicker
      v-if="!started"
      mode-name="E モード"
      theme-gradient="from-rose-500 to-pink-600"
      @select="onGenreSelected" />

    <template v-else>
      <CurrentGenreBadge :genre="selectedGenre" :scope="selectedScope" @change="changeGenre" />

      <p class="text-sm text-slate-600">
        記事のタイトルとカテゴリだけを見て、その記事の規模 (バイト数・他言語版の数・節数・画像数) を予測してください。
      </p>

    <div v-if="finished" class="space-y-4">
      <div class="bg-blue-50 border border-blue-200 rounded p-4">
        <div class="text-sm text-blue-900">最終スコア</div>
        <div class="text-3xl font-bold text-blue-700">{{ totalScore }} / {{ MAX_PER_Q * TOTAL_QUESTIONS }}</div>
      </div>
      <div class="bg-white border border-slate-200 rounded p-4 space-y-2">
        <div v-for="(r, i) in results" :key="i" class="text-sm border-b border-slate-100 last:border-0 pb-2 last:pb-0">
          <div class="font-bold">{{ i + 1 }}. {{ r.title }} → {{ r.score }}点</div>
          <div class="text-xs text-slate-600 grid grid-cols-2 gap-1 mt-1">
            <span v-for="(d, j) in r.detail" :key="j">{{ d.label }}: {{ d.guess }} / 正解 {{ d.truth }} ({{ d.score }}点)</span>
          </div>
        </div>
      </div>
      <XpResultCard v-if="recorder.xpResult.value" :xp="recorder.xpResult.value" />
      <div v-else-if="!isLoggedIn" class="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded p-3">
        ログインすると XP とレベルが貯まり、ランキングに参加できます。
        <router-link to="/login" class="text-blue-600 hover:underline ml-1">ログイン / 登録</router-link>
      </div>
      <ResultShareCard
        title="E モード"
        :total-score="totalScore"
        :max-score="MAX_TOTAL"
        :results="results"
        theme-gradient="from-rose-500 via-pink-500 to-fuchsia-600"
        :share-text="shareText" />
      <button @click="restart" class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">もう一度</button>
    </div>

    <div v-else>
      <div class="text-sm text-slate-500 mb-2">第 {{ currentQ }} 問 / {{ TOTAL_QUESTIONS }} 問</div>
      <div v-if="loading" class="text-slate-500">読み込み中…</div>
      <div v-else-if="error" class="space-y-2">
        <div class="text-red-600">エラー: {{ error }}</div>
        <div v-if="error.includes('上限')" class="text-sm text-slate-600">
          フリープランは通常モード合計で 1 日 5 セッションまでです。
          <router-link to="/daily" class="text-blue-600 hover:underline">デイリーチャレンジ</router-link> は上限に関係なく挑戦できます。
          <router-link to="/account" class="text-blue-600 hover:underline ml-1">⭐ プレミアムで無制限に</router-link>
        </div>
        <button v-else @click="loadNext" class="px-3 py-1 bg-slate-200 rounded hover:bg-slate-300 text-xs">もう一度試す</button>
      </div>

      <div v-else-if="article" class="space-y-4">
        <div class="bg-white border border-slate-200 rounded p-4">
          <div class="text-xs text-slate-500">この記事の特徴を予測してください</div>
          <div class="text-2xl font-bold mt-1">{{ article.title }}</div>
          <div v-if="hintCategories.length" class="mt-2 flex flex-wrap gap-1">
            <span v-for="c in hintCategories" :key="c" class="px-2 py-0.5 text-xs bg-slate-100 rounded">{{ c }}</span>
          </div>
        </div>

        <div v-if="!answered" class="space-y-3">
          <div v-for="(sub, i) in subs" :key="sub.key" class="bg-white border border-slate-200 rounded p-3 space-y-2">
            <div class="text-sm">
              <div class="font-bold">{{ sub.label }}</div>
              <div class="text-xs text-slate-500">目安レンジ: {{ sub.hint }}</div>
            </div>
            <div class="flex items-center gap-3">
              <input v-model.number="guesses[i]" type="number" min="0"
                class="w-32 border border-slate-300 rounded px-2 py-1" />
              <span class="text-xs text-slate-500">{{ sub.unit }}</span>
            </div>
          </div>
          <button @click="submit" class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
            予測を確定
          </button>
        </div>

        <div v-else class="space-y-3">
          <div class="bg-slate-100 rounded p-3 space-y-2">
            <div class="text-sm font-bold">合計 {{ results[results.length - 1].score }} / {{ MAX_PER_Q }} 点</div>
            <div v-for="(d, i) in results[results.length - 1].detail" :key="i" class="text-xs">
              {{ d.label }}: あなた <span class="font-bold">{{ d.guess }}</span> / 正解 <span class="font-bold">{{ d.truth }}</span>
              ({{ diffLabel(d.truth, d.guess) }}) → {{ d.score }}点
            </div>
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
