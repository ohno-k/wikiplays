import { ref, computed, onUnmounted, type Ref } from 'vue'
import {
  startGame, getGame, revealGame, answerGame, giveUpGame, nextGame,
  QuotaExceededError,
  type GameSessionView, type StartGameRequest,
} from '../api'

const STORAGE_PREFIX = 'wikiplays_game_session_'

/**
 * サーバー側ゲームセッション (A モード / デイリー) をブラウザから操作する composable。
 *
 * - 段落の自動開示はクライアントのタイマーが /reveal を叩く (サーバー側でも経過時間で下限を計算するので、
 *   タイマーを止めても得はしない)
 * - 最初の 1 文字を送った時点でサーバーが開示数を固定するため、タイマーは止まる
 */
export function useGameSession(options: { token: Ref<string | null>; storageKey?: string }) {
  const view = ref<GameSessionView | null>(null)
  const loading = ref(false)
  const busy = ref(false)
  const error = ref<string | null>(null)
  const quotaExceeded = ref(false)
  /** プログレスバーをリセットするためのキー (段落が開くたびに変わる)。 */
  const timerKey = ref(0)
  let timer: number | null = null

  const question = computed(() => view.value?.question ?? null)
  const finished = computed(() => view.value?.finished ?? false)
  const hasMore = computed(() => !!question.value && question.value.revealed < question.value.paragraphCount)
  const canReveal = computed(() => !!question.value && !question.value.answered && !question.value.locked && hasMore.value)
  const currentOptions = computed(() => {
    const q = question.value
    if (!q || q.answered) return [] as string[]
    return q.slots[q.pos]?.options ?? []
  })

  function clearTimer() {
    if (timer !== null) { clearTimeout(timer); timer = null }
  }

  function scheduleTimer() {
    clearTimer()
    if (!view.value || !canReveal.value) return
    timerKey.value++
    timer = window.setTimeout(() => { reveal() }, view.value.intervalMs)
  }

  function remember(v: GameSessionView | null) {
    if (!options.storageKey) return
    try {
      if (v && !v.finished) sessionStorage.setItem(STORAGE_PREFIX + options.storageKey, v.sessionId)
      else sessionStorage.removeItem(STORAGE_PREFIX + options.storageKey)
    } catch {
      // sessionStorage が使えない環境では無視
    }
  }

  async function apply(p: Promise<GameSessionView>, opts: { loading?: boolean } = {}): Promise<boolean> {
    if (opts.loading) loading.value = true
    busy.value = true
    error.value = null
    try {
      const v = await p
      view.value = v
      remember(v)
      scheduleTimer()
      return true
    } catch (e) {
      if (e instanceof QuotaExceededError) {
        quotaExceeded.value = true
      }
      error.value = e instanceof Error ? e.message : '通信に失敗しました'
      return false
    } finally {
      busy.value = false
      if (opts.loading) loading.value = false
    }
  }

  async function start(req: StartGameRequest): Promise<boolean> {
    clearTimer()
    view.value = null
    quotaExceeded.value = false
    return apply(startGame(req, options.token.value), { loading: true })
  }

  /** リロード後などに、進行中のセッションがあれば復帰する。 */
  async function resume(): Promise<boolean> {
    if (!options.storageKey) return false
    let id: string | null = null
    try { id = sessionStorage.getItem(STORAGE_PREFIX + options.storageKey) } catch { return false }
    if (!id) return false
    const ok = await apply(getGame(id, options.token.value), { loading: true })
    if (!ok || view.value?.finished) {
      remember(null)
      view.value = ok ? view.value : null
      error.value = null
      return false
    }
    return true
  }

  async function reveal() {
    if (!view.value || !canReveal.value || busy.value) { scheduleTimer(); return }
    await apply(revealGame(view.value.sessionId, options.token.value))
  }

  async function answer(ch: string) {
    if (!view.value || busy.value || !question.value || question.value.answered) return
    await apply(answerGame(view.value.sessionId, ch, options.token.value))
  }

  async function giveUp() {
    if (!view.value || busy.value || !question.value || question.value.answered) return
    await apply(giveUpGame(view.value.sessionId, options.token.value))
  }

  async function next() {
    if (!view.value || busy.value) return
    await apply(nextGame(view.value.sessionId, options.token.value))
  }

  function reset() {
    clearTimer()
    remember(null)
    view.value = null
    error.value = null
    quotaExceeded.value = false
  }

  onUnmounted(clearTimer)

  return {
    view, question, loading, busy, error, quotaExceeded, finished, hasMore, canReveal, currentOptions, timerKey,
    start, resume, reveal, answer, giveUp, next, reset,
  }
}
