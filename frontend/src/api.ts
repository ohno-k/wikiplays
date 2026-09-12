import type { ArticleData, Genre, ModeId, PlayMode, Scope } from './types'
import type { FameTier } from './fameTier'

/** サーバーが返す { message } を取り出してエラーにする。 */
async function errorFrom(res: Response, fallback: string): Promise<Error> {
  try {
    const body = await res.json()
    if (body && typeof body.message === 'string') return new Error(body.message)
  } catch {
    // JSON でなければフォールバック
  }
  return new Error(`${fallback} (HTTP ${res.status})`)
}

function authHeaders(token?: string | null, json = false): Record<string, string> {
  const h: Record<string, string> = {}
  if (json) h['Content-Type'] = 'application/json'
  if (token) h['Authorization'] = `Bearer ${token}`
  return h
}

/**
 * B〜E モード用のランダム記事。Free プランの 1 日上限に達していると 429。
 * fameTier (1 = 超メジャー 〜 5 = 超マニアック) を渡すとその知名度帯の記事を優先する。
 */
export async function fetchRandomArticle(
  genre?: Genre | null,
  scope?: Scope | null,
  token?: string | null,
  fameTier?: FameTier | null,
): Promise<ArticleData> {
  const params = new URLSearchParams()
  if (genre) params.set('genre', genre)
  if (scope) params.set('scope', scope)
  if (fameTier != null) params.set('fameTier', String(fameTier))
  params.set('playerId', getPlayerId())
  const res = await fetch(`/api/article/random?${params.toString()}`, { headers: authHeaders(token) })
  if (res.status === 429) throw new QuotaExceededError()
  if (!res.ok) throw await errorFrom(res, '記事取得失敗')
  return res.json()
}

export class QuotaExceededError extends Error {
  constructor() {
    super('今日のプレイ上限に達しました')
    this.name = 'QuotaExceededError'
  }
}

// ===== Game session (A モード / デイリー: サーバー側で進行・採点) =====

export interface GameSlot {
  /** 記号などの入力不要な文字。 */
  skip: string | null
  /** 入力済みなら 1 要素、現在位置なら 4 択 (ライフ使用後は 3 択)、未到達なら null。 */
  options: string[] | null
}

export interface GameQuestionResult {
  title: string
  pageUrl: string
  correct: boolean
  score: number
  revealedCount: number
  correctChars: number
  totalInputChars: number
  wrongChar: string | null
  lifeUsed: boolean
}

export interface GameQuestion {
  paragraphCount: number
  revealed: number
  paragraphs: string[]
  slots: GameSlot[]
  answerLength: number
  pos: number
  lifeUsed: boolean
  missedChar: string | null
  locked: boolean
  answered: boolean
  result: GameQuestionResult | null
}

export interface GameXp {
  xpGained: number
  xpCapped: boolean
  leveledUp: boolean
  xp: number
  level: number
  xpIntoLevel: number
  xpForNextLevel: number
  dailyRemaining: number
  streakDays: number
}

export interface GameSummary {
  results: GameQuestionResult[]
  xp: GameXp | null
  dailyRecorded: boolean | null
}

export interface GameSessionView {
  sessionId: string
  mode: 'a' | 'daily'
  genre: Genre | null
  scope: Scope | null
  communityGenreId: number | null
  dailyChallengeId: number | null
  difficulty: 'relaxed' | 'normal' | 'speed'
  /** 選択した記事の知名度 tier。null は指定なし。 */
  fameTier: FameTier | null
  intervalMs: number
  totalQuestions: number
  index: number
  finished: boolean
  totalScore: number
  maxScore: number
  question: GameQuestion
  summary: GameSummary | null
}

export interface StartGameRequest {
  mode: 'a' | 'daily'
  genre?: Genre | null
  scope?: Scope | null
  communityGenreId?: number | null
  dailyChallengeId?: number | null
  difficulty?: string
  /** 記事の知名度 tier (1〜5)。省略・null は指定なし。 */
  fameTier?: FameTier | null
}

