<script setup lang="ts">
import { ref } from 'vue'

const email = ref('')
const submitting = ref(false)
const submitted = ref(false)
const error = ref<string | null>(null)

async function submit() {
  if (!email.value) { error.value = 'メールアドレスを入力してください'; return }
  submitting.value = true
  error.value = null
  try {
    await fetch('/api/password/forgot', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value }),
    })
    submitted.value = true
  } catch {
    error.value = '送信に失敗しました'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="space-y-5 animate-fade-in max-w-md mx-auto">
    <router-link to="/login" class="text-sm text-blue-600 hover:underline">← ログインに戻る</router-link>

    <div class="glass-card p-6 space-y-4">
      <h1 class="text-2xl font-bold tracking-tight">パスワードをお忘れですか?</h1>

      <div v-if="submitted" class="space-y-3">
        <div class="text-3xl text-center">📧</div>
        <p class="text-sm text-slate-600 text-center">
          パスワード再設定用のリンクをメールで送信しました。<br />
          受信トレイをご確認ください (見当たらない場合は迷惑メールフォルダもご確認ください)。
        </p>
      </div>

      <div v-else>
        <p class="text-sm text-slate-600">
          登録したメールアドレスを入力してください。再設定用のリンクをお送りします。
        </p>

        <div class="mt-4">
          <label class="text-xs font-bold text-slate-600">メールアドレス</label>
          <input v-model="email" type="email" autocomplete="email"
            class="w-full mt-1 border border-slate-300 rounded px-3 py-2 text-sm" />
        </div>

        <div v-if="error" class="text-sm text-red-600 mt-2">{{ error }}</div>

        <button @click="submit" :disabled="submitting"
          class="w-full mt-4 px-4 py-2 brand-gradient text-white rounded font-bold hover:opacity-90 disabled:opacity-50">
          {{ submitting ? '送信中…' : '再設定リンクを送る' }}
        </button>
      </div>
    </div>
  </section>
</template>
