import { ref, type Ref } from 'vue'
import type { ArticleData, Genre, Scope } from '../types'
import { fetchRandomArticle, fetchCommunityRandomArticle } from '../api'

/**
 * 各モード用に「次の問題」を裏で 1 件先読みするキュー。
 *
 * 使い方:
 *   const genre = ref<Genre | null>(null)
 *   const scope = ref<Scope>('jp')
 *   const queue = useArticleQueue<MyModeData>({
 *     genre, scope,
 *     prepare: (a) => {
 *       if (!ok) return null
 *       return { article: a, ...derived }
 *     }
 *   })
 */

export interface ArticleQueueOptions<T> {
  prepare: (a: ArticleData) => Promise<T | null> | T | null
  maxAttempts?: number
  /** 出題ジャンル (null は総合 / 全ランダム)。 */
  genre?: Ref<Genre | null>
  /** スコープ (jp / world)。genre が null なら使われない。 */
  scope?: Ref<Scope>
  /** コミュニティジャンル ID。指定されたら通常ジャンルより優先。 */
  communityGenreId?: Ref<number | null>
}

export function useArticleQueue<T>(options: ArticleQueueOptions<T>) {
  const cache = ref<T | null>(null) as { value: T | null }
  const prefetching = ref(false)
  const maxAttempts = options.maxAttempts ?? 20

  async function findOne(): Promise<T> {
    let attempts = 0
    while (attempts < maxAttempts) {
      try {
        const communityId = options.communityGenreId?.value
        const a = communityId != null
          ? await fetchCommunityRandomArticle(communityId)
          : await fetchRandomArticle(options.genre?.value, options.scope?.value)
        const prepared = await options.prepare(a)
        if (prepared !== null && prepared !== undefined) {
          return prepared
        }
      } catch {
        // fetch 失敗は無視して次へ
      }
      attempts++
    }
    throw new Error('適切な記事が見つかりませんでした')
  }

  async function prefetch() {
    if (prefetching.value || cache.value !== null) return
    prefetching.value = true
    try {
      cache.value = await findOne()
    } catch {
      // 先読み失敗時は次の pull で再試行
    } finally {
      prefetching.value = false
    }
  }

  async function pull(): Promise<T> {
    let item: T
    if (cache.value !== null) {
      item = cache.value
      cache.value = null
    } else {
      item = await findOne()
    }
    prefetch()
    return item
  }

  function reset() {
    cache.value = null
  }

  return { pull, prefetch, reset, prefetching }
}
