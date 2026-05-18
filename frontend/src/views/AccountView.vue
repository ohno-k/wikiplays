<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const route = useRoute()
const router = useRouter()
const { user, isLoggedIn, isPremium, logout, refresh, authFetch } = useAuth()

const message = ref<string | null>(null)
const loading = ref(false)

onMounted(async () => {
  if (!isLoggedIn.value) {
    router.replace('/login')
    return
  }
  await refresh()
  if (route.query.subscribed) {
    message.value = '✅ プレミアムプランへの登録が完了しました!'
  } else if (route.query.cancelled) {
    message.value = '決済をキャンセルしました'
  }
})

async function upgrade() {
  loading.value = true
  message.value = null
  try {
    const res = await authFetch('/api/subscription/checkout', { method: 'POST' })
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'エラー' }))
      throw new Error(err.message)
    }
    const data = await res.json()
    window.location.href = data.url
  } catch (e) {
    message.value = e instanceof Error ? e.message : 'アップグレード失敗'
  } finally {
    loading.value = false
  }
}

async function manage() {
  loading.value = true
  message.value = null
  try {
    const res = await authFetch('/api/subscription/portal', { method: 'POST' })
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'エラー' }))
      throw new Error(err.message)
    }
    const data = await res.json()
    window.location.href = data.url
  } catch (e) {
    message.value = e instanceof Error ? e.message : '管理ページ開けず'
  } finally {
    loading.value = false
  }
}

const planLabel = computed(() => {
  if (!user.value) return ''
  if (user.value.premiumActive) return 'プレミアム'
  return 'フリー'
})
</script>

<template>
  <section v-if="user" class="space-y-5 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-5 space-y-2">
      <div class="text-xs font-mono text-slate-500">ACCOUNT</div>
      <h1 class="text-2xl font-bold tracking-tight">{{ user.displayName }}</h1>
      <div class="text-sm text-slate-500">{{ user.email }}</div>
    </div>

    <div v-if="message" class="glass-card p-3 text-sm text-blue-700">{{ message }}</div>

    <!-- 現在のプラン -->
    <div class="glass-card p-5 space-y-3">
      <div class="flex items-center justify-between">
        <div>
          <div class="text-xs font-mono text-slate-500">CURRENT PLAN</div>
          <div class="text-xl font-bold mt-1 flex items-center gap-2">
            <span v-if="isPremium" class="px-2 py-0.5 text-sm rounded bg-gradient-to-r from-amber-400 to-rose-400 text-white">⭐ PREMIUM</span>
            <span v-else class="px-2 py-0.5 text-sm rounded bg-slate-200 text-slate-600">FREE</span>
            <span>{{ planLabel }}プラン</span>
          </div>
        </div>
      </div>

      <div v-if="!isPremium" class="space-y-3 pt-2">
        <div class="text-sm text-slate-600">
          プレミアムプランで以下の機能が解放されます:
        </div>
        <ul class="text-sm text-slate-700 space-y-1 list-disc list-inside">
          <li>無制限プレイ (フリーは 1 日 5 問まで)</li>
          <li>過去のデイリーチャレンジへのアクセス</li>
          <li>詳細統計とジャンル別分析</li>
          <li>コミュニティジャンルの作成 (フリーは閲覧のみ)</li>
          <li>広告非表示</li>
        </ul>
        <button @click="upgrade" :disabled="loading"
          class="w-full px-4 py-3 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded-lg font-bold shadow-md hover:shadow-lg transition disabled:opacity-50">
          {{ loading ? '読み込み中…' : 'プレミアムにアップグレード (月 ¥500)' }}
        </button>
      </div>

      <div v-else class="space-y-3 pt-2">
        <div class="text-sm text-slate-600">
          いつもご利用ありがとうございます! プレミアム特典をお楽しみください。
        </div>
        <button @click="manage" :disabled="loading"
          class="w-full px-4 py-2 bg-slate-200 text-slate-700 rounded hover:bg-slate-300 disabled:opacity-50">
          {{ loading ? '読み込み中…' : '支払い・解約の管理 (Stripe)' }}
        </button>
      </div>
    </div>

    <!-- アクション -->
    <div class="glass-card p-4 flex flex-wrap gap-2">
      <router-link to="/stats" class="px-4 py-2 bg-slate-100 rounded hover:bg-slate-200 text-sm">
        📊 マイ統計
      </router-link>
      <router-link to="/friends" class="px-4 py-2 bg-slate-100 rounded hover:bg-slate-200 text-sm">
        👥 フレンド
      </router-link>
      <router-link v-if="isPremium" to="/daily-archive" class="px-4 py-2 bg-slate-100 rounded hover:bg-slate-200 text-sm">
        ⭐ デイリーアーカイブ
      </router-link>
      <button @click="logout(); router.push('/')"
        class="px-4 py-2 bg-slate-100 rounded hover:bg-slate-200 text-sm">
        ログアウト
      </button>
    </div>
  </section>
</template>
