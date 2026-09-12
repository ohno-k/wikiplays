<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import { levelTitle } from '../scoring'

const route = useRoute()
const router = useRouter()
const { user, isLoggedIn, isPremium, logout, refresh, authFetch } = useAuth()

const message = ref<string | null>(null)
const loading = ref(false)
const loadingPlan = ref<string | null>(null)

interface SubscriptionInfo {
  plan: 'FREE' | 'PREMIUM'
  status: string
  premiumActive: boolean
  trialing: boolean
  trialEligible: boolean
  trialEnd?: string
  currentPeriodEnd?: string
  cancelledAt?: string
}
const subInfo = ref<SubscriptionInfo | null>(null)

const trialDaysLeft = computed(() => {
  if (!subInfo.value?.trialing || !subInfo.value.trialEnd) return 0
  const end = new Date(subInfo.value.trialEnd).getTime()
  const now = Date.now()
  const ms = Math.max(0, end - now)
  return Math.ceil(ms / (1000 * 60 * 60 * 24))
})

async function fetchSubInfo() {
  try {
    const res = await authFetch('/api/subscription/me')
    if (res.ok) subInfo.value = await res.json()
  } catch {}
}

/** Stripe から最新状態を引いて DB を同期。Webhook 取りこぼし時の救済。 */
async function syncFromStripe(silent = false): Promise<boolean> {
  try {
    const res = await authFetch('/api/subscription/sync', { method: 'POST' })
    if (!res.ok) {
      if (!silent) message.value = '同期に失敗しました'
      return false
    }
    const data = await res.json()
    // 同期後の状態を再取得
    await Promise.all([refresh(), fetchSubInfo()])
    if (!silent && data.hasActiveSubscription) {
      message.value = '✅ Stripe と同期しました'
    } else if (!silent) {
      message.value = 'Stripe にアクティブなサブスクは見つかりませんでした'
    }
    return data.hasActiveSubscription === true
  } catch {
    if (!silent) message.value = '同期に失敗しました'
    return false
  }
}

const syncing = ref(false)
async function manualSync() {
  syncing.value = true
  message.value = null
  try { await syncFromStripe(false) }
  finally { syncing.value = false }
}

interface PlanInfo {
  id: '1m' | '3m' | '6m'
  name: string
  price: number
  months: number
  perMonth: number
  discountLabel?: string
  highlight?: boolean
}

const PLANS: PlanInfo[] = [
  { id: '1m', name: '月額プラン',  price: 500,  months: 1, perMonth: 500 },
  { id: '3m', name: '3 ヶ月プラン', price: 1300, months: 3, perMonth: 433, discountLabel: '13% お得' },
  { id: '6m', name: '6 ヶ月プラン', price: 2000, months: 6, perMonth: 333, discountLabel: '33% お得', highlight: true },
]

onMounted(async () => {
  if (!isLoggedIn.value) {
    router.replace('/login')
    return
  }
  await Promise.all([refresh(), fetchSubInfo()])

  // Webhook 取りこぼしの自動回復:
  // - Stripe から戻ってきた直後 (?subscribed=1)
  // - もしくは FREE プランのまま (Webhook 未到達の可能性)
  // のいずれかで Stripe API を直接見て同期する。
  const shouldAutoSync = route.query.subscribed || !subInfo.value?.premiumActive
  if (shouldAutoSync) {
    await syncFromStripe(true)
  }

  if (route.query.subscribed) {
    message.value = subInfo.value?.trialing
      ? '✅ 7 日間の無料体験を開始しました!'
      : '✅ プレミアムプランへの登録が完了しました!'
  } else if (route.query.cancelled) {
    message.value = '決済をキャンセルしました'
  }
})