async function gameCall(path: string, body: Record<string, unknown>, token?: string | null): Promise<GameSessionView> {
  const res = await fetch(path, {
    method: 'POST',
    headers: authHeaders(token, true),
    body: JSON.stringify({ ...body, playerId: getPlayerId() }),
  })
  if (res.status === 429) {
    const err = await errorFrom(res, '上限')
    const q = new QuotaExceededError()
    q.message = err.message
    throw q
  }
  if (!res.ok) throw await errorFrom(res, 'ゲーム API 失敗')
  return res.json()
}

export function startGame(req: StartGameRequest, token?: string | null): Promise<GameSessionView> {
  return gameCall('/api/game/start', { ...req }, token)
}

export async function getGame(sessionId: string, token?: string | null): Promise<GameSessionView> {
  const params = new URLSearchParams({ playerId: getPlayerId() })
  const res = await fetch(`/api/game/${sessionId}?${params.toString()}`, { headers: authHeaders(token) })
  if (!res.ok) throw await errorFrom(res, 'セッション取得失敗')
  return res.json()
}

export function revealGame(sessionId: string, token?: string | null): Promise<GameSessionView> {
  return gameCall(`/api/game/${sessionId}/reveal`, {}, token)
}

export function answerGame(sessionId: string, character: string, token?: string | null): Promise<GameSessionView> {
  return gameCall(`/api/game/${sessionId}/answer`, { character }, token)
}

export function giveUpGame(sessionId: string, token?: string | null): Promise<GameSessionView> {
  return gameCall(`/api/game/${sessionId}/giveup`, {}, token)
}

export function nextGame(sessionId: string, token?: string | null): Promise<GameSessionView> {
  return gameCall(`/api/game/${sessionId}/next`, {}, token)
}

export async function fetchDecoys(categories: string[], exclude: string, count = 3): Promise<string[]> {
  const params = new URLSearchParams()
  for (const c of categories) params.append('categories', c)
  params.set('exclude', exclude)
  params.set('count', String(count))
  const res = await fetch(`/api/article/decoys?${params.toString()}`)
  if (!res.ok) throw new Error(`デコイ取得失敗 (HTTP ${res.status})`)
  return res.json()
}

// ===== Daily Challenge =====

export interface DailyChallengeResponse {
  id: number
  date: string  // YYYY-MM-DD
  scope: Scope | null
  genre: Genre | null
  /** 問題数。記事本文はゲームセッション経由でのみ配信される。 */
  questionCount: number
  playerCount: number
  topScore: number
  /** 認証ユーザーが今日のチャレンジを既プレイ済みならその点数、未プレイなら null。 */
  myScore: number | null
}

export interface DailyLeaderboardEntry {
  displayName: string
  score: number
  playedAt: string
  rank: number
}

export async function fetchDailyChallenge(genre?: Genre | null, scope?: Scope | null, token?: string | null): Promise<DailyChallengeResponse> {
  const params = new URLSearchParams()
  if (genre) params.set('genre', genre)
  if (scope) params.set('scope', scope)
  const url = params.toString() ? `/api/daily/today?${params.toString()}` : '/api/daily/today'
  const headers: Record<string, string> = {}
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(url, { headers })
  if (res.status === 401) throw new Error('デイリーチャレンジはログインが必要です')
  if (res.status === 503) throw new Error('今日の問題がまだ準備中です。少し待ってからもう一度試してください。')
  if (!res.ok) throw new Error(`デイリーチャレンジ取得失敗 (HTTP ${res.status})`)
  return res.json()
}

/** 過去のデイリーチャレンジ概要 (プレミアム限定)。 */
export async function fetchDailyChallengeById(id: number, token?: string | null): Promise<DailyChallengeResponse> {
  const res = await fetch(`/api/daily/challenge/${id}`, { headers: authHeaders(token) })
  if (res.status === 401) throw new Error('ログインが必要です')
  if (res.status === 402) throw new Error('過去のデイリーチャレンジはプレミアム限定です')
  if (!res.ok) throw new Error(`チャレンジ取得失敗 (HTTP ${res.status})`)
  return res.json()
}

