import { ref, type Ref } from 'vue'
import type { ArticleData, Genre, Scope } from '../types'
import type { FameTier } from '../fameTier'
import { fetchRandomArticle, fetchCommunityRandomArticle, QuotaExceededError } from '../api'

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
  /** 記事の知名度 tier (1〜5)。null は指定なし。コミュニティジャンルでは使われない。 */
  fameTier?: Ref<FameTier | null>
  /** ログイン中の JWT (Premium 検証・Free 上限判定用)。 */
  token?: Ref<string | null>
}

export function useArticleQueue<T>(options: ArticleQueueOptions<T>) {
  const cache = ref<T | null>(null) as { value: T | null }
  const prefetching = ref(false)
  const maxAttempts = options.maxAttempts ?? 20
  // セッション内で出題済みのタイトルを記録し、連続して同じ問題が出ないようにする。
  // プールに記事が少ない (ジャンル×scope バケットに数件) ときの重複出題対策。
  const seenTitles = new Set<string>()
  // 直前に出題したタイトル。プールが小さくてもこれだけは絶対に back-to-back で出さない。
  let lastTitle: string | null = null

  async function findOne(): Promise<T> {
    let attempts = 0
    let lastFetchError: unknown = null
    let prepareRejects = 0
    let duplicateSkips = 0
    let lastTitleSkips = 0
    // 重複スキップで諦めた候補 (プール枯渇時のフォールバック用)
    let duplicateFallback: T | null = null
    // この findOne 内で取得したが prepare に弾かれたタイトル。
    // サーバーが同じ記事を返し続けないよう、後続リクエストの exclude に混ぜる。
    // (seenTitles には出題確定時しか入らないため、prepare 落ちの記事はサーバー側で除外されない)
    const localRejects = new Set<string>()
    while (attempts < maxAttempts) {
      try {
        const communityId = options.communityGenreId?.value
        const excludeTitles = communityId != null
          ? Array.from(new Set([...seenTitles, ...localRejects]))
          : undefined
        const a = communityId != null
          ? await fetchCommunityRandomArticle(communityId, options.token?.value, excludeTitles)
          : await fetchRandomArticle(options.genre?.value, options.scope?.value, options.token?.value, options.fameTier?.value)
        // 直前の記事と同一なら最大試行の半分まで強制スキップ (back-to-back 防止)
        if (a.title === lastTitle && lastTitleSkips < Math.floor(maxAttempts / 2)) {
          lastTitleSkips++
          attempts++
          continue
        }
        // 既出記事は最大 maxAttempts/2 回までスキップ。
        // それを超えても重複が続くならプールが枯渇しているので諦めて出す。
        if (seenTitles.has(a.title) && duplicateSkips < Math.floor(maxAttempts / 2)) {
          // フォールバック用に prepare 通過の候補を保存 (まだ無ければ)
          if (duplicateFallback === null) {
            const p = await options.prepare(a)
            if (p !== null && p !== undefined) duplicateFallback = p
          }
          duplicateSkips++
          attempts++
          continue
        }
        const prepared = await options.prepare(a)
        if (prepared !== null && prepared !== undefined) {
          seenTitles.add(a.title)
          lastTitle = a.title
          return prepared
        }
        prepareRejects++
        localRejects.add(a.title)
      } catch (e) {
        // Free プランの上限に達したら再試行しても無駄なので即座に伝える
        if (e instanceof QuotaExceededError) throw e
        lastFetchError = e
        console.warn('[useArticleQueue] fetch failed:', e)
      }
      attempts++
    }
    // 試行回数を使い切ったが、過去にスキップした重複候補があれば最終手段としてそれを返す。
    // プールが枯渇しているコミュニティジャンルでもエラーで止まらないようにする。
    if (duplicateFallback !== null) {
      console.warn('[useArticleQueue] returning duplicate fallback (pool exhausted)')
      return duplicateFallback
    }
    if (lastFetchError && prepareRejects === 0) {
      throw new Error(`サーバー接続に失敗しました: ${(lastFetchError as Error).message ?? lastFetchError}`)
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
    seenTitles.clear()
    lastTitle = null
  }

  return { pull, prefetch, reset, prefetching }
}
