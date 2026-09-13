/**
 * 記事の主題の「知名度」5 段階。プレイヤーが出題前に選ぶ。
 *
 * サーバー側は各記事に知名度スコア (Wikipedia での 1 日あたり閲覧数が基準) を持ち、
 * 固定の境界で 5 段階に分ける。ジャンル内の相対順位ではなく絶対基準なので、
 * 「常識レベル」はどのジャンルでも誰でも知っている題材になる。
 * 1 = 常識レベル、5 = 超マニアック。null は指定なし (全体からランダム)。
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
  { id: 1,    name: '常識レベル',   emoji: '🌟', description: '教科書やニュースに出てくる、誰でも知っている題材' },
  { id: 2,    name: 'メジャー',     emoji: '⭐', description: 'よく知られた題材' },
  { id: 3,    name: 'ふつう',       emoji: '📗', description: '知っている人は知っている題材' },
  { id: 4,    name: 'マニアック',   emoji: '🔍', description: '詳しい人向けの題材' },
  { id: 5,    name: '超マニアック', emoji: '🧪', description: 'ほとんど知られていない題材' },
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
