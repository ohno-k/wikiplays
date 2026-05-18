<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ModeLayout from './ModeLayout.vue'
import GenrePicker from '../../components/GenrePicker.vue'
import CurrentGenreBadge from '../../components/CurrentGenreBadge.vue'
import type { ArticleData, Genre, Scope } from '../../types'
import { scoreEmoji } from '../../scoring'
import { maskTitle, splitParagraphs } from '../../masking'
import { useArticleQueue } from '../../composables/useArticleQueue'
import { fetchCommunityGenres, recordPlay, fetchPlayQuota, submitPlayRecord, type CommunityGenre, type PlayQuota } from '../../api'
import ResultShareCard from '../../components/ResultShareCard.vue'
import { useAuth } from '../../composables/useAuth'

const { token, isLoggedIn } = useAuth()

const route = useRoute()
const router = useRouter()

interface ModeAData {
  article: ArticleData
  paragraphs: string[]
  chars: string[]
  charOptions: string[][]
}

const TOTAL_QUESTIONS = 5
const SCORES_BY_REVEAL = [1000, 800, 600, 400, 200, 100] // 開示段落数→スコア

type Difficulty = 'relaxed' | 'normal' | 'speed'
interface DifficultyMeta { id: Difficulty; name: string; intervalMs: number; emoji: string; description: string }
const DIFFICULTIES: DifficultyMeta[] = [
  { id: 'relaxed', name: 'のんびり', intervalMs: 20_000, emoji: '🌿', description: '20 秒ごとに 1 段落' },
  { id: 'normal',  name: 'ふつう',   intervalMs: 10_000, emoji: '⏱️', description: '10 秒ごとに 1 段落 (標準)' },
  { id: 'speed',   name: '早押し',   intervalMs:  5_000, emoji: '⚡', description: '5 秒ごとに 1 段落 (上級者向け)' },
]
const selectedDifficulty = ref<Difficulty>('normal')
const revealIntervalMs = computed(() =>
  DIFFICULTIES.find(d => d.id === selectedDifficulty.value)?.intervalMs ?? 10_000
)

const HIRAGANA = 'あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽぁぃぅぇぉっゃゅょー'
const KATAKANA = 'アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポァィゥェォッャュョー'
const ALPHA_LOWER = 'abcdefghijklmnopqrstuvwxyz'
const ALPHA_UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
const DIGITS = '0123456789'

