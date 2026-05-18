<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import { getPlayerId } from '../api'

const router = useRouter()
const { login, register } = useAuth()

const mode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const error = ref<string | null>(null)
const submitting = ref(false)

async function submit() {
  error.value = null
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await login(email.value, password.value)
    } else {
      await register(email.value, password.value, displayName.value, getPlayerId())
    }
    router.push('/account')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '失敗しました'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="space-y-5 animate-fade-in max-w-md mx-auto">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-6 space-y-4">
      <div class="text-center">
        <h1 class="text-2xl font-bold tracking-tight">
          {{ mode === 'login' ? 'ログイン' : 'アカウント登録' }}
        </h1>
        <p class="text-sm text-slate-500 mt-1">
          {{ mode === 'login' ? 'メールアドレスとパスワードでログイン' : '新しいアカウントを作成' }}
        </p>
      </div>

      <div>
        <label class="text-xs font-bold text-slate-600">メールアドレス</label>
        <input v-model="email" type="email" autocomplete="email"
          class="w-full mt-1 border border-slate-300 rounded px-3 py-2 text-sm" />
      </div>

      <div v-if="mode === 'register'">
        <label class="text-xs font-bold text-slate-600">表示名 (任意)</label>
        <input v-model="displayName" maxlength="32" placeholder="未入力ならメールから自動生成"
          class="w-full mt-1 border border-slate-300 rounded px-3 py-2 text-sm" />
      </div>

      <div>
        <label class="text-xs font-bold text-slate-600">パスワード</label>
        <input v-model="password" type="password" autocomplete="current-password"
          class="w-full mt-1 border border-slate-300 rounded px-3 py-2 text-sm" />
        <div v-if="mode === 'register'" class="text-xs text-slate-500 mt-1">8 文字以上</div>
      </div>

      <div v-if="error" class="text-sm text-red-600">{{ error }}</div>

      <button @click="submit" :disabled="submitting"
        class="w-full px-4 py-2 brand-gradient text-white rounded font-bold hover:opacity-90 disabled:opacity-50">
        {{ submitting ? '送信中…' : (mode === 'login' ? 'ログイン' : '登録') }}
      </button>

      <div class="text-center text-xs text-slate-500 pt-2">
        <button v-if="mode === 'login'" @click="mode = 'register'" class="text-blue-600 hover:underline">
          アカウントをお持ちでない方はこちら
        </button>
        <button v-else @click="mode = 'login'" class="text-blue-600 hover:underline">
          既にアカウントをお持ちの方はこちら
        </button>
      </div>
    </div>

    <div class="text-xs text-slate-400 text-center">
      アカウントを作るとプレイ履歴がサーバーに保存され、複数端末で同期されます。
    </div>
  </section>
</template>
