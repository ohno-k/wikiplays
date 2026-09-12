<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import ModeLayout from './ModeLayout.vue'
import GenrePicker from '../../components/GenrePicker.vue'
import FameTierPicker from '../../components/FameTierPicker.vue'
import CurrentGenreBadge from '../../components/CurrentGenreBadge.vue'
import { fetchDecoys } from '../../api'
import type { ArticleData, Genre, Scope } from '../../types'
import { scoreEmoji } from '../../scoring'
import { maskTitle, firstSentence } from '../../masking'
import { useArticleQueue } from '../../composables/useArticleQueue'
import { useFameTier } from '../../composables/useFameTier'
import { useSessionRecorder } from '../../composables/useSessionRecorder'
import { useAuth } from '../../composables/useAuth'
import XpResultCard from '../../components/XpResultCard.vue'
import ResultShareCard from '../../components/ResultShareCard.vue'

interface ModeDData {
  article: ArticleData
  choices: string[]
}

const TOTAL_QUESTIONS = 5
const MAX_TOTAL = 1000 * TOTAL_QUESTIONS
const HINTS_MAX = 3
const SCORE_BY_HINTS = [1000, 800, 600, 400] // ヒント追加数→スコア

type HintKey = 'sections' | 'image' | 'infobox'
interface Hint {
  key: HintKey
  label: string
  available: boolean
}

const loading = ref(false)
const error = ref<string | null>(null)
const article = ref<ArticleData | null>(null)
const choices = ref<string[]>([])
const hintsOpened = ref<Set<HintKey>>(new Set())
const infoboxItem = ref<{ key: string; value: string } | null>(null)
const imageIndex = ref(0)
const answered = ref(false)
const selectedChoice = ref<string | null>(null)
const currentQ = ref(1)
const results = ref<{ title: string; chosen: string | null; score: number; hintsUsed: number; correct: boolean }[]>([])

const finished = computed(() => results.value.length >= TOTAL_QUESTIONS)
const totalScore = computed(() => results.value.reduce((s, r) => s + r.score, 0))

const hints = computed<Hint[]>(() => {
  if (!article.value) return []
  return [
    { key: 'sections', label: '目次',             available: article.value.sections.length > 0 },
    { key: 'image',    label: '画像',             available: article.value.imageUrls.length > 0 },
    { key: 'infobox',  label: 'インフォボックス', available: Object.keys(article.value.infobox).length > 0 },
  ]
})


