<script setup lang="ts">
import { MODES } from '../types'
import AdSlot from '../components/AdSlot.vue'
import { useAuth } from '../composables/useAuth'

const { isLoggedIn } = useAuth()
</script>

<template>
  <section class="space-y-10">
    <!-- ヒーロー -->
    <div class="text-center space-y-4 py-6 animate-slide-up">
      <div class="inline-block px-3 py-1 text-xs font-mono text-slate-500 bg-slate-100 rounded-full border border-slate-200">
        Wikipedia から主題を当てるクイズ
      </div>
      <h1 class="text-5xl sm:text-6xl font-extrabold tracking-tight">
        <span class="brand-text">Wikiplays</span>
      </h1>
      <p class="text-slate-600 max-w-xl mx-auto leading-relaxed">
        Wikipedia の記事から主題を当てるクイズゲーム。<br class="hidden sm:block" />
        お好きな遊び方で挑戦しましょう。
      </p>
    </div>

    <!-- デイリー + ランキング + コミュニティジャンルへのリンク -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
      <router-link
        to="/daily"
        class="block glass-card glass-card-hover relative overflow-hidden p-4 animate-slide-up">
        <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-amber-500 via-orange-500 to-rose-500"></div>
        <div class="absolute -right-6 -top-6 w-24 h-24 rounded-full opacity-15 blur-2xl bg-gradient-to-br from-amber-500 to-rose-500"></div>
        <div class="relative flex items-center gap-3">
          <div class="text-3xl">⭐</div>
          <div class="flex-1">
            <div class="text-xs font-mono text-amber-600 font-bold flex items-center gap-1">
              DAILY
              <span v-if="!isLoggedIn" class="ml-1 px-1.5 py-0.5 bg-slate-700 text-white rounded-full text-[10px] font-bold">
                🔒 要ログイン
              </span>
            </div>
            <div class="text-base font-bold">今日の 5 問チャレンジ</div>
            <div class="text-xs text-slate-600 mt-0.5">全プレイヤー共通の問題</div>
          </div>
        </div>
      </router-link>

      <router-link
        to="/leaderboard"
        class="block glass-card glass-card-hover relative overflow-hidden p-4 animate-slide-up"
        style="animation-delay: 50ms">
        <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-yellow-400 via-amber-500 to-orange-500"></div>
        <div class="absolute -right-6 -top-6 w-24 h-24 rounded-full opacity-15 blur-2xl bg-gradient-to-br from-yellow-400 to-orange-500"></div>
        <div class="relative flex items-center gap-3">
          <div class="text-3xl">🏆</div>
          <div class="flex-1">
            <div class="text-xs font-mono text-amber-700 font-bold">RANKING</div>
            <div class="text-base font-bold">ランキング</div>
            <div class="text-xs text-slate-600 mt-0.5">全プレイヤーの累計スコア</div>
          </div>
        </div>
      </router-link>

      <router-link
        to="/community"
        class="block glass-card glass-card-hover relative overflow-hidden p-4 animate-slide-up"
        style="animation-delay: 100ms">
        <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-fuchsia-500 via-purple-500 to-indigo-500"></div>
        <div class="absolute -right-6 -top-6 w-24 h-24 rounded-full opacity-15 blur-2xl bg-gradient-to-br from-fuchsia-500 to-indigo-500"></div>
        <div class="relative flex items-center gap-3">
          <div class="text-3xl">🏷️</div>
          <div class="flex-1">
            <div class="text-xs font-mono text-purple-600 font-bold">COMMUNITY</div>
            <div class="text-base font-bold">コミュニティジャンル</div>
            <div class="text-xs text-slate-600 mt-0.5">自分でジャンルを作って公開</div>
          </div>
        </div>
      </router-link>
    </div>

    <!-- 広告枠 (Free のみ) -->
    <AdSlot slot="home-top" format="auto" label="ホーム上部" />

    <!-- モードカード -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <router-link
        v-for="(mode, i) in MODES"
        :key="mode.id"
        :to="`/mode/${mode.id}`"
        :data-theme="mode.theme"
        class="group relative block overflow-hidden rounded-2xl glass-card glass-card-hover animate-slide-up"
        :style="{ animationDelay: `${i * 60}ms` }">
        <!-- 装飾的なグラデーションバンド -->
        <div :class="['absolute inset-x-0 top-0 h-1 bg-gradient-to-r', mode.gradient]"></div>
        <div :class="['absolute -right-8 -top-8 w-32 h-32 rounded-full opacity-10 group-hover:opacity-20 transition-opacity blur-2xl bg-gradient-to-br', mode.gradient]"></div>

        <div class="relative p-5 space-y-3">
          <div class="flex items-center gap-3">
            <div :class="['inline-flex items-center justify-center w-12 h-12 rounded-xl text-2xl text-white bg-gradient-to-br shadow-md', mode.gradient]">
              {{ mode.emoji }}
            </div>
            <div>
              <div class="text-xs font-mono text-slate-500">{{ mode.shortName }}</div>
              <div class="text-lg font-bold tracking-tight">{{ mode.name }}</div>
            </div>
          </div>
          <div class="text-xs theme-text font-bold tracking-wide">
            {{ mode.tagline }}
          </div>
          <p class="text-sm text-slate-600 leading-relaxed">{{ mode.description }}</p>
          <div class="flex items-center justify-end pt-1 text-xs theme-text font-bold opacity-70 group-hover:opacity-100 transition-opacity">
            プレイ →
          </div>
        </div>
      </router-link>
    </div>

  </section>
</template>
