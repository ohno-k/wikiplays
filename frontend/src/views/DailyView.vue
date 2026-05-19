<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { ArticleData } from '../types'
import {
  fetchDailyChallenge,
  submitDailyScore,
  fetchDailyLeaderboard,
  recordPlay,
  submitPlayRecord,
  type DailyChallengeResponse,
  type DailyLeaderboardEntry,
  type PlayRecordResult,
} from '../api'
import { maskTitle, splitParagraphs } from '../masking'
import { isCorrect, scoreEmoji } from '../scoring'
import ResultShareCard from '../components/ResultShareCard.vue'
import { useAuth } from '../composables/useAuth'

const { isLoggedIn, token, refresh: refreshAuth } = useAuth()
const xpResult = ref<PlayRecordResult | null>(null)

// デイリーは全プレイヤー共通の「総合」のみ (ジャンル/スコープ選択なし)
const view = ref<'pick' | 'play' | 'result'>('pick')

const challenge = ref<DailyChallengeResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const leaderboard = ref<DailyLeaderboardEntry[]>([])
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

// デイリーは総合 (genre=null, scope=null) 固定なのでメタ情報も不要

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
  if (!title) return []
  // 通常: タイトル本体 (曖昧さ回避括弧の前) を取る
  let main = title.split(/[（(]/)[0].trim()
  // 「(94) オーロラ」のように括弧から始まる場合は最初の split が空になるので、
  // 括弧書きを丸ごと取り除いた残りを使う
  if (!main) {
    main = title.replace(/[（(][^（()）]*[）)]/g, '').trim()
  }
  if (!main) main = title.trim()
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

/** 入場時に今日の challenge を取得し、既プレイ判定を行う。 */
async function loadChallenge() {
  if (!isLoggedIn.value) return
  loading.value = true
  error.value = null
  try {
    challenge.value = await fetchDailyChallenge(null, null, token.value)
    if (challenge.value.myScore != null) {
      // 既に今日のスコアあり → 開始ボタンを出さず、結果画面 (リーダーボード) に直接遷移
      submitted.value = true
      view.value = 'result'
      loadLeaderboard()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '読み込み失敗'
  } finally {
    loading.value = false
  }
}

async function startChallenge() {
  if (!challenge.value) {
    await loadChallenge()
  }
  const c = challenge.value
  if (!c || c.myScore != null) return  // 未取得 or 既プレイなら開始しない
  loading.value = true
  error.value = null
  view.value = 'play'
  qResults.value = []
  currentQ.value = 0
  submitted.value = false
  try {
    if (c.articles.length < TOTAL_QUESTIONS) {
      error.value = '今日の問題がまだ準備中です。少し待ってからもう一度試してください。'
      view.value = 'pick'
      return
    }
    setupArticle(c.articles[0])
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
  const ok = await submitDailyScore({
    dailyChallengeId: challenge.value.id,
    score: totalScore.value,
  }, token.value)
  if (!ok) {
    error.value = 'スコアの記録に失敗しました (既に今日プレイ済みの可能性があります)。'
    submitted.value = true  // 連打防止
    loadLeaderboard()
    return
  }
  // LocalStorage に履歴記録
  recordPlay({
    mode: 'daily',
    genre: challenge.value.genre,
    scope: challenge.value.scope,
    score: totalScore.value,
    maxScore: 1000 * TOTAL_QUESTIONS,
  })
  // サーバー側 play_record にも記録 (全体ランキング /api/leaderboard 集計用)
  submitPlayRecord({
    mode: 'daily',
    genre: challenge.value.genre,
    scope: challenge.value.scope,
    score: totalScore.value,
    maxScore: 1000 * TOTAL_QUESTIONS,
  }, token.value).then((result) => {
    xpResult.value = result
    if (result && result.xpGained != null) refreshAuth()
  }).catch(() => {})
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
  return `Wikiplays デイリー ${challenge.value.date} 🎲総合\n${totalScore.value}/${1000 * TOTAL_QUESTIONS}\n${emojis}\nhttps://wikiplays.me/daily`
})

function isCorrectFinal(): boolean {
  if (!currentArticle.value || !answered.value) return false
  return correctFlag.value
}

onMounted(() => {
  // 入場時に今日のチャレンジ情報を取得 (既プレイなら結果画面に遷移)
  loadChallenge()
})
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

    <!-- 開始画面 (ログイン済のみ・未プレイのみ) -->
    <div v-else-if="view === 'pick'" class="space-y-4">
      <div v-if="loading" class="text-center text-slate-500 py-6">読み込み中…</div>
      <button v-else @click="startChallenge"
        :disabled="challenge?.myScore != null"
        class="w-full glass-card glass-card-hover p-6 flex items-center gap-4 text-left disabled:opacity-50 disabled:cursor-not-allowed">
        <div class="text-5xl">🎲</div>
        <div class="flex-1">
          <div class="text-xs font-mono text-amber-600 font-bold">START</div>
          <div class="text-lg font-bold">今日のチャレンジを始める</div>
          <div class="text-xs text-slate-500 mt-0.5">全 Wikipedia から 5 問 / 全プレイヤー共通の問題</div>
        </div>
        <div class="text-slate-400 text-2xl">→</div>
      </button>

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

        <article
          class="no-copy bg-white border border-slate-200 rounded p-4 leading-relaxed text-sm min-h-32 space-y-3 max-h-[50vh] overflow-y-auto"
          @copy.prevent
          @cut.prevent
          @contextmenu.prevent
          @dragstart.prevent>
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
      <div v-if="error" class="glass-card p-3 text-sm text-red-700">{{ error }}</div>

      <div class="glass-card p-5 text-center">
        <div class="text-sm text-slate-500">{{ challenge?.myScore != null ? '今日のあなたのスコア' : 'あなたのスコア' }}</div>
        <div class="text-5xl font-bold brand-text mt-2">{{ challenge?.myScore ?? totalScore }}</div>
        <div class="text-xs text-slate-400">/ {{ 1000 * TOTAL_QUESTIONS }}</div>
        <div v-if="challenge?.myScore != null" class="text-xs text-slate-500 mt-2">
          ⏰ デイリーチャレンジは 1 日 1 回。明日また挑戦できます。
        </div>
      </div>

      <!-- XP 獲得表示 (ログイン時のみ) -->
      <div v-if="xpResult && xpResult.xpGained != null"
        :class="[
          'rounded-lg p-4 space-y-2 border',
          xpResult.leveledUp
            ? 'bg-gradient-to-r from-amber-50 to-rose-50 border-amber-300'
            : 'bg-emerald-50 border-emerald-200'
        ]">
        <div class="flex items-baseline justify-between">
          <div class="text-sm font-mono text-slate-500">EXPERIENCE</div>
          <div v-if="xpResult.leveledUp" class="text-sm font-bold text-amber-600">
            ✨ LEVEL UP! → Lv.{{ xpResult.level }}
          </div>
          <div v-else class="text-sm font-bold text-emerald-700">Lv.{{ xpResult.level }}</div>
        </div>
        <div class="text-2xl font-bold text-slate-800">
          +{{ xpResult.xpGained }} XP
          <span v-if="xpResult.xpCapped" class="text-xs font-normal text-slate-500 ml-2">
            (本日のキャップに到達)
          </span>
        </div>
        <div v-if="xpResult.xpForNextLevel != null && xpResult.xpIntoLevel != null"
          class="h-2 bg-slate-200 rounded-full overflow-hidden">
          <div class="h-full bg-gradient-to-r from-sky-400 to-emerald-400 transition-all"
            :style="{ width: `${Math.min(100, (xpResult.xpIntoLevel / xpResult.xpForNextLevel) * 100)}%` }"></div>
        </div>
        <div class="text-xs text-slate-500 flex justify-between">
          <span>{{ xpResult.xpIntoLevel }} / {{ xpResult.xpForNextLevel }} XP</span>
          <span v-if="xpResult.dailyRemaining != null">本日の残り獲得可能: {{ xpResult.dailyRemaining }} XP</span>
        </div>
      </div>

      <ResultShareCard
        v-if="qResults.length > 0"
        :title="`デイリーチャレンジ ${challenge?.date ?? ''}`"
        subtitle="🎲 総合"
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
