import { ref, watch } from 'vue'
import { normalizeFameTier, type FameTier } from '../fameTier'

const STORAGE_KEY = 'wikiplays-fame-tier'

function load(): FameTier | null {
  try {
    return normalizeFameTier(localStorage.getItem(STORAGE_KEY))
  } catch {
    return null
  }
}

/** 全モード共通の「記事の知名度」選択。モジュールスコープで 1 つだけ持ち、LocalStorage に保存する。 */
const fameTier = ref<FameTier | null>(load())

watch(fameTier, (v) => {
  try {
    if (v == null) localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, String(v))
  } catch {
    // プライベートモード等で保存できなくても動作には影響しない
  }
})

export function useFameTier() {
  return { fameTier }
}
