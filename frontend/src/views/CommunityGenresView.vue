<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  fetchCommunityGenres,
  createCommunityGenre,
  deleteCommunityGenre,
  getDisplayName,
  setDisplayName,
  type CommunityGenre,
} from '../api'
import { useAuth } from '../composables/useAuth'

const { token, isLoggedIn, isPremium } = useAuth()

const list = ref<CommunityGenre[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// 作成フォーム
const showForm = ref(false)
const formName = ref('')
const formEmoji = ref('🏷️')
const formCategories = ref<string[]>(['', '', ''])
const formCreator = ref(getDisplayName())
const submitting = ref(false)
const submitError = ref<string | null>(null)

const EMOJI_OPTIONS = ['🏷️', '⭐', '🎯', '🎨', '🎬', '🎮', '🍣', '🏰', '🚄', '✈️', '⚾', '🎸', '📖', '🐱', '🐶', '🐉', '🌸', '🍵']

async function load() {
  loading.value = true
  error.value = null
  try {
    list.value = await fetchCommunityGenres()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取得失敗'
  } finally {
    loading.value = false
  }
}

function addCategoryField() {
  if (formCategories.value.length < 5) formCategories.value.push('')
}
function removeCategoryField(i: number) {
  if (formCategories.value.length > 1) formCategories.value.splice(i, 1)
}

async function submitForm() {
  submitError.value = null
  if (!formName.value.trim()) { submitError.value = 'ジャンル名を入力してください'; return }
  const cats = formCategories.value.map(c => c.trim()).filter(c => c.length > 0)
  if (cats.length === 0) { submitError.value = 'カテゴリを 1 つ以上入力してください'; return }
  submitting.value = true
  try {
    if (formCreator.value) setDisplayName(formCreator.value)
    await createCommunityGenre({
      name: formName.value.trim(),
      emoji: formEmoji.value,
      categories: cats,
      creatorName: formCreator.value.trim() || '名無し',
    }, token.value)
    formName.value = ''
    formCategories.value = ['', '', '']
    showForm.value = false
    await load()
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : '作成失敗'
  } finally {
    submitting.value = false
  }
}

async function handleDelete(g: CommunityGenre) {
  if (!confirm(`「${g.name}」を削除しますか?`)) return
  try {
    await deleteCommunityGenre(g.id)
    await load()
  } catch (e) {
    alert(e instanceof Error ? e.message : '削除失敗')
  }
}

onMounted(load)
</script>

<template>
  <section class="space-y-5 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-5 space-y-2">
      <div class="text-xs font-mono text-slate-500">COMMUNITY GENRES</div>
      <h1 class="text-2xl font-bold tracking-tight">コミュニティジャンル</h1>
      <p class="text-sm text-slate-600">
        Wikipedia のカテゴリを指定して、あなただけのジャンルを作って公開できます。
        作ったジャンルは A モードのプレイで選択できます。
      </p>
    </div>

    <!-- Premium 限定の案内 -->
    <div v-if="!isPremium" class="glass-card p-4 flex items-center justify-between gap-3">
      <div class="text-sm">
        <div class="font-bold flex items-center gap-1">
          <span class="text-amber-500">⭐</span>
          <span>ジャンル作成はプレミアム限定</span>
        </div>
        <div class="text-xs text-slate-500 mt-0.5">
          {{ isLoggedIn ? 'プレミアムにアップグレードすると独自ジャンルを作成できます' : 'ログイン後、プレミアムへの加入で作成可能になります' }}
        </div>
      </div>
      <router-link :to="isLoggedIn ? '/account' : '/login'"
        class="px-3 py-1.5 bg-gradient-to-r from-amber-500 to-rose-500 text-white rounded-full text-xs font-bold whitespace-nowrap">
        {{ isLoggedIn ? 'アップグレード' : 'ログイン' }}
      </router-link>
    </div>

    <div class="flex gap-2">
      <button @click="showForm = !showForm" :disabled="!isPremium"
        class="px-4 py-2 bg-gradient-to-r from-fuchsia-500 to-purple-600 text-white rounded-lg font-bold shadow-md hover:shadow-lg transition disabled:opacity-50 disabled:cursor-not-allowed">
        ＋ 新しいジャンルを作る
      </button>
      <button @click="load" class="px-3 py-2 bg-slate-100 rounded-lg text-sm hover:bg-slate-200">
        更新
      </button>
    </div>

    <!-- 作成フォーム -->
    <div v-if="showForm" class="glass-card p-5 space-y-3 animate-slide-up">
      <h2 class="font-bold">新しいジャンルを作成</h2>

      <div>
        <label class="text-xs font-bold text-slate-600">名前</label>
        <input v-model="formName" maxlength="50" placeholder="例: 将棋棋士"
          class="w-full border border-slate-300 rounded px-3 py-2 text-sm" />
      </div>

      <div>
        <label class="text-xs font-bold text-slate-600">絵文字</label>
        <div class="flex flex-wrap gap-1 mt-1">
          <button v-for="e in EMOJI_OPTIONS" :key="e" @click="formEmoji = e"
            :class="['px-2 py-1 rounded text-xl transition', formEmoji === e ? 'bg-purple-100 ring-2 ring-purple-400' : 'hover:bg-slate-100']">
            {{ e }}
          </button>
        </div>
      </div>

      <div>
        <label class="text-xs font-bold text-slate-600">Wikipedia カテゴリ (1〜5 個)</label>
        <div class="text-xs text-slate-500 mb-1">
          例: 「日本の将棋棋士」「タイトル経験者」のように、日本語版 Wikipedia に存在するカテゴリ名を指定
        </div>
        <div v-for="(_, i) in formCategories" :key="i" class="flex gap-1 mb-1">
          <input v-model="formCategories[i]" placeholder="例: 日本の将棋棋士"
            class="flex-1 border border-slate-300 rounded px-3 py-1.5 text-sm" />
          <button v-if="formCategories.length > 1" @click="removeCategoryField(i)"
            class="px-2 text-xs text-red-500 hover:bg-red-50 rounded">×</button>
        </div>
        <button v-if="formCategories.length < 5" @click="addCategoryField"
          class="text-xs text-blue-600 hover:underline">＋ カテゴリを追加</button>
      </div>

      <div>
        <label class="text-xs font-bold text-slate-600">あなたの名前 (任意)</label>
        <input v-model="formCreator" maxlength="32" placeholder="名無し"
          class="w-full border border-slate-300 rounded px-3 py-2 text-sm" />
      </div>

      <div v-if="submitError" class="text-sm text-red-600">{{ submitError }}</div>

      <div class="flex gap-2">
        <button @click="submitForm" :disabled="submitting"
          class="px-4 py-2 bg-purple-600 text-white rounded hover:bg-purple-700 disabled:bg-slate-300">
          {{ submitting ? '作成中…' : '作成' }}
        </button>
        <button @click="showForm = false" class="px-4 py-2 bg-slate-200 rounded hover:bg-slate-300">
          キャンセル
        </button>
      </div>
    </div>

    <!-- 一覧 -->
    <div v-if="loading" class="text-slate-500">読み込み中…</div>
    <div v-else-if="error" class="text-red-600">{{ error }}</div>

    <div v-else-if="list.length === 0" class="text-center text-slate-500 py-8">
      まだコミュニティジャンルがありません。最初の 1 つを作ってみませんか?
    </div>

    <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <router-link v-for="g in list" :key="g.id"
        :to="`/mode/a?community=${g.id}`"
        class="glass-card glass-card-hover p-4 flex items-start gap-3 relative">
        <div class="text-3xl">{{ g.emoji }}</div>
        <div class="flex-1 min-w-0">
          <div class="font-bold truncate">{{ g.name }}</div>
          <div class="text-xs text-slate-500 mt-0.5 flex items-center gap-2">
            <span>by {{ g.creatorName }}</span>
            <span>•</span>
            <span>{{ g.playCount }} プレイ</span>
          </div>
          <div class="flex flex-wrap gap-1 mt-2">
            <span v-for="c in g.categories" :key="c"
              class="text-xs px-1.5 py-0.5 bg-slate-100 rounded">{{ c }}</span>
          </div>
        </div>
        <button v-if="g.mine"
          @click.prevent="handleDelete(g)"
          title="削除"
          class="absolute top-2 right-2 text-xs text-slate-400 hover:text-red-500">
          ×
        </button>
      </router-link>
    </div>
  </section>
</template>
