import { describe, it, expect } from 'vitest'
import { isCorrect, normalizeAnswer, scoreEmoji, levelTitle } from './scoring'

describe('isCorrect', () => {
  it('normalizes width, case, spaces and punctuation', () => {
    expect(isCorrect('ｔｏｋｙｏ tower', 'Tokyo Tower')).toBe(true)
    expect(isCorrect('東京タワー', '東京タワー (電波塔)')).toBe(true)
    expect(isCorrect('', '東京タワー')).toBe(false)
  })

  it('accepts redirect aliases', () => {
    expect(isCorrect('ジブリ', 'スタジオジブリ', ['ジブリ', 'Studio Ghibli'])).toBe(true)
    expect(isCorrect('studio ghibli', 'スタジオジブリ', ['ジブリ', 'Studio Ghibli'])).toBe(true)
    expect(isCorrect('ジブリ', 'スタジオジブリ')).toBe(false)
  })

  it('normalizeAnswer drops parentheses', () => {
    expect(normalizeAnswer('東京 (映画)')).toBe('東京')
  })
})

describe('helpers', () => {
  it('scoreEmoji buckets', () => {
    expect(scoreEmoji(1000, 1000)).toBe('🟩')
    expect(scoreEmoji(0, 1000)).toBe('⬛')
  })
  it('levelTitle is monotonic', () => {
    expect(levelTitle(1)).toBe('新人')
    expect(levelTitle(10)).toBe('研究員')
    expect(levelTitle(99)).toBe('生き字引')
  })
})
