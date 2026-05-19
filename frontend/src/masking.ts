/**
 * マスク表示に使う記号。`?` を使うと文末判定 (splitSentences) と衝突するので避ける。
 */
export const MASK_TOKEN = '■■■■'
const SHORT_MASK = '■■'

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * タイトルの各文字間に「最大 1 個の空白 (半角/全角)」を許容する正規表現を作る。
 * Wikipedia は日本人名で「相原 信行」のように姓と名の間に半角スペースを入れる
 * 慣習があるため、タイトル「相原信行」のままだと本文側でマッチしないことへの対策。
 * i フラグで大文字小文字差 (R2BEAT vs R2Beat vs r2beat) も吸収する。
 */
function buildTolerantTitleRegex(title: string): RegExp {
  const chars = Array.from(title)
  if (chars.length === 0) return /(?!)/g
  const pattern = chars.map(escapeRegex).join('[ 　]?')
  return new RegExp(pattern, 'gi')
}

/**
 * 本文中で「タイトルの前半 + 空白 + タイトルの後半」の形が見つかれば、
 * タイトルを人名 (姓・名) と判定し、その分割位置を返す。
 * 例: title="相原信行", text 内に「相原 信行」があれば ["相原", "信行"] を返す。
 */
function detectPersonNameParts(text: string, title: string): string[] | null {
  const chars = Array.from(title)
  if (chars.length < 3) return null
  for (let i = 1; i < chars.length; i++) {
    const left = chars.slice(0, i).join('')
    const right = chars.slice(i).join('')
    // 姓・名はそれぞれ 1 文字以上、片方が 1 文字だけの場合は誤検出リスクが高いので除外
    if (left.length < 2 || right.length < 2) continue
    const pattern = escapeRegex(left) + '[ 　]+' + escapeRegex(right)
    if (new RegExp(pattern, 'i').test(text)) {
      return [left, right]
    }
  }
  return null
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

  // 1. タイトル本体 (文字間の任意空白を許容)
  //    例: title="相原信行" でも本文「相原 信行」にマッチする
  let out = text.replace(buildTolerantTitleRegex(title), MASK_TOKEN)

  // 2. 人名分割: 本文中で「○○ ○○」と空白挟みで現れていたら姓と名を個別マスク
  //    "相原" 単独出現 (相原体操クラブ等) や "信行" 単独出現も伏せる
  const parts = detectPersonNameParts(text, title)
  if (parts) {
    for (const p of parts) {
      out = out.replace(new RegExp(escapeRegex(p), 'gi'), MASK_TOKEN)
    }
  }

  // 3. 「タイトル (曖昧さ回避)」の括弧前部分
  const paren = title.split(/[（(]/)[0].trim()
  if (paren && paren !== title && paren.length >= 2) {
    out = out.replace(buildTolerantTitleRegex(paren), MASK_TOKEN)
  }

  // 4. マスクトークン直後の括弧書きを丸ごと消す
  //    例: 「■■■■」（はるのゆめ、英: ...） → 「■■■■」
  //    例: ■■■■（よみがな） → ■■■■
  //    マスクが連続している場合 (■■■■ ■■■■) もまとめて 1 括弧扱いにする
  const esc = escapeRegex(MASK_TOKEN)
  out = out.replace(
    new RegExp(`([「『]?)(?:${esc}[ 　]?)+([」』]?)\\s*[（(][^（()）]{0,80}[）)]`, 'g'),
    `$1${MASK_TOKEN}$2`
  )

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