export async function fetchDailyLeaderboard(challengeId: number, limit = 20): Promise<DailyLeaderboardEntry[]> {
  const res = await fetch(`/api/daily/leaderboard/${challengeId}?limit=${limit}`)
  if (!res.ok) throw new Error(`ランキング取得失敗 (HTTP ${res.status})`)
  return res.json()
}

// ===== Player ID (匿名識別用、ブラウザに保存) =====

const PLAYER_ID_KEY = 'wikiplays_player_id'
const DISPLAY_NAME_KEY = 'wikiplays_display_name'

export function getPlayerId(): string {
  let id = localStorage.getItem(PLAYER_ID_KEY)
  if (!id) {
    id = 'p_' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36)
    localStorage.setItem(PLAYER_ID_KEY, id)
  }
  return id
}

export function getDisplayName(): string {
  return localStorage.getItem(DISPLAY_NAME_KEY) ?? ''
}

export function setDisplayName(name: string): void {
  localStorage.setItem(DISPLAY_NAME_KEY, name)
}

// ===== Community Genres =====

export interface CommunityGenre {
  id: number
  name: string
  emoji: string
  categories: string[]
  creatorName: string
  createdAt: string
  playCount: number
  mine: boolean
}

export async function fetchCommunityGenres(): Promise<CommunityGenre[]> {
  const params = new URLSearchParams()
  params.set('playerId', getPlayerId())
  const res = await fetch(`/api/community-genres?${params.toString()}`)
  if (!res.ok) throw new Error(`一覧取得失敗 (HTTP ${res.status})`)
  return res.json()
}

export async function createCommunityGenre(payload: {
  name: string
  emoji: string
  categories: string[]
  creatorName: string
}, token?: string | null): Promise<CommunityGenre> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch('/api/community-genres', {
    method: 'POST',
    headers,
    body: JSON.stringify({ ...payload, creatorId: getPlayerId() }),
  })
  if (res.status === 401) throw new Error('ログインが必要です')
  if (res.status === 402) throw new Error('プレミアムプランへのアップグレードが必要です')
  if (res.status === 403) throw new Error('禁止語句を含むカテゴリは使えません')
  if (!res.ok) throw new Error(`作成失敗 (HTTP ${res.status})`)
  return res.json()
}

