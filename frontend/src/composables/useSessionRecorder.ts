import { ref } from 'vue'
import { recordPlay, submitPlayRecord, type PlayRecordResult } from '../api'
import type { Genre, ModeId, Scope } from '../types'
import { useAuth } from './useAuth'

/**
 * クライアント採点モード (B〜E) のセッション結果を LocalStorage とサーバーに記録し、
 * 獲得 XP を保持する。A モードとデイリーはサーバー側セッションが記録する。
 */
export function useSessionRecorder(mode: Exclude<ModeId, 'a'>) {
  const { token, refresh } = useAuth()
  const xpResult = ref<PlayRecordResult | null>(null)

  async function record(r: { genre: Genre | null; scope: Scope | null; score: number; maxScore: number }) {
    recordPlay({
      mode,
      genre: r.genre,
      scope: r.genre ? r.scope : null,
      score: r.score,
      maxScore: r.maxScore,
    })
    try {
      const result = await submitPlayRecord({
        mode,
        genre: r.genre,
        scope: r.genre ? r.scope : null,
        score: r.score,
        maxScore: r.maxScore,
      }, token.value)
      xpResult.value = result
      if (result && result.xpGained != null) refresh()
    } catch {
      xpResult.value = null
    }
  }

  function reset() {
    xpResult.value = null
  }

  return { xpResult, record, reset }
}