function shuffle<T>(arr: T[]): T[] {
  const a = arr.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

const selectedGenre = ref<Genre | null>(null)
const selectedScope = ref<Scope>('jp')
const started = ref(false)
const { fameTier } = useFameTier()

const { token, isLoggedIn } = useAuth()
const recorder = useSessionRecorder('d')

const queue = useArticleQueue<ModeDData>({
  genre: selectedGenre,
  scope: selectedScope,
  fameTier,
  token,
  prepare: async (a) => {
    if (a.categories.length === 0) return null
    try {
      const decoys = await fetchDecoys(a.categories.slice(0, 3), a.title, 3)
      if (decoys.length < 3) return null
      return { article: a, choices: shuffle([a.title, ...decoys.slice(0, 3)]) }
    } catch {
      return null
    }
  },
  maxAttempts: 10
})

async function loadNext() {
  loading.value = true
  error.value = null
  answered.value = false
  selectedChoice.value = null
  hintsOpened.value = new Set()
  infoboxItem.value = null
  imageIndex.value = 0
  choices.value = []

  try {
    const d = await queue.pull()
    article.value = d.article
    choices.value = d.choices
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

function openHint(key: HintKey) {
  if (!article.value || hintsOpened.value.has(key)) return
  if (hintsOpened.value.size >= HINTS_MAX) return
  hintsOpened.value.add(key)
  if (key === 'infobox') {
    const keys = Object.keys(article.value.infobox)
    if (keys.length > 0) {
      const k = keys[Math.floor(Math.random() * keys.length)]
      infoboxItem.value = { key: k, value: article.value.infobox[k] }
    }
  }
  if (key === 'image') {
    imageIndex.value = Math.floor(Math.random() * article.value.imageUrls.length)
  }
}

function selectChoice(c: string) {
  if (answered.value) return
  selectedChoice.value = c
}

function submit() {
  if (!article.value || !selectedChoice.value) return
  const correct = selectedChoice.value === article.value.title
  const hintsUsed = hintsOpened.value.size
  const score = correct ? (SCORE_BY_HINTS[hintsUsed] ?? 400) : 0
  results.value.push({
    title: article.value.title,
    chosen: selectedChoice.value,
    score,
    hintsUsed,
    correct,
  })
  answered.value = true
}

function nextQuestion() {
  if (currentQ.value < TOTAL_QUESTIONS) { currentQ.value++; loadNext() }
}
function restart() { results.value = []; currentQ.value = 1; recorder.reset(); queue.reset(); loadNext() }

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

const shareText = computed(() => {
  const emojis = results.value.map(r => scoreEmoji(r.score, 1000)).join('')
  return `Wikiplays Dモード ${totalScore.value}/${1000 * TOTAL_QUESTIONS}\n${emojis}`
})
watch(finished, (v) => {
  if (!v) return
  recorder.record({ genre: selectedGenre.value, scope: selectedScope.value, score: totalScore.value, maxScore: MAX_TOTAL })
})


</script>

<template>
  <ModeLayout mode-name="4 択クイズ" short-name="D モード" emoji="🔍" theme="amber" gradient="from-amber-500 to-orange-600">
    <template v-if="!started">
      <FameTierPicker v-model="fameTier" />
      <GenrePicker
        mode-name="D モード"
        theme-gradient="from-amber-500 to-orange-600"
        @select="onGenreSelected" />
    </template>

    <template v-else>
      <CurrentGenreBadge :genre="selectedGenre" :scope="selectedScope" :fame-tier="fameTier" @change="changeGenre" />

      <p class="text-sm text-slate-600">
        4 つの選択肢から正解の記事を選んでください。ヒントを追加するたびに減点されます。
      </p>

    <div v-if="finished" class="space-y-4">
      <div class="bg-blue-50 border border-blue-200 rounded p-4">
        <div class="text-sm text-blue-900">最終スコア</div>
        <div class="text-3xl font-bold text-blue-700">{{ totalScore }} / {{ 1000 * TOTAL_QUESTIONS }}</div>
      </div>
      <div class="bg-white border border-slate-200 rounded p-4 space-y-2">
        <div v-for="(r, i) in results" :key="i" class="flex justify-between text-sm">
          <span>{{ i + 1 }}. {{ r.title }}</span>
          <span class="text-slate-600">{{ r.correct ? `ヒント ${r.hintsUsed} で正解` : '不正解' }} → {{ r.score }}点</span>
        </div>
      </div>
      <XpResultCard v-if="recorder.xpResult.value" :xp="recorder.xpResult.value" />
      <div v-else-if="!isLoggedIn" class="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded p-3">
        ログインすると XP とレベルが貯まり、ランキングに参加できます。
        <router-link to="/login" class="text-blue-600 hover:underline ml-1">ログイン / 登録</router-link>
      </div>
      <ResultShareCard
        title="D モード"
        :total-score="totalScore"
        :max-score="MAX_TOTAL"
        :results="results"
        theme-gradient="from-amber-500 via-orange-500 to-red-500"
        :share-text="shareText" />
      <button @click="restart" class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">もう一度</button>
    </div>

    <div v-else>
      <div class="text-sm text-slate-500 mb-2">第 {{ currentQ }} 問 / {{ TOTAL_QUESTIONS }} 問 / ヒント {{ hintsOpened.size }}/{{ HINTS_MAX }}</div>
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
        <article
          class="no-copy bg-white border border-slate-200 rounded p-3 text-sm leading-relaxed"
          @copy.prevent
          @cut.prevent
          @contextmenu.prevent
          @dragstart.prevent>
          {{ maskTitle(firstSentence(article.introExtract), article.title, article.aliases ?? []) }}
        </article>

        <!-- ヒント -->
        <div class="flex flex-wrap gap-2">
          <button
            v-for="h in hints"
            :key="h.key"
            @click="openHint(h.key)"
            :disabled="!h.available || hintsOpened.has(h.key) || answered || hintsOpened.size >= HINTS_MAX"
            class="px-3 py-1.5 rounded border text-xs"
            :class="hintsOpened.has(h.key)
              ? 'bg-emerald-100 border-emerald-300 text-emerald-700'
              : 'bg-white border-slate-300 hover:bg-slate-50 disabled:opacity-40'">
            + {{ h.label }} ({{ hintsOpened.has(h.key) ? '開封済' : '-200点' }})
          </button>
        </div>

        <div class="space-y-2">
          <div v-if="hintsOpened.has('sections')" class="bg-amber-50 border border-amber-200 rounded p-3 text-xs">
            <div class="font-bold text-amber-700 mb-1">目次</div>
            <span v-for="s in article.sections" :key="s.title" class="inline-block px-2 py-0.5 mr-1 mb-1 bg-white rounded">{{ s.title }}</span>
          </div>
          <div v-if="hintsOpened.has('image') && article.imageUrls.length" class="bg-amber-50 border border-amber-200 rounded p-3">
            <div class="text-xs text-amber-700 font-bold mb-1">画像</div>
            <img :src="article.imageUrls[imageIndex]" alt="" class="max-h-48 mx-auto" />
          </div>
          <div v-if="hintsOpened.has('infobox') && infoboxItem" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">インフォボックス</div>
            <div><span class="font-bold">{{ infoboxItem.key }}</span>: {{ infoboxItem.value }}</div>
          </div>
        </div>

        <!-- 選択肢 -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
          <button
            v-for="c in choices"
            :key="c"
            @click="selectChoice(c)"
            :disabled="answered"
            class="px-3 py-3 rounded border text-left text-sm"
            :class="[
              selectedChoice === c ? 'border-blue-500 bg-blue-50' : 'border-slate-300 bg-white hover:bg-slate-50',
              answered && c === article.title ? 'border-emerald-500 bg-emerald-50' : '',
              answered && selectedChoice === c && c !== article.title ? 'border-red-500 bg-red-50' : '',
            ]">
            {{ c }}
          </button>
        </div>

        <div v-if="!answered">
          <button @click="submit" :disabled="!selectedChoice"
            class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:bg-slate-300">
            決定
          </button>
        </div>

        <div v-else class="space-y-3">
          <div class="bg-slate-100 rounded p-3">
            <div class="text-sm">答え: <span class="font-bold">{{ article.title }}</span></div>
            <div v-if="results[results.length - 1].correct" class="text-lg font-bold text-emerald-600">
              正解! ヒント {{ results[results.length - 1].hintsUsed }} で当てた
            </div>
            <div v-else class="text-lg font-bold text-red-600">不正解</div>
            <div class="text-sm text-slate-700">スコア: {{ results[results.length - 1].score }}</div>
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