async function upgrade(planId: string) {
  loadingPlan.value = planId
  loading.value = true
  message.value = null
  try {
    const res = await authFetch(`/api/subscription/checkout?plan=${planId}`, { method: 'POST' })
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
    loadingPlan.value = null
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

const xpPercent = computed(() => {
  const u = user.value
  if (!u || !u.xpForNextLevel) return 0
  return Math.min(100, Math.max(0, (u.xpIntoLevel / u.xpForNextLevel) * 100))
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

    <!-- 経験値・レベル -->
    <div v-if="user.level != null" class="glass-card p-5 space-y-3">
      <div class="flex items-baseline justify-between">
        <div class="text-xs font-mono text-slate-500">EXPERIENCE</div>
        <div class="text-xs text-slate-400">
          称号: <span class="font-bold text-slate-600">{{ levelTitle(user.level) }}</span>
          ・ 累計 {{ user.xp ?? 0 }} XP
          <span v-if="user.streakDays" class="ml-2 text-orange-500 font-bold">🔥 {{ user.streakDays }} 日連続</span>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <span class="inline-flex items-center justify-center w-12 h-12 rounded-full
                     bg-gradient-to-br from-sky-500 to-indigo-600 text-white font-bold shadow-glow-blue">
          Lv.{{ user.level }}
        </span>
        <div class="flex-1 space-y-1">
          <div class="h-3 bg-slate-200 rounded-full overflow-hidden">
            <div class="h-full bg-gradient-to-r from-sky-400 to-emerald-400 transition-all"
              :style="{ width: `${xpPercent}%` }"></div>
          </div>
          <div class="text-xs text-slate-500 flex justify-between">
            <span>{{ user.xpIntoLevel ?? 0 }} / {{ user.xpForNextLevel ?? 0 }} XP</span>
            <span>次のレベルまで {{ Math.max(0, (user.xpForNextLevel ?? 0) - (user.xpIntoLevel ?? 0)) }} XP</span>
          </div>
        </div>
      </div>
      <div class="text-xs text-slate-400">
        プレイすると経験値を獲得できます (1 プレイあたり 最大 110 XP / 日 300 XP まで)。
      </div>
    </div>

    <div v-if="message" class="glass-card p-3 text-sm text-blue-700">{{ message }}</div>

    <!-- 現在のプラン -->
    <div class="glass-card p-5 space-y-3">
      <div class="flex items-center justify-between">
        <div>
          <div class="text-xs font-mono text-slate-500">CURRENT PLAN</div>
          <div class="text-xl font-bold mt-1 flex items-center gap-2">
            <span v-if="subInfo?.trialing" class="px-2 py-0.5 text-sm rounded bg-gradient-to-r from-emerald-400 to-teal-400 text-white">🎁 トライアル中</span>
            <span v-else-if="isPremium" class="px-2 py-0.5 text-sm rounded bg-gradient-to-r from-amber-400 to-rose-400 text-white">⭐ PREMIUM</span>
            <span v-else class="px-2 py-0.5 text-sm rounded bg-slate-200 text-slate-600">FREE</span>
            <span>{{ planLabel }}プラン</span>
          </div>
        </div>
      </div>

      <!-- トライアル中の案内 -->
      <div v-if="subInfo?.trialing" class="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-sm space-y-1">
        <div class="font-bold text-emerald-700">
          🎁 7 日間無料体験中 (あと {{ trialDaysLeft }} 日)
        </div>
        <div class="text-xs text-emerald-700">
          {{ subInfo.trialEnd ? new Date(subInfo.trialEnd).toLocaleDateString('ja-JP') : '' }} に自動課金が開始されます。
          それまでにキャンセルすれば請求は発生しません。
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
          <li>コミュニティジャンルのプレイ・作成</li>
          <li>広告非表示</li>
        </ul>
        <div v-if="subInfo?.trialEligible" class="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-sm">
          <div class="font-bold text-emerald-700">🎁 初回 7 日間無料</div>
          <div class="text-xs text-emerald-700 mt-1">
            7 日間は無料で全機能を使えます。期間中にキャンセルすれば請求は発生しません。
          </div>
        </div>
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

      <!-- 状態がずれた時の手動同期ボタン (小さく目立たないように) -->
      <div class="pt-1 text-right">
        <button @click="manualSync" :disabled="syncing"
          class="text-xs text-slate-400 hover:text-slate-600 underline disabled:opacity-50">
          {{ syncing ? '同期中…' : 'Stripe と同期する' }}
        </button>
      </div>
    </div>

    <!-- 3 プラン選択 (未加入時のみ) -->
    <div v-if="!isPremium" class="space-y-3">
      <div class="text-sm font-bold text-slate-700 px-1">プランを選んでアップグレード</div>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div v-for="plan in PLANS" :key="plan.id"
          :class="[
            'glass-card overflow-hidden p-5 space-y-3 relative',
            plan.highlight ? 'ring-2 ring-amber-400' : ''
          ]">
          <div v-if="plan.highlight" class="absolute top-0 right-0 px-2 py-1 bg-gradient-to-r from-amber-400 to-rose-400 text-white text-xs font-bold rounded-bl-lg">
            おすすめ
          </div>
          <div class="text-xs font-mono text-slate-500">{{ plan.months }} ヶ月</div>
          <div class="text-lg font-bold">{{ plan.name }}</div>
          <div>
            <div class="text-3xl font-bold number-display brand-text">¥{{ plan.price.toLocaleString() }}</div>
            <div class="text-xs text-slate-500 mt-1">
              月あたり ¥{{ plan.perMonth.toLocaleString() }}
              <span v-if="plan.discountLabel" class="ml-1 px-1.5 py-0.5 bg-emerald-100 text-emerald-700 rounded text-xs font-bold">
                {{ plan.discountLabel }}
              </span>
            </div>
          </div>
          <button @click="upgrade(plan.id)" :disabled="loading"
            :class="[
              'w-full px-4 py-2 rounded font-bold text-white shadow-md hover:shadow-lg transition disabled:opacity-50',
              plan.highlight
                ? 'bg-gradient-to-r from-amber-500 to-rose-500'
                : 'bg-gradient-to-r from-slate-600 to-slate-700'
            ]">
            {{ loadingPlan === plan.id ? '読み込み中…' : (subInfo?.trialEligible ? '7 日間無料で試す' : 'このプランで申込') }}
          </button>
          <div v-if="subInfo?.trialEligible" class="text-xs text-emerald-700 text-center font-bold">
            7 日間無料、その後 ¥{{ plan.price.toLocaleString() }} 自動課金
          </div>
        </div>
      </div>
      <div class="text-xs text-slate-400 text-center pt-1">
        支払いは Stripe を通じて安全に行われます。
        無料期間中・期間後ともいつでもキャンセル可能。期間後の自動課金前にキャンセルすれば請求は発生しません。
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
