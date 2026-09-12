export interface Section {
  title: string
  level: number
}

export interface ArticleData {
  title: string
  introExtract: string
  fullExtract: string
  sections: Section[]
  imageUrls: string[]
  infobox: Record<string, string>
  categories: string[]
  languageLinkCount: number
  recentPageViews: number | null
  articleLength: number
  pageUrl: string
  extractedYear: number | null
  extractedYearKind: YearKind | null
  /** この記事へのリダイレクト名 (別名・略称)。古いキャッシュでは欠けていることがある。 */
  aliases?: string[]
}

export type YearKind =
  | 'birth'
  | 'death'
  | 'founded'
  | 'established'
  | 'opened'
  | 'completed'
  | 'released'
  | 'occurred'

export const YEAR_KIND_LABELS: Record<YearKind, string> = {
  birth: '生年',
  death: '没年',
  founded: '設立年',
  established: '成立年',
  opened: '開業年',
  completed: '竣工年',
  released: '発表年',
  occurred: '発生年',
}

export type ModeId = 'a' | 'b' | 'c' | 'd' | 'e'

export type ModeTheme = 'blue' | 'purple' | 'emerald' | 'amber' | 'rose'

export interface ModeMeta {
  id: ModeId
  name: string
  shortName: string
  description: string
  emoji: string
  theme: ModeTheme
  /** タイトルカードの装飾用のグラデーション。tailwind の "from-X to-Y" 形式 */
  gradient: string
  tagline: string
}

export const MODES: ModeMeta[] = [
  {
    id: 'a',
    name: 'じわじわ開示',
    shortName: 'A モード',
    tagline: 'メインモード・早押し',
    description: '記事の末尾から段落が少しずつ開示。早く気づくほど高得点。答えは 4 択で 1 文字ずつ。',
    emoji: '⏱️',
    theme: 'blue',
    gradient: 'from-sky-500 to-indigo-600',
  },
  {
    id: 'b',
    name: 'ヒントカード',
    shortName: 'B モード',
    tagline: 'カードを引いて推理',
    description: '13 種のヒントカードから好きなものを開く。安いカードで当てるほど高得点。',
    emoji: '🃏',
    theme: 'purple',
    gradient: 'from-fuchsia-500 to-purple-600',
  },
  {
    id: 'c',
    name: '年代あて',
    shortName: 'C モード',
    tagline: '何年のできごと?',
    description: '記事の主題が何年か、ざっくり → ズームの 2 段階スライダーで当てる。近いほど高得点。',
    emoji: '🎯',
    theme: 'emerald',
    gradient: 'from-emerald-500 to-teal-600',
  },
  {
    id: 'd',
    name: '4 択クイズ',
    shortName: 'D モード',
    tagline: 'サクッと遊べる',
    description: '同じ分野の 4 つの候補から正解の記事を選ぶ。ヒントを開くか、勘で答えるか。',
    emoji: '🔍',
    theme: 'amber',
    gradient: 'from-amber-500 to-orange-600',
  },
  {
    id: 'e',
    name: '数字あて',
    shortName: 'E モード',
    tagline: '記事の規模を予測',
    description: 'タイトルだけを見て、記事のバイト数・他言語版の数・節数・画像数を予測する。',
    emoji: '🔄',
    theme: 'rose',
    gradient: 'from-rose-500 to-pink-600',
  },
]

/** 全モード (デイリー含む) の識別子。 */
export type PlayMode = ModeId | 'daily'

export const PLAY_MODE_LABELS: Record<PlayMode, string> = {
  a: 'A モード',
  b: 'B モード',
  c: 'C モード',
  d: 'D モード',
  e: 'E モード',
  daily: 'デイリー',
}

export function getModeById(id: ModeId): ModeMeta | undefined {
  return MODES.find(m => m.id === id)
}

export type Scope = 'jp' | 'world'
export type Genre =
  | 'rail'
  | 'history'
  | 'geography'
  | 'science'
  | 'biology'
  | 'art'
  | 'literature'
  | 'food'
  | 'sports'
  | 'music'
  | 'movie'
  | 'anime'
  | 'astronomy'
  | 'architecture'
  | 'mythology'
  | 'language'
  | 'vehicle'
  | 'plant'
  | 'paleontology'
  | 'computer'

export interface GenreMeta {
  id: Genre
  name: string
  emoji: string
  /** どのスコープで遊べるか。 */
  scopes: Scope[]
}

export const GENRES: GenreMeta[] = [
  { id: 'rail',         name: '鉄道',           emoji: '🚆', scopes: ['jp', 'world'] },
  { id: 'history',      name: '歴史',           emoji: '🏯', scopes: ['jp', 'world'] },
  { id: 'geography',    name: '地理',           emoji: '🗾', scopes: ['jp', 'world'] },
  { id: 'science',      name: '科学',           emoji: '🔬', scopes: ['jp', 'world'] },
  { id: 'biology',      name: '生物',           emoji: '🦁', scopes: ['jp', 'world'] },
  { id: 'plant',        name: '植物',           emoji: '🌸', scopes: ['jp', 'world'] },
  { id: 'paleontology', name: '古生物',         emoji: '🦖', scopes: ['jp', 'world'] },
  { id: 'astronomy',    name: '天体',           emoji: '🌌', scopes: ['jp', 'world'] },
  { id: 'art',          name: '芸術',           emoji: '🎨', scopes: ['jp', 'world'] },
  { id: 'literature',   name: '文学',           emoji: '📚', scopes: ['jp', 'world'] },
  { id: 'music',        name: '音楽',           emoji: '🎵', scopes: ['jp', 'world'] },
  { id: 'movie',        name: '映画',           emoji: '🎬', scopes: ['jp', 'world'] },
  { id: 'anime',        name: 'アニメ・漫画',   emoji: '📺', scopes: ['jp', 'world'] },
  { id: 'sports',       name: 'スポーツ',       emoji: '⚽', scopes: ['jp', 'world'] },
  { id: 'mythology',    name: '神話',           emoji: '🐉', scopes: ['jp', 'world'] },
  { id: 'architecture', name: '建築',           emoji: '🏛️', scopes: ['jp', 'world'] },
  { id: 'vehicle',      name: '乗り物',         emoji: '✈️', scopes: ['jp', 'world'] },
  { id: 'language',     name: '言語',           emoji: '💬', scopes: ['jp', 'world'] },
  { id: 'computer',     name: 'IT・コンピュータ', emoji: '💻', scopes: ['jp', 'world'] },
  { id: 'food',         name: '食',             emoji: '🍱', scopes: ['jp', 'world'] },
]

export interface GenreSelection {
  /** ジャンル ID。null は「総合 (全ランダム)」 */
  genre: Genre | null
  scope: Scope
}

export const SCOPE_LABELS: Record<Scope, { name: string; emoji: string }> = {
  jp:    { name: '日本',  emoji: '🇯🇵' },
  world: { name: '世界',  emoji: '🌍' },
}
