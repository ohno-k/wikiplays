/**
 * マスク表示に使う記号。`?` を使うと文末判定 (splitSentences) と衝突するので避ける。
 */
export const MASK_TOKEN = '■■■■'
const SHORT_MASK = '■■'

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 記事タイトルおよび関連する手がかり (読み仮名・別名・英字名) を全てマスクする。
 *
 * Wikipedia の冒頭は典型的に
 *   「タイトル」（よみがな、英: English Name、別名）は、〜
 * の形をとり、タイトル本体を伏せても括弧書きで読み仮名が漏れる。
 * そこでマスクトークン直後に来る括弧書きも一緒に消す。
 */
export function maskTitle(text: string, title: string): string {
  if (!text || !title) return text

  // 1. タイトル本体
  let out = text.replace(new RegExp(escapeRegex(title), 'g'), MASK_TOKEN)

  // 2. 「タイトル (曖昧さ回避)」の括弧前部分
  const paren = title.split(/[（(]/)[0].trim()
  if (paren && paren !== title) {
    out = out.replace(new RegExp(escapeRegex(paren), 'g'), MASK_TOKEN)
  }

  // 3. マスクトークン直後の括弧書きを丸ごと消す
  //    例: 「■■■■」（はるのゆめ、英: ...） → 「■■■■」
  //    例: ■■■■（よみがな） → ■■■■
  const esc = escapeRegex(MASK_TOKEN)
  // 引用記号 + マスク + 引用記号 + 括弧書き
  out = out.replace(
    new RegExp(`([「『]?)${esc}([」』]?)\\s*[（(][^（()）]{0,80}[）)]`, 'g'),
    `$1${MASK_TOKEN}$2`
  )

  // 4. マスクトークンの直前にある記号付き別名 (英字括弧書きや別名追記) も消す
  //    例: ■■■■, ■■■■（英: Title） は (3) で対応済み
  return out
}

const ERA_NAMES = [
  '明治', '大正', '昭和', '平成', '令和',
  '慶応', '元治', '文久', '万延', '安政', '嘉永', '弘化', '天保', '文政', '文化',
  '寛政', '天明', '安永', '明和', '宝暦', '宝永', '元禄', '貞享', '天和', '延宝',
  '寛文', '寛永', '元和', '慶長', '天正', '永禄', '弘治', '天文', '享禄', '大永',
  '永正', '文亀', '明応',
]

/**
 * 西暦・年代・世紀・元号などの年表記をすべてマスクする。
 * 月日 (3月14日 等) は年情報を含まないため残す。
 */
export function maskYears(text: string): string {
  if (!text) return text
  let out = text
  out = out.replace(/紀元前\d+年/g, MASK_TOKEN + '年')
  out = out.replace(/紀元前\d+/g, '紀元前' + SHORT_MASK)
  out = out.replace(/\d+年代/g, MASK_TOKEN + '年代')
  out = out.replace(/\d+世紀/g, SHORT_MASK + '世紀')
  out = out.replace(/[一二三四五六七八九十百]+世紀/g, SHORT_MASK + '世紀')
  out = out.replace(/\d{1,4}年/g, MASK_TOKEN + '年')
  out = out.replace(/[一二三四五六七八九十百千]+年/g, MASK_TOKEN + '年')
  const eraPat = ERA_NAMES.join('|')
  out = out.replace(new RegExp(`(${eraPat})(\\d+|元)年`, 'g'), MASK_TOKEN)
  out = out.replace(/\d{4}[-/]\d{1,2}[-/]\d{1,2}/g, MASK_TOKEN)
  return out
}

/**
 * 記事の最初の文を取り出す。
 */
export function firstSentence(text: string): string {
  const m = text.match(/^[^。．！？]*[。．！？]/)
  return m ? m[0] : text.slice(0, 100)
}

/**
 * 記事冒頭を文単位に分割する。
 * 注意: マスクを先にかけると ? が誤判定されるので、必ず分割→マスクの順で使うこと。
 */
export function splitSentences(text: string): string[] {
  return text
    .replace(/\n+/g, ' ')
    .split(/(?<=[。．！？])/)
    .map(s => s.trim())
    .filter(s => s.length > 0)
}

/** 末尾に来るメタセクション以降を切り捨てる。 */
const META_SECTION_HEADINGS = [
  '関連項目', '参考文献', '外部リンク', '脚注', '注釈', '出典',
  'ギャラリー', '関連書籍', '関連作品', '参考資料', '注'
]

export function trimMetaSections(text: string): string {
  if (!text) return text
  let cutAt = text.length
  for (const heading of META_SECTION_HEADINGS) {
    // 改行で囲まれた見出し行を探す
    const re = new RegExp(`\\n${escapeRegex(heading)}\\s*\\n`)
    const m = text.match(re)
    if (m && m.index !== undefined && m.index < cutAt) {
      cutAt = m.index
    }
  }
  return text.substring(0, cutAt)
}

/**
 * 記事本文を段落単位に分割し、見出し行 (sections と一致するもの) を除外する。
 * `sections` は ArticleData.sections の title 配列を渡す。
 */
export function splitParagraphs(fullText: string, headingTitles: string[] = []): string[] {
  if (!fullText) return []
  const trimmed = trimMetaSections(fullText)
  const headingSet = new Set(headingTitles.map(h => h.trim()))
  return trimmed
    .split(/\n{2,}/)
    .map(p => p.trim())
    .filter(p => {
      if (p.length === 0) return false
      if (headingSet.has(p)) return false      // 目次にある見出し
      if (p.length < 15 && !/[。．！？]/.test(p)) return false // 句点を含まない短い行 = ほぼ見出し
      return true
    })
}
