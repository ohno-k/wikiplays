<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const status = ref<'pending' | 'ok' | 'fail'>('pending')

onMounted(async () => {
  const token = route.query.token as string | undefined
  if (!token) { status.value = 'fail'; return }
  try {
    const res = await fetch('/api/email/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    })
    status.value = res.ok ? 'ok' : 'fail'
  } catch {
    status.value = 'fail'
  }
})
</script>

<template>
  <section class="space-y-5 animate-fade-in max-w-md mx-auto">
    <div class="glass-card p-6 text-center space-y-3">
      <div v-if="status === 'pending'" class="space-y-3">
        <div class="text-4xl">⏳</div>
        <div class="text-lg font-bold">確認中…</div>
      </div>
      <div v-else-if="status === 'ok'" class="space-y-3">
        <div class="text-5xl">✅</div>
        <h1 class="text-2xl font-bold">メールアドレスを確認しました</h1>
        <p class="text-sm text-slate-600">アカウントを安全に保つため、メール検証が完了しました。</p>
        <router-link to="/" class="inline-block px-4 py-2 brand-gradient text-white rounded font-bold">
          ホームへ
        </router-link>
      </div>
      <div v-else class="space-y-3">
        <div class="text-5xl">❌</div>
        <h1 class="text-2xl font-bold">確認に失敗しました</h1>
        <p class="text-sm text-slate-600">リンクが無効、または有効期限が切れています。</p>
        <router-link to="/account" class="inline-block px-4 py-2 bg-slate-200 rounded">
          アカウントへ
        </router-link>
      </div>
    </div>
  </section>
</template>
