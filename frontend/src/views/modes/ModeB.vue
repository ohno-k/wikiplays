<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import ModeLayout from './ModeLayout.vue'
import GenrePicker from '../../components/GenrePicker.vue'
import FameTierPicker from '../../components/FameTierPicker.vue'
import CurrentGenreBadge from '../../components/CurrentGenreBadge.vue'
import { fetchDecoys } from '../../api'
import type { ArticleData, Genre, Scope } from '../../types'
import { isCorrect, scoreEmoji } from '../../scoring'
import { maskTitle, splitSentences } from '../../masking'
import { useArticleQueue } from '../../composables/useArticleQueue'
import { useFameTier } from '../../composables/useFameTier'
import { useSessionRecorder } from '../../composables/useSessionRecorder'
import { useAuth } from '../../composables/useAuth'
import XpResultCard from '../../components/XpResultCard.vue'
import ResultShareCard from '../../components/ResultShareCard.vue'

const TOTAL_QUESTIONS = 5
const MAX_SCORE = 1000
const MAX_TOTAL = MAX_SCORE * TOTAL_QUESTIONS

type HintKey =
  | 'charCount'
  | 'langlinks'
  | 'length'
  | 'categories'
  | 'sections'
  | 'infobox'
  | 'related'
  | 'image'
  | 'image2'
  | 'lastChar'
  | 'intro'
  | 'sentence2'
  | 'firstChar'

interface HintCard {
  key: HintKey
  label: string
  cost: number
  description: string
}

/** カードリスト (コスト昇順)。 */
const CARDS: HintCard[] = [
  { key: 'charCount',  label: '答えの文字数',         cost: 30,  description: 'タイトルの長さ' },
  { key: 'langlinks',  label: '他言語版の数',         cost: 50,  description: '記事の知名度の目安' },
  { key: 'length',     label: '記事の長さ',           cost: 50,  description: 'バイト数だけ表示' },
  { key: 'categories', label: 'カテゴリ一覧',         cost: 100, description: 'ジャンルの手がかり' },
  { key: 'sections',   label: '目次',                 cost: 150, description: '記事の章構成' },
  { key: 'infobox',    label: 'インフォボックス1項目', cost: 150, description: 'ランダムな1項目' },
  { key: 'lastChar',   label: '末尾の文字',           cost: 200, description: 'タイトルの最後の文字' },
  { key: 'related',    label: '同分野の関連記事',     cost: 200, description: '近いトピックの他記事3件' },
  { key: 'image',      label: '画像 1 枚目',          cost: 200, description: '記事内の画像' },
  { key: 'image2',     label: 'もう 1 枚画像',        cost: 200, description: '別の画像' },
  { key: 'intro',      label: '冒頭 1 文目',          cost: 300, description: '本文の最初の文' },
  { key: 'sentence2',  label: '冒頭 2 文目',          cost: 300, description: '本文の 2 番目の文' },
  { key: 'firstChar',  label: '答えの頭文字',         cost: 400, description: 'タイトルの最初の文字' },
]

const loading = ref(false)
const error = ref<string | null>(null)
const article = ref<ArticleData | null>(null)
const answer = ref('')
const answered = ref(false)
const opened = ref<Set<HintKey>>(new Set())
const infoboxItem = ref<{ key: string; value: string } | null>(null)
const imageIdx1 = ref(0)
const imageIdx2 = ref(1)
const relatedTitles = ref<string[]>([])
const relatedLoading = ref(false)
const currentQ = ref(1)
const results = ref<{ title: string; score: number; opened: HintKey[]; correct: boolean }[]>([])

const finished = computed(() => results.value.length >= TOTAL_QUESTIONS)
const totalScore = computed(() => results.value.reduce((s, r) => s + r.score, 0))

const introSentences = computed(() => {
  if (!article.value) return [] as string[]
  return splitSentences(article.value.introExtract).map(s => maskTitle(s, article.value!.title, article.value!.aliases ?? []))
})