/** 答え判定で読み飛ばす文字 (記号・空白)。プレイヤーの入力対象にならない。 */
const SKIP_CHAR_RE = /[・＝＋＝\s\-/「」『』（）()【】\[\]"',.、。!！?？]/

const loading = ref(false)
const error = ref<string | null>(null)
const article = ref<ArticleData | null>(null)
const paragraphs = ref<string[]>([])
const revealed = ref(1)
const answerChars = ref<string[]>([])      // 答えの各文字
const charOptions = ref<string[][]>([])    // 各位置の 4 択
const currentPos = ref(0)                   // 現在入力中の位置
const wrongChoice = ref<string | null>(null) // 失敗時に押した文字
const answered = ref(false)
const correctFlag = ref(false)
const autoTimer = ref<number | null>(null)
const currentQ = ref(1)
const results = ref<{ title: string; score: number; revealedCount: number; correct: boolean; wrongChoice: string | null; correctChars: number; totalInputChars: number }[]>([])

const finished = computed(() => results.value.length >= TOTAL_QUESTIONS)
const totalScore = computed(() => results.value.reduce((s, r) => s + r.score, 0))

const visibleParagraphs = computed(() => {
  const n = paragraphs.value.length
  const start = Math.max(0, n - revealed.value)
  return paragraphs.value.slice(start)
})

const hasMore = computed(() => revealed.value < paragraphs.value.length)

const currentChoices = computed(() => charOptions.value[currentPos.value] ?? [])

function clearTimers() {
  if (autoTimer.value !== null) { clearTimeout(autoTimer.value); autoTimer.value = null }
}

function scheduleNextReveal() {
  clearTimers()
  if (!hasMore.value || answered.value) return
  autoTimer.value = window.setTimeout(() => {
    if (!answered.value && hasMore.value) {
      revealed.value++
      scheduleNextReveal()
    }
  }, revealIntervalMs.value)
}

function shuffle<T>(arr: T[]): T[] {
  const a = arr.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function isKanji(c: string): boolean {
  return /\p{Script=Han}/u.test(c)
}

function generateOptions(target: string, fullExtract: string): string[] {
  let pool: string[] = []
  if (isKanji(target)) {
    const kanji = fullExtract.match(/\p{Script=Han}/gu) || []
    pool = Array.from(new Set(kanji)).filter(c => c !== target)
    // 漢字が足りなければ、よくある漢字を補充
    if (pool.length < 3) {
      const filler = '人本年日中大国生学者一二三月時山田'.split('')
      for (const f of filler) {
        if (f !== target && !pool.includes(f)) pool.push(f)
        if (pool.length >= 10) break
      }
    }
  } else if (/[぀-ゟ]/.test(target)) {
    pool = Array.from(HIRAGANA).filter(c => c !== target)
  } else if (/[゠-ヿ]/.test(target)) {
    pool = Array.from(KATAKANA).filter(c => c !== target)
  } else if (/[a-z]/.test(target)) {
    pool = Array.from(ALPHA_LOWER).filter(c => c !== target)
  } else if (/[A-Z]/.test(target)) {
    pool = Array.from(ALPHA_UPPER).filter(c => c !== target)
  } else if (/[0-9]/.test(target)) {
    pool = Array.from(DIGITS).filter(c => c !== target)
  } else {
    pool = ['・', '＝', '＋', 'ー'].filter(c => c !== target)
  }
  const dummies = shuffle(pool).slice(0, 3)
  return shuffle([target, ...dummies])
}

/** タイトルから入力対象の文字配列を作る。「タイトル (曖昧さ回避)」の括弧前部分のみ。 */
function getAnswerChars(title: string): string[] {
  const main = title.split(/[（(]/)[0].trim()
  return Array.from(main)
}

function advanceSkippable() {
  while (
    currentPos.value < answerChars.value.length &&
    SKIP_CHAR_RE.test(answerChars.value[currentPos.value])
  ) {
    currentPos.value++
  }
}

const selectedGenre = ref<Genre | null>(null)
const selectedScope = ref<Scope>('jp')
const selectedCommunityGenreId = ref<number | null>(null)
const selectedCommunityGenreName = ref<string>('')
const started = ref(false)
const communityGenres = ref<CommunityGenre[]>([])
const playQuota = ref<PlayQuota | null>(null)

async function refreshQuota() {
  try {
    playQuota.value = await fetchPlayQuota(token.value)
  } catch {
    playQuota.value = null
  }
}

const queue = useArticleQueue<ModeAData>({
  genre: selectedGenre,
  scope: selectedScope,
  communityGenreId: selectedCommunityGenreId,
  prepare: (a) => {
    const headingTitles = a.sections.map(s => s.title)
    const masked = splitParagraphs(a.fullExtract, headingTitles).map(p => maskTitle(p, a.title))
    const chars = getAnswerChars(a.title)
    if (masked.length < 2 || chars.length < 1 || chars.length > 20) return null
    const charOpts = chars.map(c =>
      SKIP_CHAR_RE.test(c) ? [c] : generateOptions(c, a.fullExtract)
    )
    return { article: a, paragraphs: masked, chars, charOptions: charOpts }
  }
})

async function loadNext() {
  loading.value = true
  error.value = null
  answered.value = false
  correctFlag.value = false
  revealed.value = 1
  paragraphs.value = []
  answerChars.value = []
  charOptions.value = []
  currentPos.value = 0
  wrongChoice.value = null
  clearTimers()
  try {
    const d = await queue.pull()
    article.value = d.article
    paragraphs.value = d.paragraphs
    answerChars.value = d.chars
    charOptions.value = d.charOptions
    currentPos.value = 0
    advanceSkippable()
    scheduleNextReveal()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

function revealMore() {
  if (hasMore.value) {
    revealed.value++
    scheduleNextReveal()
  }
}

function selectChar(char: string) {
  if (answered.value || !article.value) return
  const expected = answerChars.value[currentPos.value]
  if (char === expected) {
    currentPos.value++
    advanceSkippable()
    if (currentPos.value >= answerChars.value.length) {
      finish(true)
    }
  } else {
    wrongChoice.value = char
    finish(false)
  }
}

function finish(correct: boolean) {
  if (!article.value) return
  clearTimers()
  correctFlag.value = correct

  // 段階開示で得られる満点
  const maxScore = SCORES_BY_REVEAL[Math.min(revealed.value - 1, SCORES_BY_REVEAL.length - 1)] ?? 100

  // 入力対象 (記号を除く) の文字数と、既に正解した文字数
  const totalInputChars = answerChars.value.filter(c => !SKIP_CHAR_RE.test(c)).length
  const correctChars = answerChars.value
    .slice(0, currentPos.value)
    .filter(c => !SKIP_CHAR_RE.test(c))
    .length

  let score: number
  if (correct) {
    score = maxScore
  } else if (totalInputChars > 0) {
    // 部分点は最大スコアの半分まで (完答とは大きな差をつける)
    score = Math.round(maxScore * (correctChars / totalInputChars) * 0.5)
  } else {
    score = 0
  }

  results.value.push({
    title: article.value.title,
    score,
    revealedCount: revealed.value,
    correct,
    wrongChoice: wrongChoice.value,
    correctChars,
    totalInputChars,
  })
  answered.value = true
}

function giveUp() {
  finish(false)
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

function onGenreSelected(g: Genre | null, s: Scope) {
  selectedGenre.value = g
  selectedScope.value = s
  selectedCommunityGenreId.value = null
  selectedCommunityGenreName.value = ''
  results.value = []
  currentQ.value = 1
  queue.reset()
  started.value = true
  loadNext()
  // クエリパラメータも更新
  router.replace({ query: { ...route.query, community: undefined } })
}

function onCommunityGenreSelected(cg: CommunityGenre) {
  selectedGenre.value = null
  selectedCommunityGenreId.value = cg.id
  selectedCommunityGenreName.value = cg.name
  results.value = []
  currentQ.value = 1
  queue.reset()
  started.value = true
  loadNext()
  router.replace({ query: { ...route.query, community: String(cg.id) } })
}

function changeGenre() {
  started.value = false
  results.value = []
  currentQ.value = 1
  queue.reset()
  clearTimers()
  router.replace({ query: {} })
}

/** ホームから ?community=ID で来た場合、自動開始する。 */
async function tryStartFromQuery() {
  const cid = route.query.community
  if (cid && typeof cid === 'string') {
    const id = parseInt(cid, 10)
    if (!isNaN(id)) {
      // コミュニティジャンル一覧から名前を取得
      try {
        if (communityGenres.value.length === 0) {
          communityGenres.value = await fetchCommunityGenres()
        }
        const found = communityGenres.value.find(g => g.id === id)
        if (found) {
          onCommunityGenreSelected(found)
          return
        }
      } catch {
        // 取得失敗時は通常のジャンル選択画面に
      }
    }
  }
  // ジャンル選択画面のコミュニティ枠用に裏で取得
  fetchCommunityGenres().then(list => { communityGenres.value = list }).catch(() => {})
}

const shareText = computed(() => {
  const emojis = results.value.map(r => scoreEmoji(r.score, 1000)).join('')
  return `Wikiplays Aモード ${totalScore.value}/${1000 * TOTAL_QUESTIONS}\n${emojis}`
})

onMounted(tryStartFromQuery)
onUnmounted(clearTimers)

/** セッション終了時に履歴を記録 (LocalStorage + サーバー)。 */
watch(finished, (v) => {
  if (v) {
    recordPlay({
      mode: 'a',
      genre: selectedGenre.value,
      scope: selectedGenre.value ? selectedScope.value : null,
      communityGenreId: selectedCommunityGenreId.value ?? undefined,
      communityGenreName: selectedCommunityGenreName.value || undefined,
      score: totalScore.value,
      maxScore: 1000 * TOTAL_QUESTIONS,
      difficulty: selectedDifficulty.value,
    })
    // サーバー側にも記録 (Quota カウント + ランキング集計用)
    submitPlayRecord({
      mode: 'a',
      genre: selectedGenre.value,
      scope: selectedGenre.value ? selectedScope.value : null,
      communityGenreId: selectedCommunityGenreId.value,
      score: totalScore.value,
      maxScore: 1000 * TOTAL_QUESTIONS,
      difficulty: selectedDifficulty.value,
    }, token.value).then(() => refreshQuota()).catch(() => {})
  }
})

onMounted(refreshQuota)
</script>

<template>
  <ModeLayout mode-name="段階開示型" short-name="A モード" emoji="⏱️" theme="blue" gradient="from-sky-500 to-indigo-600">
    <!-- Quota 表示 (Free のみ) -->
    <div v-if="!started && playQuota && !playQuota.unlimited"
      class="glass-card p-3 flex items-center justify-between"
      :class="playQuota.remaining === 0 ? 'border-red-300' : ''">
      <div class="text-sm">
        <div class="text-xs font-mono text-slate-500">FREE プラン</div>
        <div class="font-bold">
          今日のプレイ可能数: {{ playQuota.remaining }} / {{ playQuota.limit }}
        </div>
      </div>
      <router-link v-if="playQuota.remaining < 3" to="/account"
        class="text-xs px-3 py-1 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded-full font-bold hover:opacity-90">
        ⭐ アップグレード
      </router-link>
    </div>

    <!-- 制限到達時の警告 -->
    <div v-if="!started && playQuota && !playQuota.unlimited && playQuota.remaining === 0"
      class="glass-card p-5 text-center space-y-2 ring-1 ring-red-200">
      <div class="text-3xl">🛑</div>
      <div class="font-bold">今日のプレイ上限に達しました</div>
      <div class="text-sm text-slate-600">
        フリープランは 1 日 5 問までです。プレミアムにアップグレードすると無制限に遊べます。
      </div>
      <router-link v-if="!isLoggedIn" to="/login"
        class="inline-block px-4 py-2 bg-blue-600 text-white rounded font-bold hover:bg-blue-700">
        ログイン / 登録
      </router-link>
      <router-link v-else to="/account"
        class="inline-block px-4 py-2 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded font-bold hover:opacity-90">
        ⭐ プレミアムにアップグレード
      </router-link>
    </div>

    <!-- ジャンル選択画面 -->
    <GenrePicker
      v-if="!started && (!playQuota || playQuota.unlimited || playQuota.remaining > 0)"
      mode-name="A モード"
      theme-gradient="from-sky-500 to-indigo-600"
      :community-genres="communityGenres"
      @select="onGenreSelected"
      @select-community="onCommunityGenreSelected" />

    <template v-else>
      <CurrentGenreBadge
        :genre="selectedGenre"
        :scope="selectedScope"
        :community-genre-name="selectedCommunityGenreName || undefined"
        @change="changeGenre" />

      <!-- 難易度切替 -->
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
      </div>

      <p class="text-sm text-slate-600">
        記事の末尾から段落が自動で追加されます。答えは 4 択で 1 文字ずつ入力。
        <span class="text-red-600 font-bold">1 文字でも間違えたら即不正解</span>。
      </p>

    <!-- 終了画面 -->
    <div v-if="finished" class="space-y-4">
      <div class="bg-blue-50 border border-blue-200 rounded p-4">
        <div class="text-sm text-blue-900">最終スコア</div>
        <div class="text-3xl font-bold text-blue-700">{{ totalScore }} / {{ 1000 * TOTAL_QUESTIONS }}</div>
      </div>
      <div class="bg-white border border-slate-200 rounded p-4 space-y-2">
        <div v-for="(r, i) in results" :key="i" class="flex justify-between text-sm">
          <span>{{ i + 1 }}. {{ r.title }}</span>
          <span class="text-slate-600">
            {{ r.correct
              ? `${r.revealedCount} 段落で正解`
              : `${r.correctChars}/${r.totalInputChars} 文字` }}
            → {{ r.score }}点
          </span>
        </div>
      </div>
      <ResultShareCard
        title="A モード"
        :subtitle="selectedCommunityGenreName || '通常プレイ'"
        :total-score="totalScore"
        :max-score="1000 * TOTAL_QUESTIONS"
        :results="results"
        theme-gradient="from-sky-500 via-indigo-500 to-purple-600"
        :share-text="shareText" />
      <button @click="restart" class="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">もう一度</button>
    </div>

    <div v-else>
      <div class="text-sm text-slate-500 mb-2 flex justify-between items-center">
        <span>第 {{ currentQ }} 問 / {{ TOTAL_QUESTIONS }} 問</span>
        <span>{{ revealed }} / {{ paragraphs.length }} 段落</span>
      </div>

      <!-- 視覚的プログレスバー (難易度で速度可変) -->
      <div v-if="!answered && hasMore" class="h-1.5 bg-slate-200 rounded overflow-hidden mb-3">
        <div class="h-full bg-blue-500 progress-bar"
          :key="`${revealed}-${selectedDifficulty}`"
          :style="{ animationDuration: `${revealIntervalMs}ms` }"></div>
      </div>

      <div v-if="loading" class="text-slate-500">読み込み中…</div>
      <div v-else-if="error" class="text-red-600">エラー: {{ error }}</div>

      <div v-else-if="article" class="space-y-4">
        <article class="bg-white border border-slate-200 rounded p-4 leading-relaxed text-sm min-h-32 space-y-3 max-h-[50vh] overflow-y-auto">
          <p v-if="revealed < paragraphs.length" class="text-slate-300 text-center">… (前段落は未開示)</p>
          <p v-for="(p, i) in visibleParagraphs" :key="i" class="whitespace-pre-wrap">{{ p }}</p>
        </article>

        <!-- 入力エリア -->
        <div class="bg-white border border-slate-200 rounded p-4 space-y-3">
          <div class="text-xs text-slate-500">答えを 1 文字ずつ選んでください ({{ answerChars.length }} 文字)</div>

          <!-- 現在の入力状態 -->
          <div class="text-2xl font-bold text-center tracking-wider flex flex-wrap justify-center gap-1">
            <template v-for="(c, i) in answerChars" :key="i">
              <span v-if="SKIP_CHAR_RE.test(c)" class="text-slate-400">{{ c }}</span>
              <span v-else-if="i < currentPos" class="text-emerald-600">{{ c }}</span>
              <span v-else-if="i === currentPos && !answered" class="text-blue-600 underline">_</span>
              <span v-else class="text-slate-300">・</span>
            </template>
          </div>

          <!-- 4 択ボタン -->
          <div v-if="!answered && currentChoices.length === 4" class="grid grid-cols-4 gap-2">
            <button
              v-for="opt in currentChoices"
              :key="opt"
              @click="selectChar(opt)"
              class="px-2 py-3 rounded border-2 border-slate-300 bg-white hover:bg-blue-50 hover:border-blue-400 text-xl font-bold transition">
              {{ opt }}
            </button>
          </div>

          <div v-if="!answered" class="flex gap-2 justify-end">
            <button
              @click="revealMore"
              :disabled="!hasMore"
              class="px-3 py-1 bg-slate-200 rounded hover:bg-slate-300 disabled:opacity-50 text-xs">
              すぐ次の段落を見る
            </button>
            <button @click="giveUp" class="px-3 py-1 bg-slate-200 rounded hover:bg-slate-300 text-xs">
              ギブアップ
            </button>
          </div>
        </div>

        <!-- 結果 -->
        <div v-if="answered" class="space-y-3">
          <div class="bg-slate-100 rounded p-3">
            <div class="text-sm">答え: <span class="font-bold">{{ article.title }}</span></div>
            <div v-if="correctFlag" class="text-lg font-bold text-emerald-600">
              正解! {{ results[results.length - 1].revealedCount }} 段落で当てた
            </div>
            <div v-else class="text-lg font-bold text-red-600">
              不正解<span v-if="wrongChoice"> (「{{ wrongChoice }}」を選んだ)</span>
            </div>
            <div v-if="!correctFlag" class="text-sm text-slate-700">
              {{ results[results.length - 1].correctChars }} / {{ results[results.length - 1].totalInputChars }} 文字正解
            </div>
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

<style scoped>
.progress-bar {
  animation-name: shrink;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
  transform-origin: right;
}
@keyframes shrink {
  0% { transform: scaleX(1); }
  100% { transform: scaleX(0); }
}
</style>
