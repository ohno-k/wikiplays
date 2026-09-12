/**
 * 記事の主題の「知名度」5 段階。プレイヤーが出題前に選ぶ。
 *
 * サーバー側は各記事に知名度スコア (他言語版数・記事長・別名数から算出) を持ち、
 * 選んだジャンル内でスコア順に 5 等分した帯から出題する。
 * 1 = 最も有名、5 = 最もマニアック。null は指定なし (全体からランダム)。
 */
export type FameTier = 1 | 2 | 3 | 4 | 5

export interface FameTierMeta {
  id: FameTier | null
  name: string
  emoji: string
  description: string
}

export const FAME_TIERS: FameTierMeta[] = [
  { id: null, name: 'おまかせ',     emoji: '🎲', description: '知名度を問わず全体からランダム' },
  { id: 1,    name: '超メジャー',   emoji: '🌟', description: 'ジャンル内で最も有名な記事 (上位 20%)' },
  { id: 2,    name: 'メジャー',     emoji: '⭐', description: 'よく知られた記事' },
  { id: 3,    name: 'ふつう',       emoji: '📗', description: '知っている人は知っている記事' },
  { id: 4,    name: 'マニアック',   emoji: '🔍', description: '詳しい人向けの記事' },
  { id: 5,    name: '超マニアック', emoji: '🧪', description: 'ジャンル内で最も知られていない記事 (下位 20%)' },
]

export function fameTierMeta(id: FameTier | null | undefined): FameTierMeta {
  return FAME_TIERS.find(t => t.id === (id ?? null)) ?? FAME_TIERS[0]
}

/** 不正な値 (範囲外・NaN) は null に丸める。 */
export function normalizeFameTier(v: unknown): FameTier | null {
  const n = typeof v === 'string' ? parseInt(v, 10) : v
  if (typeof n !== 'number' || !Number.isInteger(n) || n < 1 || n > 5) return null
  return n as FameTier
}
