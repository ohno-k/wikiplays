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

/** 解答が正解と一致しているかを判定する。 */
export function isCorrect(answer: string, title: string): boolean {
  const a = normalizeAnswer(answer)
  const t = normalizeAnswer(title)
  if (!a || !t) return false
  return a === t
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
