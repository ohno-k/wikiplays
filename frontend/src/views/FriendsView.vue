<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuth } from '../composables/useAuth'

interface FriendEntry {
  id: number
  status: string
  isInviter: boolean
  userId: number
  displayName: string
  email: string
  createdAt: string
}

const { isLoggedIn, authFetch } = useAuth()

const friends = ref<FriendEntry[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const inviteEmail = ref('')
const inviteMessage = ref<string | null>(null)

async function load() {
  if (!isLoggedIn.value) return
  loading.value = true
  error.value = null
  try {
    const res = await authFetch('/api/friends')
    if (res.ok) friends.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

async function invite() {
  inviteMessage.value = null
  if (!inviteEmail.value) return
  try {
    const res = await authFetch('/api/friends/invite', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: inviteEmail.value }),
    })
    if (res.ok) {
      inviteMessage.value = '招待を送信しました'
      inviteEmail.value = ''
      load()
    } else {
      const err = await res.json().catch(() => ({}))
      inviteMessage.value = err.message ?? '招待に失敗しました'
    }
  } catch {
    inviteMessage.value = '招待に失敗しました'
  }
}

async function accept(id: number) {
  await authFetch(`/api/friends/${id}/accept`, { method: 'POST' })
  load()
}

async function remove(id: number) {
  if (!confirm('この関係を削除しますか?')) return
  await authFetch(`/api/friends/${id}`, { method: 'DELETE' })
  load()
}

const accepted = computed(() => friends.value.filter(f => f.status === 'ACCEPTED'))
const incoming = computed(() => friends.value.filter(f => f.status === 'PENDING' && !f.isInviter))
const outgoing = computed(() => friends.value.filter(f => f.status === 'PENDING' && f.isInviter))

onMounted(load)
</script>

<template>
  <section class="space-y-5 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-5 space-y-2">
      <div class="text-xs font-mono text-slate-500">FRIENDS</div>
      <h1 class="text-2xl font-bold tracking-tight">👥 フレンド</h1>
    </div>

    <div v-if="!isLoggedIn" class="glass-card p-5 text-center space-y-2">
      <div class="text-3xl">🔒</div>
      <div class="font-bold">ログインが必要です</div>
      <router-link to="/login" class="inline-block px-4 py-2 bg-blue-600 text-white rounded">ログイン</router-link>
    </div>

    <template v-else>
      <!-- 招待フォーム -->
      <div class="glass-card p-4 space-y-2">
        <div class="text-sm font-bold">フレンドを招待</div>
        <div class="flex gap-2">
          <input v-model="inviteEmail" type="email" placeholder="相手のメールアドレス"
            class="flex-1 border border-slate-300 rounded px-3 py-2 text-sm" />
          <button @click="invite" :disabled="!inviteEmail"
            class="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:bg-slate-300">
            招待
          </button>
        </div>
        <div v-if="inviteMessage" class="text-xs text-slate-600">{{ inviteMessage }}</div>
      </div>

      <div v-if="loading" class="text-slate-500">読み込み中…</div>
      <div v-if="error" class="text-red-600 text-sm">{{ error }}</div>

      <!-- 受信した招待 -->
      <div v-if="incoming.length" class="glass-card p-4 space-y-2">
        <div class="text-sm font-bold">受信した招待</div>
        <div v-for="f in incoming" :key="f.id" class="flex items-center justify-between text-sm border-b border-slate-100 last:border-0 pb-2 last:pb-0">
          <div>
            <div class="font-bold">{{ f.displayName }}</div>
            <div class="text-xs text-slate-500">{{ f.email }}</div>
          </div>
          <div class="flex gap-1">
            <button @click="accept(f.id)" class="px-3 py-1 bg-emerald-600 text-white rounded text-xs hover:bg-emerald-700">承認</button>
            <button @click="remove(f.id)" class="px-3 py-1 bg-slate-200 rounded text-xs hover:bg-slate-300">拒否</button>
          </div>
        </div>
      </div>

      <!-- フレンド -->
      <div class="glass-card p-4 space-y-2">
        <div class="text-sm font-bold">フレンド ({{ accepted.length }})</div>
        <div v-if="accepted.length === 0" class="text-xs text-slate-500 py-2 text-center">まだフレンドがいません</div>
        <div v-else>
          <div v-for="f in accepted" :key="f.id" class="flex items-center justify-between text-sm border-b border-slate-100 last:border-0 pb-2 last:pb-0">
            <div>
              <div class="font-bold">{{ f.displayName }}</div>
              <div class="text-xs text-slate-500">{{ f.email }}</div>
            </div>
            <button @click="remove(f.id)" class="text-xs text-red-500 hover:underline">削除</button>
          </div>
        </div>
      </div>

      <!-- 送信した招待 -->
      <div v-if="outgoing.length" class="glass-card p-4 space-y-2">
        <div class="text-sm font-bold text-slate-500">送信した招待 (承認待ち)</div>
        <div v-for="f in outgoing" :key="f.id" class="flex items-center justify-between text-sm border-b border-slate-100 last:border-0 pb-2 last:pb-0">
          <div>
            <div class="text-slate-700">{{ f.displayName }}</div>
            <div class="text-xs text-slate-500">{{ f.email }}</div>
          </div>
          <button @click="remove(f.id)" class="text-xs text-slate-500 hover:underline">取消</button>
        </div>
      </div>
    </template>
  </section>
</template>
