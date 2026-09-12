<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import { GENRES, SCOPE_LABELS, type Genre, type Scope } from '../types'

function label(entry: ArchiveEntry): string {
  if (!entry.genre) return '🎲 総合'
  const meta = GENRES.find(g => g.id === (entry.genre as Genre))
  const scope = entry.scope ? SCOPE_LABELS[entry.scope as Scope] : null
  return `${meta?.emoji ?? ''} ${scope ? scope.name + 'の' : ''}${meta?.name ?? entry.genre}`
}

interface ArchiveEntry {
  id: number
  date: string
  scope: string
  genre: string
  questionCount: number
  played: boolean
}

const router = useRouter()
const { isLoggedIn, isPremium, authFetch } = useAuth()

const list = ref<ArchiveEntry[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

async function load() {
  if (!isLoggedIn.value) {
    error.value = 'ログインが必要です'
    return
  }
  loading.value = true
  error.value = null
  try {
    const res = await authFetch('/api/daily/archive?limit=50')
    if (res.status === 402) {
      error.value = '過去アーカイブはプレミアム限定機能です'
      return
    }
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    list.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="space-y-5 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-5 space-y-2">
      <div class="text-xs font-mono text-slate-500">PREMIUM ARCHIVE</div>
      <h1 class="text-2xl font-bold tracking-tight flex items-center gap-2">
        <span class="text-amber-500">⭐</span>
        <span>デイリーアーカイブ</span>
      </h1>
      <p class="text-sm text-slate-600">
        過去のデイリーチャレンジに挑戦できます (プレミアム限定)。
      </p>
    </div>

    <div v-if="!isLoggedIn" class="glass-card p-5 text-center space-y-2">
      <div class="text-3xl">🔒</div>
      <div class="font-bold">ログインが必要です</div>
      <router-link to="/login" class="inline-block mt-2 px-4 py-2 bg-blue-600 text-white rounded">
        ログイン
      </router-link>
    </div>

    <div v-else-if="!isPremium" class="glass-card p-5 text-center space-y-2 ring-1 ring-amber-200">
      <div class="text-3xl">⭐</div>
      <div class="font-bold">プレミアムプランへのアップグレードが必要です</div>
      <p class="text-sm text-slate-600">過去のデイリーチャレンジは無制限に挑戦可能です。</p>
      <router-link to="/account"
        class="inline-block mt-2 px-4 py-2 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded font-bold">
        アップグレード
      </router-link>
    </div>

    <div v-else-if="loading" class="text-slate-500 text-center py-8">読み込み中…</div>
    <div v-else-if="error" class="text-red-600">{{ error }}</div>

    <div v-else-if="list.length === 0" class="text-center text-slate-500 py-8">
      まだアーカイブがありません
    </div>

    <div v-else class="space-y-2">
      <div v-for="entry in list" :key="entry.id"
        class="glass-card glass-card-hover p-4 cursor-pointer"
        @click="router.push(`/daily?archiveId=${entry.id}`)">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-xs font-mono text-slate-500">{{ entry.date }}</div>
            <div class="font-bold mt-0.5">{{ label(entry) }}</div>
            <div class="text-xs text-slate-500 mt-1">
              {{ entry.questionCount }} 問
              <span v-if="entry.played" class="ml-2 px-1.5 py-0.5 bg-emerald-100 text-emerald-700 rounded">挑戦済み</span>
            </div>
          </div>
          <div class="text-slate-400">→</div>
        </div>
      </div>
    </div>
  </section>
</template>