export async function deleteCommunityGenre(id: number): Promise<void> {
  const params = new URLSearchParams({ playerId: getPlayerId() })
  const res = await fetch(`/api/community-genres/${id}?${params.toString()}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`削除失敗 (HTTP ${res.status})`)
}

/**
 * コミュニティジャンルでのプレイ開始を記録する (1 セッションにつき 1 回だけ呼ぶ)。
 * 失敗は無視 (集計用なので致命的ではない)。
 */
export async function recordCommunityGenrePlay(genreId: number, token?: string | null): Promise<void> {
  try {
    const headers: Record<string, string> = {}
    if (token) headers['Authorization'] = `Bearer ${token}`
    await fetch(`/api/community-genres/${genreId}/play`, { method: 'POST', headers })
  } catch {
    // ignore
  }
}

export async function fetchCommunityRandomArticle(
  genreId: number,
  token?: string | null,
  excludeTitles?: string[],
): Promise<ArticleData> {
  const headers: Record<string, string> = {}
  if (token) headers['Authorization'] = `Bearer ${token}`
  let url = `/api/community-genres/${genreId}/random`
  if (excludeTitles && excludeTitles.length > 0) {
    // URL 長を抑えるため直近 30 件までに絞る
    const recent = excludeTitles.slice(-30)
    const params = new URLSearchParams()
    for (const t of recent) params.append('exclude', t)
    url += `?${params.toString()}`
  }
  const res = await fetch(url, { headers })
  if (res.status === 401) throw new Error('ログインが必要です')
  if (res.status === 402) throw new Error('コミュニティジャンルのプレイはプレミアムプラン限定です')
  if (!res.ok) throw new Error(`記事取得失敗 (HTTP ${res.status})`)
  return res.json()
}

// ===== Local Play History (アカウント代わり) =====

export interface PlayRecord {
  date: string                    // ISO 日付
  mode: PlayMode                  // モード種別
  genre: Genre | null
  scope: Scope | null
  communityGenreId?: number
  communityGenreName?: string
  score: number
  maxScore: number
  difficulty?: string             // A モードの難易度
}

const PLAY_HISTORY_KEY = 'wikiplays_play_history'
const HISTORY_LIMIT = 500

export function recordPlay(record: Omit<PlayRecord, 'date'>): void {
  const history = getPlayHistory()
  history.unshift({ ...record, date: new Date().toISOString() })
  if (history.length > HISTORY_LIMIT) history.length = HISTORY_LIMIT
  localStorage.setItem(PLAY_HISTORY_KEY, JSON.stringify(history))
}

export function getPlayHistory(): PlayRecord[] {
  try {
    const raw = localStorage.getItem(PLAY_HISTORY_KEY)
    if (!raw) return []
    return JSON.parse(raw)
  } catch {
    return []
  }
}

export function clearPlayHistory(): void {
  localStorage.removeItem(PLAY_HISTORY_KEY)
}

// ===== Server-side play record + Quota + Leaderboard =====

export interface PlayQuota {
  unlimited: boolean
  played: number
  limit: number
  remaining: number
}

export async function fetchPlayQuota(token?: string | null): Promise<PlayQuota> {
  const params = new URLSearchParams({ playerId: getPlayerId() })
  const headers: Record<string, string> = {}
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(`/api/play/quota?${params.toString()}`, { headers })
  if (!res.ok) throw new Error(`Quota 取得失敗 (HTTP ${res.status})`)
  return res.json()
}

export interface PlayRecordResult {
  id: number
  /** ログイン時のみ。匿名プレイ時は undefined。 */
  xpGained?: number
  xpCapped?: boolean
  leveledUp?: boolean
  xp?: number
  level?: number
  xpIntoLevel?: number
  xpForNextLevel?: number
  dailyRemaining?: number
  streakDays?: number
}

/**
 * クライアント採点モード (B〜E) の結果をサーバーに記録する。
 * A モードとデイリーはサーバー側セッションが自動で記録するので、ここからは送らない。
 */
export async function submitPlayRecord(record: {
  mode: Exclude<ModeId, 'a'>
  genre: Genre | null
  scope: Scope | null
  communityGenreId?: number | null
  score: number
  maxScore: number
  difficulty?: string
}, token?: string | null): Promise<PlayRecordResult | null> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch('/api/play/record', {
    method: 'POST',
    headers,
    body: JSON.stringify({ ...record, playerId: getPlayerId() }),
  })
  if (!res.ok) return null
  try { return await res.json() } catch { return null }
}

export interface LeaderboardRow {
  rank: number
  displayName: string
  totalScore: number
  bestScore: number
  playCount: number
}

export async function fetchLeaderboard(opts: {
  period?: 'today' | 'week' | 'month' | 'all'
  mode?: string
  genre?: Genre | null
  scope?: Scope | null
  communityGenreId?: number | null
  limit?: number
}): Promise<LeaderboardRow[]> {
  const params = new URLSearchParams()
  if (opts.period) params.set('period', opts.period)
  if (opts.mode) params.set('mode', opts.mode)
  if (opts.genre) params.set('genre', opts.genre)
  if (opts.scope) params.set('scope', opts.scope)
  if (opts.communityGenreId != null) params.set('communityGenreId', String(opts.communityGenreId))
  if (opts.limit) params.set('limit', String(opts.limit))
  const res = await fetch(`/api/leaderboard?${params.toString()}`)
  if (!res.ok) throw new Error(`ランキング取得失敗 (HTTP ${res.status})`)
  return res.json()
}
