<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(async () => {
  const token = route.query.token as string | undefined
  if (token) {
    localStorage.setItem('wikiplays_jwt', token)
    // /api/auth/me で user 情報を取得して保存
    try {
      const res = await fetch('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } })
      if (res.ok) {
        const user = await res.json()
        localStorage.setItem('wikiplays_user', JSON.stringify(user))
      }
    } catch {}
    window.location.href = '/account'
  } else {
    router.replace('/login?error=oauth')
  }
})
</script>

<template>
  <section class="max-w-md mx-auto py-10 text-center space-y-3 animate-fade-in">
    <div class="text-4xl">⏳</div>
    <div class="text-lg font-bold">ログイン中…</div>
  </section>
</template>
