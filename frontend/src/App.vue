<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useAuth } from './composables/useAuth'
import { levelTitle } from './scoring'

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
            class="px-3 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 transition flex items-center gap-1.5">
            <span v-if="isPremium" class="text-amber-500">⭐</span>
            <span v-if="user?.level != null"
              :title="levelTitle(user.level)"
              class="inline-flex items-center justify-center min-w-[1.5rem] h-5 px-1.5 rounded-full
                     bg-gradient-to-r from-sky-500 to-indigo-500 text-white text-[0.7rem] font-bold leading-none shadow-sm">
              Lv.{{ user.level }}
            </span>
            <span v-if="user?.streakDays" class="text-xs text-orange-500 font-bold" title="連続プレイ日数">
              🔥{{ user.streakDays }}
            </span>
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

    <footer class="text-xs text-slate-400 py-6 border-t border-slate-200/60 mt-12">
      <div class="max-w-5xl mx-auto px-4 space-y-2">
        <div class="flex flex-wrap items-center justify-center gap-x-4 gap-y-1">
          <RouterLink to="/about" class="hover:text-slate-600 hover:underline">About</RouterLink>
          <span class="opacity-30">·</span>
          <RouterLink to="/rules" class="hover:text-slate-600 hover:underline">遊び方</RouterLink>
          <span class="opacity-30">·</span>
          <RouterLink to="/legal" class="hover:text-slate-600 hover:underline">特定商取引法に基づく表記</RouterLink>
          <span class="opacity-30">·</span>
          <a href="mailto:wikiplays416@gmail.com" class="hover:text-slate-600 hover:underline">お問い合わせ</a>
        </div>
        <div class="text-center">
          コンテンツは
          <a href="https://ja.wikipedia.org/" target="_blank" rel="noopener" class="underline hover:text-slate-600">日本語版 Wikipedia</a>
          (CC BY-SA 4.0) を利用しています
        </div>
      </div>
    </footer>
  </div>
</template>
