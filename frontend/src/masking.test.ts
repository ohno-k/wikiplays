import { describe, it, expect } from 'vitest'
import { maskTitle, maskYears, splitParagraphs, trimMetaSections, MASK_TOKEN } from './masking'

describe('maskTitle', () => {
  it('masks the title and the reading in parentheses', () => {
    const out = maskTitle('東京タワー（とうきょうタワー、英: Tokyo Tower）は、東京都港区にある電波塔である。', '東京タワー')
    expect(out).not.toContain('東京タワー')
    expect(out).not.toContain('とうきょうタワー')
    expect(out.startsWith(MASK_TOKEN + 'は、')).toBe(true)
  })

  it('tolerates a space between family and given name', () => {
    const out = maskTitle('相原 信行は体操選手。相原体操クラブを設立。', '相原信行')
    expect(out).not.toContain('相原')
    expect(out).not.toContain('信行')
  })

  it('masks aliases of two or more characters only', () => {
    const out = maskTitle('通称ジブリ。略称は「ジ」。', 'スタジオジブリ', ['ジブリ', 'ジ'])
    expect(out).not.toContain('ジブリ')
    expect(out).toContain('「ジ」')
  })

  it('masks the core of a disambiguated title', () => {
    expect(maskTitle('オーロラは極地で見られる。', 'オーロラ (現象)')).not.toContain('オーロラ')
  })
})

describe('maskYears', () => {
  it('masks western years, eras and centuries but keeps month/day', () => {
    const out = maskYears('1958年12月23日に完成し、昭和33年のことである。20世紀の建築。')
    expect(out).not.toContain('1958')
    expect(out).not.toContain('昭和33年')
    expect(out).not.toContain('20世紀')
    expect(out).toContain('12月23日')
  })
})

describe('splitParagraphs', () => {
  it('drops headings and trailing meta sections', () => {
    const text = '冒頭の段落です。\n\n\n歴史\n\n\n歴史の段落です。\n\n\n脚注\n\n\n[1] 出典です。'
    expect(splitParagraphs(text, ['歴史', '脚注'])).toEqual(['冒頭の段落です。', '歴史の段落です。'])
    expect(trimMetaSections('本文。\n関連項目\n外部')).toBe('本文。')
  })
})
