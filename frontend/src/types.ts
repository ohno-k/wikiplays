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
    name: '段階開示型',
    shortName: 'A モード',
    tagline: '早押し・1 文字ずつ',
    description: '記事末尾から 1 段落ずつ自動開示。4 択で 1 文字ずつ答える。',
    emoji: '⏱️',
    theme: 'blue',
    gradient: 'from-sky-500 to-indigo-600',
  },
  {
    id: 'b',
    name: '手がかり選択型',
    shortName: 'B モード',
    tagline: 'カードを引いて推理',
    description: '13 種のヒントカードから好きなものを選んで開示。安く当てた人ほど高得点。',
    emoji: '🃏',
    theme: 'purple',
    gradient: 'from-fuchsia-500 to-purple-600',
  },
  {
    id: 'c',
    name: '座標推定型',
    shortName: 'C モード',
    tagline: '年代を当てる',
    description: '記事の主題が何年か、粗いスライダー → ズームの 2 段階で推定。連続値スコア。',
    emoji: '🎯',
    theme: 'emerald',
    gradient: 'from-emerald-500 to-teal-600',
  },
  {
    id: 'd',
    name: '消去法型',
    shortName: 'D モード',
    tagline: '4 択で当てる',
    description: '同分野の 4 択から正解を選択。ヒントを取るか答えるかのジレンマ。',
    emoji: '🔍',
    theme: 'amber',
    gradient: 'from-amber-500 to-orange-600',
  },
  {
    id: 'e',
    name: '逆引き型',
    shortName: 'E モード',
    tagline: '記事の数値を予測',
    description: '記事タイトルだけ提示。文字数・言語数・節数・画像数を予測する。',
    emoji: '🔄',
    theme: 'rose',
    gradient: 'from-rose-500 to-pink-600',
  },
]

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
