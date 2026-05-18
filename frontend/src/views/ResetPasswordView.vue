<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const password = ref('')
const confirm = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)

async function submit() {
  error.value = null
  if (password.value.length < 8) { error.value = 'パスワードは 8 文字以上にしてください'; return }
  if (password.value !== confirm.value) { error.value = 'パスワードが一致しません'; return }
  const token = route.query.token as string | undefined
  if (!token) { error.value = 'リンクが無効です'; return }
  submitting.value = true
  try {
    const res = await fetch('/api/password/reset', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, password: password.value }),
    })
    if (res.ok) {
      router.push('/login?reset=1')
    } else {
      error.value = 'リンクが無効、または期限が切れています'
    }
  } catch {
    error.value = '送信に失敗しました'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="space-y-5 animate-fade-in max-w-md mx-auto">
    <div class="glass-card p-6 space-y-4">
      <h1 class="text-2xl font-bold tracking-tight">新しいパスワードを設定</h1>

      <div>
        <label class="text-xs font-bold text-slate-600">新しいパスワード</label>
        <input v-model="password" type="password" autocomplete="new-password"
          class="w-full mt-1 border border-slate-300 rounded px-3 py-2 text-sm" />
        <div class="text-xs text-slate-500 mt-1">8 文字以上</div>
      </div>

      <div>
        <label class="text-xs font-bold text-slate-600">確認</label>
        <input v-model="confirm" type="password" autocomplete="new-password"
          class="w-full mt-1 border border-slate-300 rounded px-3 py-2 text-sm" />
      </div>

      <div v-if="error" class="text-sm text-red-600">{{ error }}</div>

      <button @click="submit" :disabled="submitting"
        class="w-full px-4 py-2 brand-gradient text-white rounded font-bold hover:opacity-90 disabled:opacity-50">
        {{ submitting ? '更新中…' : 'パスワードを更新' }}
      </button>
    </div>
  </section>
</template>
