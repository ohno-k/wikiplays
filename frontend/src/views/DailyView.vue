<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { ArticleData, Genre, Scope } from '../types'
import { GENRES, SCOPE_LABELS } from '../types'
import {
  fetchDailyChallenge,
  submitDailyScore,
  fetchDailyLeaderboard,
  getPlayerId,
  getDisplayName,
  setDisplayName,
  recordPlay,
  type DailyChallengeResponse,
  type DailyLeaderboardEntry,
} from '../api'
import { maskTitle, splitParagraphs } from '../masking'
import { isCorrect, scoreEmoji } from '../scoring'
import ResultShareCard from '../components/ResultShareCard.vue'

// ===== ジャンル選択 =====
const selectedGenre = ref<Genre | null>(null)
const selectedScope = ref<Scope>('jp')
const view = ref<'pick' | 'play' | 'result'>('pick')

const challenge = ref<DailyChallengeResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const leaderboard = ref<DailyLeaderboardEntry[]>([])
const displayName = ref(getDisplayName())
const submitted = ref(false)

const TOTAL_QUESTIONS = 5
const SCORES_BY_REVEAL = [1000, 800, 600, 400, 200, 100]
const REVEAL_INTERVAL_MS = 10_000

const HIRAGANA = 'あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽぁぃぅぇぉっゃゅょー'
const KATAKANA = 'アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポァィゥェォッャュョー'
const ALPHA_LOWER = 'abcdefghijklmnopqrstuvwxyz'
const ALPHA_UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
const DIGITS = '0123456789'
const SKIP_CHAR_RE = /[・＝＋＝\s\-/「」『』（）()【】\[\]"',.、。!！?？]/

const currentQ = ref(0) // 0-indexed
const currentArticle = ref<ArticleData | null>(null)
const paragraphs = ref<string[]>([])
const revealed = ref(1)
const answerChars = ref<string[]>([])
const charOptions = ref<string[][]>([])
const currentPos = ref(0)
const answered = ref(false)
const correctFlag = ref(false)
const autoTimer = ref<number | null>(null)
const qResults = ref<{ score: number; correct: boolean; revealedCount: number }[]>([])

const totalScore = computed(() => qResults.value.reduce((s, r) => s + r.score, 0))
const visibleParagraphs = computed(() => {
  const n = paragraphs.value.length
  const start = Math.max(0, n - revealed.value)
  return paragraphs.value.slice(start)
})
const hasMore = computed(() => revealed.value < paragraphs.value.length)
const currentChoices = computed(() => charOptions.value[currentPos.value] ?? [])

const genreMeta = computed(() =>
  challenge.value?.genre ? GENRES.find(g => g.id === challenge.value!.genre) : null
)
const scopeMeta = computed(() =>
  challenge.value?.scope ? SCOPE_LABELS[challenge.value.scope] : null
)

function clearTimers() {
  if (autoTimer.value !== null) { clearTimeout(autoTimer.value); autoTimer.value = null }
}
function scheduleNextReveal() {
  clearTimers()
  if (!hasMore.value || answered.value) return
  autoTimer.value = window.setTimeout(() => {
    if (!answered.value && hasMore.value) { revealed.value++; scheduleNextReveal() }
  }, REVEAL_INTERVAL_MS)
}

function shuffle<T>(arr: T[]): T[] {
  const a = arr.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}
function isKanji(c: string): boolean { return /\p{Script=Han}/u.test(c) }
function generateOptions(target: string, fullExtract: string): string[] {
  let pool: string[] = []
  if (isKanji(target)) {
    const kanji = fullExtract.match(/\p{Script=Han}/gu) || []
    pool = Array.from(new Set(kanji)).filter(c => c !== target)
    if (pool.length < 3) {
      const filler = '人本年日中大国生学者一二三月時山田'.split('')
      for (const f of filler) {
        if (f !== target && !pool.includes(f)) pool.push(f)
        if (pool.length >= 10) break
      }
    }
  } else if (/[぀-ゟ]/.test(target)) pool = Array.from(HIRAGANA).filter(c => c !== target)
  else if (/[゠-ヿ]/.test(target)) pool = Array.from(KATAKANA).filter(c => c !== target)
  else if (/[a-z]/.test(target)) pool = Array.from(ALPHA_LOWER).filter(c => c !== target)
  else if (/[A-Z]/.test(target)) pool = Array.from(ALPHA_UPPER).filter(c => c !== target)
  else if (/[0-9]/.test(target)) pool = Array.from(DIGITS).filter(c => c !== target)
  else pool = ['・', '＝', '＋', 'ー'].filter(c => c !== target)
  const dummies = shuffle(pool).slice(0, 3)
  return shuffle([target, ...dummies])
}
function getAnswerChars(title: string): string[] {
  const main = title.split(/[（(]/)[0].trim()
  return Array.from(main)
}
function advanceSkippable() {
  while (currentPos.value < answerChars.value.length && SKIP_CHAR_RE.test(answerChars.value[currentPos.value])) {
    currentPos.value++
  }
}

function setupArticle(a: ArticleData) {
  const headings = a.sections.map(s => s.title)
  const ps = splitParagraphs(a.fullExtract, headings).map(p => maskTitle(p, a.title))
  paragraphs.value = ps.length >= 1 ? ps : [maskTitle(a.introExtract, a.title)]
  const chars = getAnswerChars(a.title)
  answerChars.value = chars
  charOptions.value = chars.map(c => SKIP_CHAR_RE.test(c) ? [c] : generateOptions(c, a.fullExtract))
  currentPos.value = 0
  revealed.value = 1
  answered.value = false
  correctFlag.value = false
  currentArticle.value = a
  advanceSkippable()
  scheduleNextReveal()
}

async function startChallenge() {
  loading.value = true
  error.value = null
  view.value = 'play'
  qResults.value = []
  currentQ.value = 0
  submitted.value = false
  try {
    challenge.value = await fetchDailyChallenge(selectedGenre.value, selectedScope.value)
    if (challenge.value.articles.length < TOTAL_QUESTIONS) {
      error.value = '今日の問題がまだ準備中です。少し待ってからもう一度試してください。'
      view.value = 'pick'
      return
    }
    setupArticle(challenge.value.articles[0])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '読み込み失敗'
    view.value = 'pick'
  } finally {
    loading.value = false
  }
}

function selectChar(char: string) {
  if (answered.value || !currentArticle.value) return
  const expected = answerChars.value[currentPos.value]
  if (char === expected) {
    currentPos.value++
    advanceSkippable()
    if (currentPos.value >= answerChars.value.length) finish(true)
  } else {
    finish(false)
  }
}

function finish(correct: boolean) {
  if (!currentArticle.value) return
  clearTimers()
  correctFlag.value = correct
  const maxScore = SCORES_BY_REVEAL[Math.min(revealed.value - 1, SCORES_BY_REVEAL.length - 1)] ?? 100
  const totalInputChars = answerChars.value.filter(c => !SKIP_CHAR_RE.test(c)).length
  const correctChars = answerChars.value.slice(0, currentPos.value).filter(c => !SKIP_CHAR_RE.test(c)).length
  let score: number
  if (correct) score = maxScore
  else if (totalInputChars > 0) score = Math.round(maxScore * (correctChars / totalInputChars) * 0.5)
  else score = 0
  qResults.value.push({ score, correct, revealedCount: revealed.value })
  answered.value = true
}

function giveUp() { finish(false) }

function nextQuestion() {
  if (!challenge.value) return
  currentQ.value++
  if (currentQ.value >= TOTAL_QUESTIONS) {
    view.value = 'result'
    submitToServer()
    return
  }
  setupArticle(challenge.value.articles[currentQ.value])
}

async function submitToServer() {
  if (!challenge.value || submitted.value) return
  if (displayName.value) setDisplayName(displayName.value)
  await submitDailyScore({
    dailyChallengeId: challenge.value.id,
    playerId: getPlayerId(),
    displayName: displayName.value || '名無し',
    score: totalScore.value,
  })
  // LocalStorage に履歴記録
  recordPlay({
    mode: 'daily',
    genre: challenge.value.genre,
    scope: challenge.value.scope,
    score: totalScore.value,
    maxScore: 1000 * TOTAL_QUESTIONS,
  })
  submitted.value = true
  loadLeaderboard()
}

async function loadLeaderboard() {
  if (!challenge.value) return
  try {
    leaderboard.value = await fetchDailyLeaderboard(challenge.value.id)
  } catch {
    leaderboard.value = []
  }
}

const shareText = computed(() => {
  if (!challenge.value) return ''
  const emojis = qResults.value.map(r => scoreEmoji(r.score, 1000)).join('')
  const scopeLabel = scopeMeta.value ? `${scopeMeta.value.emoji}${scopeMeta.value.name}` : ''
  const genreLabel = genreMeta.value ? `${genreMeta.value.emoji}${genreMeta.value.name}` : '総合'
  return `Wikiplays デイリー ${challenge.value.date} ${scopeLabel}${genreLabel}\n${totalScore.value}/${1000 * TOTAL_QUESTIONS}\n${emojis}\nhttps://wikiplays.me/daily`
})

function isCorrectFinal(): boolean {
  if (!currentArticle.value || !answered.value) return false
  return correctFlag.value
}

onMounted(() => { /* no auto start */ })
onUnmounted(clearTimers)
// 'isCorrect' import を使うため (1問完答判定の保険)
void isCorrect
</script>

<template>
  <section class="space-y-5 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-5 space-y-2">
      <div class="text-xs font-mono text-slate-500">DAILY CHALLENGE</div>
      <h1 class="text-2xl font-bold tracking-tight">今日の 5 問チャレンジ</h1>
      <p class="text-sm text-slate-600">
        全プレイヤーが同じ問題に挑戦します。スコアでランキング入りを狙ってください。
        1 日 1 回まで挑戦可能。
      </p>
    </div>

    <!-- ジャンル選択 -->
    <div v-if="view === 'pick'" class="space-y-4">
      <div class="text-sm font-bold">ジャンルを選んでください</div>

      <!-- スコープ -->
      <div class="flex justify-center gap-2">
        <button v-for="(meta, key) in SCOPE_LABELS" :key="key"
          @click="selectedScope = (key as Scope)"
          :class="[
            'px-4 py-2 rounded-full text-sm font-bold transition flex items-center gap-2',
            selectedScope === key
              ? 'bg-gradient-to-r from-amber-500 to-rose-500 text-white shadow-md'
              : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50',
          ]">
          <span>{{ meta.emoji }}</span><span>{{ meta.name }}</span>
        </button>
      </div>

      <!-- 総合 -->
      <button @click="selectedGenre = null; startChallenge()"
        class="w-full glass-card glass-card-hover p-4 flex items-center gap-3 text-left">
        <div class="text-3xl">🎲</div>
        <div class="flex-1">
          <div class="font-bold">総合 (おまかせ)</div>
          <div class="text-xs text-slate-500">全 Wikipedia から 5 問</div>
        </div>
        <div class="text-slate-400">→</div>
      </button>

      <div class="grid grid-cols-2 sm:grid-cols-3 gap-2">
        <button v-for="g in GENRES" :key="g.id"
          @click="selectedGenre = g.id; startChallenge()"
          class="glass-card glass-card-hover p-3 text-left flex items-center gap-2">
          <span class="text-2xl">{{ g.emoji }}</span>
          <div>
            <div class="font-bold text-sm">{{ g.name }}</div>
            <div class="text-xs text-slate-500">{{ SCOPE_LABELS[selectedScope].emoji }} {{ SCOPE_LABELS[selectedScope].name }}</div>
          </div>
        </button>
      </div>

      <div v-if="error" class="text-red-600 text-sm">{{ error }}</div>
    </div>

    <!-- プレイ画面 -->
    <div v-else-if="view === 'play'">
      <div v-if="loading" class="text-slate-500">読み込み中…</div>
      <div v-else-if="currentArticle">
        <div class="text-sm text-slate-500 mb-2 flex justify-between">
          <span>第 {{ currentQ + 1 }} 問 / {{ TOTAL_QUESTIONS }}</span>
          <span>{{ revealed }} / {{ paragraphs.length }} 段落 / 累計 {{ totalScore }}</span>
        </div>

        <div v-if="!answered && hasMore" class="h-1.5 bg-slate-200 rounded overflow-hidden mb-3">
          <div class="h-full bg-amber-500 progress-bar" :key="revealed"></div>
        </div>

        <article class="bg-white border border-slate-200 rounded p-4 leading-relaxed text-sm min-h-32 space-y-3 max-h-[50vh] overflow-y-auto">
          <p v-if="revealed < paragraphs.length" class="text-slate-300 text-center">… (前段落は未開示)</p>
          <p v-for="(p, i) in visibleParagraphs" :key="i" class="whitespace-pre-wrap">{{ p }}</p>
        </article>

        <div class="bg-white border border-slate-200 rounded p-4 space-y-3 mt-4">
          <div class="text-xs text-slate-500">{{ answerChars.length }} 文字</div>
          <div class="text-2xl font-bold text-center tracking-wider flex flex-wrap justify-center gap-1">
            <template v-for="(c, i) in answerChars" :key="i">
              <span v-if="SKIP_CHAR_RE.test(c)" class="text-slate-400">{{ c }}</span>
              <span v-else-if="i < currentPos" class="text-emerald-600">{{ c }}</span>
              <span v-else-if="i === currentPos && !answered" class="text-blue-600 underline">_</span>
              <span v-else class="text-slate-300">・</span>
            </template>
          </div>

          <div v-if="!answered && currentChoices.length === 4" class="grid grid-cols-4 gap-2">
            <button v-for="opt in currentChoices" :key="opt"
              @click="selectChar(opt)"
              class="px-2 py-3 rounded border-2 border-slate-300 bg-white hover:bg-amber-50 hover:border-amber-400 text-xl font-bold transition">
              {{ opt }}
            </button>
          </div>

          <div v-if="!answered" class="flex gap-2 justify-end">
            <button @click="giveUp" class="px-3 py-1 bg-slate-200 rounded hover:bg-slate-300 text-xs">ギブアップ</button>
          </div>
        </div>

        <div v-if="answered" class="space-y-3 mt-4">
          <div class="bg-slate-100 rounded p-3">
            <div class="text-sm">答え: <span class="font-bold">{{ currentArticle.title }}</span></div>
            <div v-if="isCorrectFinal()" class="text-lg font-bold text-emerald-600">
              正解! {{ qResults[qResults.length-1].revealedCount }} 段落で当てた
            </div>
            <div v-else class="text-lg font-bold text-red-600">不正解</div>
            <div class="text-sm">スコア: {{ qResults[qResults.length-1].score }}</div>
          </div>
          <button @click="nextQuestion" class="w-full px-4 py-2 bg-amber-600 text-white rounded hover:bg-amber-700">
            {{ currentQ + 1 >= TOTAL_QUESTIONS ? '結果を見る' : '次の問題へ' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 結果画面 -->
    <div v-else-if="view === 'result'" class="space-y-4">
      <div class="glass-card p-5 text-center">
        <div class="text-sm text-slate-500">あなたのスコア</div>
        <div class="text-5xl font-bold brand-text mt-2">{{ totalScore }}</div>
        <div class="text-xs text-slate-400">/ {{ 1000 * TOTAL_QUESTIONS }}</div>
      </div>

      <div v-if="!submitted" class="glass-card p-4 space-y-2">
        <label class="text-xs font-bold text-slate-600">ランキングに載せる名前 (任意)</label>
        <input v-model="displayName" type="text" maxlength="32" placeholder="名無し"
          class="w-full border border-slate-300 rounded px-3 py-2 text-sm" />
        <button @click="submitToServer" class="w-full px-4 py-2 bg-amber-600 text-white rounded hover:bg-amber-700">
          スコアを記録
        </button>
      </div>

      <ResultShareCard
        :title="`デイリーチャレンジ ${challenge?.date ?? ''}`"
        :subtitle="genreMeta ? `${scopeMeta?.emoji ?? ''} ${genreMeta.emoji} ${genreMeta.name}` : '🎲 総合'"
        :total-score="totalScore"
        :max-score="1000 * TOTAL_QUESTIONS"
        :results="qResults"
        theme-gradient="from-amber-500 via-orange-500 to-rose-500"
        :share-text="shareText" />

      <div v-if="leaderboard.length" class="glass-card p-4">
        <div class="text-sm font-bold mb-2">ランキング (TOP {{ leaderboard.length }})</div>
        <ol class="space-y-1 text-sm">
          <li v-for="e in leaderboard" :key="e.rank" class="flex justify-between">
            <span><span class="font-mono text-slate-500">#{{ e.rank }}</span> {{ e.displayName }}</span>
            <span class="font-bold">{{ e.score }}</span>
          </li>
        </ol>
      </div>
    </div>
  </section>
</template>

<style scoped>
.progress-bar {
  animation: shrink 10s linear forwards;
  transform-origin: right;
}
@keyframes shrink {
  0% { transform: scaleX(1); }
  100% { transform: scaleX(0); }
}
</style>
