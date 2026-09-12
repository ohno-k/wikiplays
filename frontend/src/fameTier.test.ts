import { describe, it, expect } from 'vitest'
import { FAME_TIERS, fameTierMeta, normalizeFameTier } from './fameTier'

describe('fameTier', () => {
  it('has おまかせ plus 5 tiers', () => {
    expect(FAME_TIERS.map(t => t.id)).toEqual([null, 1, 2, 3, 4, 5])
  })

  it('normalizes strings and rejects out-of-range values', () => {
    expect(normalizeFameTier('3')).toBe(3)
    expect(normalizeFameTier(5)).toBe(5)
    expect(normalizeFameTier(0)).toBeNull()
    expect(normalizeFameTier(6)).toBeNull()
    expect(normalizeFameTier('abc')).toBeNull()
    expect(normalizeFameTier(null)).toBeNull()
    expect(normalizeFameTier(undefined)).toBeNull()
    expect(normalizeFameTier(2.5)).toBeNull()
  })

  it('falls back to おまかせ for unknown ids', () => {
    expect(fameTierMeta(null).name).toBe('おまかせ')
    expect(fameTierMeta(undefined).id).toBeNull()
    expect(fameTierMeta(1).name).toBe('超メジャー')
  })
})
