import type { ArticleData, Genre, Scope } from './types'

export async function fetchRandomArticle(genre?: Genre | null, scope?: Scope | null): Promise<ArticleData> {
  const params = new URLSearchParams()
  if (genre) params.set('genre', genre)
  if (scope) params.set('scope', scope)
  const url = params.toString() ? `/api/article/random?${params.toString()}` : '/api/article/random'
  const res = await fetch(url)
  if (!res.ok) throw new Error(`記事取得失敗 (HTTP ${res.status})`)
  return res.json()
}

export async function fetchArticleByTitle(title: string): Promise<ArticleData> {
  const res = await fetch(`/api/article/${encodeURIComponent(title)}`)
  if (!res.ok) throw new Error(`記事取得失敗 (HTTP ${res.status})`)
  return res.json()
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
  articles: ArticleData[]
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
  if (!res.ok) throw new Error(`デイリーチャレンジ取得失敗 (HTTP ${res.status})`)
  return res.json()
}

export async function submitDailyScore(payload: {
  dailyChallengeId: number
  score: number
}, token?: string | null): Promise<boolean> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch('/api/daily/submit', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  })
  return res.ok
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

export async function fetchCommunityRandomArticle(genreId: number, token?: string | null): Promise<ArticleData> {
  const headers: Record<string, string> = {}
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(`/api/community-genres/${genreId}/random`, { headers })
  if (res.status === 401) throw new Error('ログインが必要です')
  if (res.status === 402) throw new Error('コミュニティジャンルのプレイはプレミアムプラン限定です')
  if (!res.ok) throw new Error(`記事取得失敗 (HTTP ${res.status})`)
  return res.json()
}

// ===== Local Play History (アカウント代わり) =====

export interface PlayRecord {
  date: string                    // ISO 日付
  mode: 'a' | 'daily'             // モード種別
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

export async function submitPlayRecord(record: {
  mode: 'a' | 'daily'
  genre: Genre | null
  scope: Scope | null
  communityGenreId?: number | null
  score: number
  maxScore: number
  difficulty?: string
}, token?: string | null): Promise<void> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  await fetch('/api/play/record', {
    method: 'POST',
    headers,
    body: JSON.stringify({ ...record, playerId: getPlayerId() }),
  })
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
