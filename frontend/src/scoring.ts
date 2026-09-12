/**
 * 正解判定の表記正規化。
 * 全角・半角、記号、空白、英大小文字、括弧書きを揃える。
 */
export function normalizeAnswer(s: string): string {
  if (!s) return ''
  return s
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[\s　]+/g, '')
    .replace(/[「」『』"'`、。・,.!?！？]/g, '')
    .replace(/[\(（][^）)]*[\)）]/g, '') // 括弧書きを落とす
    .trim()
}

/**
 * 解答が正解と一致しているかを判定する。
 * Wikipedia のリダイレクト名 (別名・略称・旧表記) も正解として受け付ける。
 */
export function isCorrect(answer: string, title: string, aliases: string[] = []): boolean {
  const a = normalizeAnswer(answer)
  if (!a) return false
  const candidates = [title, ...aliases]
  return candidates.some(c => {
    const t = normalizeAnswer(c)
    return !!t && a === t
  })
}

/** レベルに応じた称号。 */
export function levelTitle(level: number): string {
  if (level >= 40) return '生き字引'
  if (level >= 30) return '博士'
  if (level >= 20) return '司書長'
  if (level >= 15) return '編集者'
  if (level >= 10) return '研究員'
  if (level >= 5) return '読書家'
  if (level >= 2) return '見習い'
  return '新人'
}

/** スコアを絵文字で表示する (Wordle 風)。 */
export function scoreEmoji(score: number, max: number): string {
  const ratio = score / max
  if (ratio >= 0.9) return '🟩'
  if (ratio >= 0.7) return '🟨'
  if (ratio >= 0.4) return '🟧'
  if (ratio > 0) return '🟥'
  return '⬛'
}