const answerCoreLength = computed(() => {
  if (!article.value) return 0
  return article.value.title.split(/[（(]/)[0].trim().length
})

const lastCharOfAnswer = computed(() => {
  if (!article.value) return ''
  const core = article.value.title.split(/[（(]/)[0].trim()
  return core.charAt(core.length - 1)
})

const firstCharOfAnswer = computed(() => {
  if (!article.value) return ''
  const core = article.value.title.split(/[（(]/)[0].trim()
  return core.charAt(0)
})

const availableCards = computed(() => {
  if (!article.value) return [] as HintCard[]
  const a = article.value
  return CARDS.filter(c => {
    switch (c.key) {
      case 'intro':      return introSentences.value.length >= 1
      case 'sentence2':  return introSentences.value.length >= 2
      case 'sections':   return a.sections.length > 0
      case 'image':      return a.imageUrls.length > 0
      case 'image2':     return a.imageUrls.length > 1
      case 'infobox':    return Object.keys(a.infobox).length > 0
      case 'categories': return a.categories.length > 0
      case 'related':    return a.categories.length > 0
      case 'langlinks':  return true
      case 'length':     return true
      case 'charCount':  return true
      case 'firstChar':  return true
      case 'lastChar':   return true
    }
  })
})

const currentCost = computed(() => {
  let total = 0
  for (const key of opened.value) {
    const c = CARDS.find(x => x.key === key)
    if (c) total += c.cost
  }
  return total
})
const currentMaxScore = computed(() => Math.max(0, MAX_SCORE - currentCost.value))

async function openCard(key: HintKey) {
  if (!article.value || opened.value.has(key)) return
  opened.value.add(key)
  if (key === 'infobox') {
    const keys = Object.keys(article.value.infobox)
    if (keys.length > 0) {
      const k = keys[Math.floor(Math.random() * keys.length)]
      infoboxItem.value = { key: k, value: article.value.infobox[k] }
    }
  }
  if (key === 'image') {
    imageIdx1.value = 0
  }
  if (key === 'image2') {
    // image とかぶらないようにランダム選択
    if (article.value.imageUrls.length > 1) {
      let i = 1
      do {
        i = Math.floor(Math.random() * article.value.imageUrls.length)
      } while (i === imageIdx1.value && article.value.imageUrls.length > 1)
      imageIdx2.value = i
    }
  }
  if (key === 'related') {
    relatedLoading.value = true
    try {
      const decoys = await fetchDecoys(article.value.categories.slice(0, 3), article.value.title, 3)
      relatedTitles.value = decoys
    } catch {
      relatedTitles.value = []
    } finally {
      relatedLoading.value = false
    }
  }
}

const selectedGenre = ref<Genre | null>(null)
const selectedScope = ref<Scope>('jp')
const started = ref(false)
const { fameTier } = useFameTier()

const { token, isLoggedIn } = useAuth()
const recorder = useSessionRecorder('b')

const queue = useArticleQueue<ArticleData>({
  genre: selectedGenre,
  scope: selectedScope,
  fameTier,
  token,
  prepare: (a) => {
    if (!a.introExtract || a.introExtract.length < 30) return null
    return a
  }
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
  answer.value = ''
  opened.value = new Set()
  infoboxItem.value = null
  imageIdx1.value = 0
  imageIdx2.value = 1
  relatedTitles.value = []
  try {
    article.value = await queue.pull()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

function submit() {
  if (!article.value) return
  const correct = isCorrect(answer.value, article.value.title, article.value.aliases ?? [])
  const score = correct ? currentMaxScore.value : 0
  results.value.push({
    title: article.value.title,
    score,
    opened: Array.from(opened.value),
    correct,
  })
  answered.value = true
}

function giveUp() {
  if (!article.value) return
  results.value.push({ title: article.value.title, score: 0, opened: Array.from(opened.value), correct: false })
  answered.value = true
}

function nextQuestion() {
  if (currentQ.value < TOTAL_QUESTIONS) { currentQ.value++; loadNext() }
}
function restart() { results.value = []; currentQ.value = 1; recorder.reset(); queue.reset(); loadNext() }

const shareText = computed(() => {
  const emojis = results.value.map(r => scoreEmoji(r.score, MAX_SCORE)).join('')
  return `Wikiplays Bモード ${totalScore.value}/${MAX_SCORE * TOTAL_QUESTIONS}\n${emojis}`
})

watch(finished, (v) => {
  if (!v) return
  recorder.record({ genre: selectedGenre.value, scope: selectedScope.value, score: totalScore.value, maxScore: MAX_TOTAL })
})


</script>

<template>
  <ModeLayout mode-name="ヒントカード" short-name="B モード" emoji="🃏" theme="purple" gradient="from-fuchsia-500 to-purple-600">
    <template v-if="!started">
      <FameTierPicker v-model="fameTier" />
      <GenrePicker
        mode-name="B モード"
        theme-gradient="from-fuchsia-500 to-purple-600"
        @select="onGenreSelected" />
    </template>

    <template v-else>
      <CurrentGenreBadge :genre="selectedGenre" :scope="selectedScope" :fame-tier="fameTier" @change="changeGenre" />

      <p class="text-sm text-slate-600">
        ヒントカードを開くたびにコストが引かれます。<span class="font-bold">安いカードで効率的に当てた人が高得点</span>。
      </p>

    <div v-if="finished" class="space-y-4">
      <div class="bg-blue-50 border border-blue-200 rounded p-4">
        <div class="text-sm text-blue-900">最終スコア</div>
        <div class="text-3xl font-bold text-blue-700">{{ totalScore }} / {{ MAX_SCORE * TOTAL_QUESTIONS }}</div>
      </div>
      <div class="bg-white border border-slate-200 rounded p-4 space-y-2">
        <div v-for="(r, i) in results" :key="i" class="text-sm border-b border-slate-100 last:border-0 pb-2 last:pb-0">
          <div class="flex justify-between font-bold">
            <span>{{ i + 1 }}. {{ r.title }}</span>
            <span>{{ r.score }}点</span>
          </div>
          <div class="text-xs text-slate-500">
            {{ r.correct ? '正解' : '不正解' }} / 開いたカード: {{ r.opened.length }} 枚
          </div>
        </div>
      </div>
      <XpResultCard v-if="recorder.xpResult.value" :xp="recorder.xpResult.value" />
      <div v-else-if="!isLoggedIn" class="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded p-3">
        ログインすると XP とレベルが貯まり、ランキングに参加できます。
        <router-link to="/login" class="text-blue-600 hover:underline ml-1">ログイン / 登録</router-link>
      </div>
      <ResultShareCard
        title="B モード"
        :total-score="totalScore"
        :max-score="MAX_TOTAL"
        :results="results"
        theme-gradient="from-fuchsia-500 via-purple-500 to-indigo-600"
        :share-text="shareText" />
      <button @click="restart" class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">もう一度</button>
    </div>

    <div v-else>
      <div class="text-sm text-slate-500 mb-2 flex justify-between">
        <span>第 {{ currentQ }} 問 / {{ TOTAL_QUESTIONS }} 問</span>
        <span>現在の最大スコア: <span class="font-bold text-blue-700">{{ currentMaxScore }}</span> / {{ MAX_SCORE }} (使用 {{ currentCost }})</span>
      </div>
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
        <!-- ヒントカードボタン群 -->
        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          <button
            v-for="c in availableCards"
            :key="c.key"
            @click="openCard(c.key)"
            :disabled="opened.has(c.key) || answered"
            class="px-2 py-2 rounded border text-xs text-left"
            :class="opened.has(c.key)
              ? 'bg-emerald-100 border-emerald-300 text-emerald-700'
              : 'bg-white border-slate-300 hover:bg-slate-50 disabled:opacity-40'">
            <div class="font-bold">{{ c.label }}</div>
            <div class="text-slate-500">-{{ c.cost }} 点</div>
          </button>
        </div>

        <!-- 開示された情報 -->
        <div class="no-copy space-y-2" @copy.prevent @cut.prevent @contextmenu.prevent @dragstart.prevent>
          <div v-if="opened.has('charCount')" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">答えの文字数</div>
            <div class="text-lg font-bold">{{ answerCoreLength }} 文字</div>
          </div>
          <div v-if="opened.has('langlinks')" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">他言語版の数</div>
            <div class="text-lg font-bold">{{ article.languageLinkCount }} 言語</div>
          </div>
          <div v-if="opened.has('length')" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">記事の長さ</div>
            <div class="text-lg font-bold">{{ article.articleLength.toLocaleString() }} バイト</div>
          </div>
          <div v-if="opened.has('categories')" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">カテゴリ</div>
            <div class="flex flex-wrap gap-1">
              <span v-for="c in article.categories" :key="c" class="px-2 py-1 bg-white border border-slate-200 rounded text-xs">{{ c }}</span>
            </div>
          </div>
          <div v-if="opened.has('sections')" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">目次</div>
            <ul class="text-slate-700">
              <li v-for="s in article.sections" :key="s.title" :style="{ marginLeft: `${(s.level - 1) * 1}em` }">・{{ s.title }}</li>
            </ul>
          </div>
          <div v-if="opened.has('infobox') && infoboxItem" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">インフォボックス</div>
            <div><span class="font-bold">{{ infoboxItem.key }}</span>: {{ infoboxItem.value }}</div>
          </div>
          <div v-if="opened.has('lastChar')" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">末尾の文字</div>
            <div class="text-2xl font-bold">... {{ lastCharOfAnswer }}</div>
          </div>
          <div v-if="opened.has('related')" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">同分野の関連記事</div>
            <div v-if="relatedLoading" class="text-slate-500">読み込み中…</div>
            <div v-else-if="relatedTitles.length" class="flex flex-wrap gap-1">
              <span v-for="t in relatedTitles" :key="t" class="px-2 py-1 bg-white border border-slate-200 rounded text-xs">{{ t }}</span>
            </div>
            <div v-else class="text-slate-500 text-xs">関連記事が見つかりませんでした</div>
          </div>
          <div v-if="opened.has('image') && article.imageUrls.length" class="bg-amber-50 border border-amber-200 rounded p-3">
            <div class="text-xs text-amber-700 font-bold mb-1">画像 1 枚目</div>
            <img :src="article.imageUrls[imageIdx1]" alt="" class="max-h-60 mx-auto" />
          </div>
          <div v-if="opened.has('image2') && article.imageUrls.length > 1" class="bg-amber-50 border border-amber-200 rounded p-3">
            <div class="text-xs text-amber-700 font-bold mb-1">画像 2 枚目</div>
            <img :src="article.imageUrls[imageIdx2]" alt="" class="max-h-60 mx-auto" />
          </div>
          <div v-if="opened.has('intro') && introSentences.length >= 1" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">冒頭 1 文目</div>
            {{ introSentences[0] }}
          </div>
          <div v-if="opened.has('sentence2') && introSentences.length >= 2" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">冒頭 2 文目</div>
            {{ introSentences[1] }}
          </div>
          <div v-if="opened.has('firstChar')" class="bg-amber-50 border border-amber-200 rounded p-3 text-sm">
            <div class="text-xs text-amber-700 font-bold mb-1">答えの頭文字</div>
            <div class="text-2xl font-bold">{{ firstCharOfAnswer }} ...</div>
          </div>
        </div>

        <!-- 回答エリア -->
        <div v-if="!answered" class="space-y-2">
          <div class="flex gap-2">
            <input v-model="answer" @keyup.enter="submit" type="text" placeholder="記事のタイトル (略称・別名も可)"
              class="flex-1 border border-slate-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            <button @click="submit" :disabled="!answer" class="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:bg-slate-300">
              回答
            </button>
          </div>
          <button @click="giveUp" class="px-3 py-1 text-sm bg-slate-200 rounded hover:bg-slate-300">ギブアップ</button>
        </div>

        <div v-else class="space-y-3">
          <div class="bg-slate-100 rounded p-3">
            <div class="text-sm">答え: <span class="font-bold">{{ article.title }}</span></div>
            <div v-if="results[results.length - 1].correct" class="text-lg font-bold text-emerald-600">
              正解! コスト {{ currentCost }} / 獲得 {{ results[results.length - 1].score }} 点
            </div>
            <div v-else class="text-lg font-bold text-red-600">不正解</div>
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
