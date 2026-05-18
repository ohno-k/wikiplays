<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useAuth } from './composables/useAuth'

const { isLoggedIn, isPremium, user, refresh } = useAuth()

onMounted(() => {
  if (isLoggedIn.value) refresh()
})
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <header class="sticky top-0 z-20 bg-white/70 backdrop-blur-md border-b border-slate-200/70">
      <div class="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between">
        <RouterLink to="/" class="flex items-center gap-2 group">
          <span class="inline-flex items-center justify-center w-9 h-9 rounded-lg brand-gradient text-white text-lg font-bold shadow-glow-blue group-hover:scale-105 transition-transform">
            W
          </span>
          <span class="text-xl font-bold tracking-tight brand-text">Wikiplays</span>
        </RouterLink>
        <nav class="text-sm flex items-center gap-1">
          <RouterLink to="/" class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition">
            ホーム
          </RouterLink>
          <RouterLink to="/rules" class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition hidden sm:inline-flex">
            📖 ルール
          </RouterLink>
          <RouterLink to="/leaderboard" class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition hidden sm:inline-flex">
            🏆
          </RouterLink>
          <RouterLink to="/stats" class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition hidden sm:inline-flex">
            📊
          </RouterLink>
          <RouterLink v-if="isLoggedIn" to="/account"
            class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition flex items-center gap-1">
            <span v-if="isPremium" class="text-amber-500">⭐</span>
            <span>{{ user?.displayName }}</span>
          </RouterLink>
          <RouterLink v-else to="/login"
            class="px-3 py-1.5 rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition">
            ログイン
          </RouterLink>
        </nav>
      </div>
    </header>

    <main class="flex-1 max-w-5xl w-full mx-auto px-4 py-8 animate-fade-in">
      <RouterView />
    </main>

    <footer class="text-center text-xs text-slate-400 py-6 border-t border-slate-200/60 mt-12">
      コンテンツは
      <a href="https://ja.wikipedia.org/" target="_blank" rel="noopener" class="underline hover:text-slate-600">日本語版 Wikipedia</a>
      (CC BY-SA 4.0) を利用しています
    </footer>
  </div>
</template>
