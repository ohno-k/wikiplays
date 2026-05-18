<script setup lang="ts">
import { ref } from 'vue'
import { GENRES, SCOPE_LABELS, type Genre, type Scope } from '../types'
import type { CommunityGenre } from '../api'

defineProps<{
  modeName: string
  themeGradient?: string
  communityGenres?: CommunityGenre[]
}>()

const emit = defineEmits<{
  select: [genre: Genre | null, scope: Scope]
  selectCommunity: [genre: CommunityGenre]
}>()

const activeScope = ref<Scope>('jp')

function selectAll() {
  emit('select', null, activeScope.value)
}

function selectGenre(g: Genre) {
  emit('select', g, activeScope.value)
}

function selectCommunity(g: CommunityGenre) {
  emit('selectCommunity', g)
}
</script>

<template>
  <section class="space-y-6 animate-fade-in">
    <div class="text-center space-y-2">
      <div class="text-xs font-mono text-slate-500">{{ modeName }} で挑戦するジャンルを選んでください</div>
      <h2 class="text-2xl sm:text-3xl font-bold tracking-tight">どの分野で遊びますか?</h2>
    </div>

    <!-- スコープ切替 (日本 / 世界) -->
    <div class="flex justify-center gap-2">
      <button
        v-for="(meta, key) in SCOPE_LABELS"
        :key="key"
        @click="activeScope = (key as Scope)"
        :class="[
          'px-5 py-2.5 rounded-full text-sm font-bold transition flex items-center gap-2',
          activeScope === key
            ? 'bg-gradient-to-r ' + (themeGradient ?? 'from-sky-500 to-indigo-600') + ' text-white shadow-md scale-105'
            : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50',
        ]">
        <span class="text-lg">{{ meta.emoji }}</span>
        <span>{{ meta.name }}</span>
      </button>
    </div>

    <!-- 「総合」カード (スコープに関わらずどこからでもランダム) -->
    <button
      @click="selectAll"
      class="w-full glass-card glass-card-hover p-5 flex items-center gap-4 text-left">
      <div class="text-4xl">🎲</div>
      <div class="flex-1">
        <div class="text-xs font-mono text-slate-500">SCOPE FREE</div>
        <div class="text-lg font-bold">総合 (おまかせ)</div>
        <p class="text-sm text-slate-600">全 Wikipedia からランダムに出題されます。</p>
      </div>
      <div class="text-slate-400">→</div>
    </button>

    <div class="flex items-center gap-3 text-xs text-slate-400">
      <div class="flex-1 h-px bg-slate-200"></div>
      <span>または分野を選ぶ ({{ SCOPE_LABELS[activeScope].emoji }} {{ SCOPE_LABELS[activeScope].name }})</span>
      <div class="flex-1 h-px bg-slate-200"></div>
    </div>

    <!-- ジャンルグリッド -->
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
      <button
        v-for="(g, i) in GENRES"
        :key="g.id"
        @click="selectGenre(g.id)"
        :disabled="!g.scopes.includes(activeScope)"
        class="glass-card glass-card-hover p-4 text-left space-y-1.5 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:translate-y-0 disabled:hover:shadow-card animate-slide-up"
        :style="{ animationDelay: `${i * 30}ms` }">
        <div class="text-3xl">{{ g.emoji }}</div>
        <div class="text-base font-bold">{{ g.name }}</div>
        <div class="text-xs text-slate-500">
          {{ SCOPE_LABELS[activeScope].emoji }} {{ SCOPE_LABELS[activeScope].name }}の{{ g.name }}
        </div>
      </button>
    </div>

    <!-- コミュニティジャンル枠 -->
    <div v-if="communityGenres && communityGenres.length > 0" class="space-y-3 pt-2">
      <div class="flex items-center gap-3 text-xs text-slate-400">
        <div class="flex-1 h-px bg-slate-200"></div>
        <span>🏷️ コミュニティジャンル</span>
        <div class="flex-1 h-px bg-slate-200"></div>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <button
          v-for="g in communityGenres.slice(0, 9)"
          :key="g.id"
          @click="selectCommunity(g)"
          class="glass-card glass-card-hover p-3 text-left flex items-center gap-2">
          <span class="text-2xl">{{ g.emoji }}</span>
          <div class="min-w-0">
            <div class="font-bold text-sm truncate">{{ g.name }}</div>
            <div class="text-xs text-slate-500 truncate">by {{ g.creatorName }} • {{ g.playCount }} プレイ</div>
          </div>
        </button>
      </div>
      <router-link to="/community" class="block text-center text-xs text-blue-600 hover:underline">
        すべてのコミュニティジャンルを見る →
      </router-link>
    </div>

    <div class="text-xs text-slate-400 text-center pt-2">
      ジャンルを選ぶと、その分野の Wikipedia 記事だけが出題されます。
    </div>
  </section>
</template>
