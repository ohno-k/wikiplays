<script setup lang="ts">
import { computed } from 'vue'
import type { GameQuestion } from '../api'

const props = defineProps<{
  question: GameQuestion
  index: number
  total: number
  intervalMs: number
  timerKey: number
  busy: boolean
  canReveal: boolean
  /** 'blue' | 'amber' など。tailwind の色名。 */
  accent?: string
  /** 「すぐ次の段落を見る」ボタンを出すか (デイリーでは出さない)。 */
  allowManualReveal?: boolean
}>()

const emit = defineEmits<{
  answer: [char: string]
  reveal: []
  giveUp: []
  next: []
}>()

/** Tailwind は動的クラス名を生成できないので、静的な対応表を持つ。 */
const ACCENTS: Record<string, { bar: string; text: string; hover: string }> = {
  blue:  { bar: 'bg-blue-500',  text: 'text-blue-600',  hover: 'hover:bg-blue-50 hover:border-blue-400' },
  amber: { bar: 'bg-amber-500', text: 'text-amber-600', hover: 'hover:bg-amber-50 hover:border-amber-400' },
}
const accent = computed(() => ACCENTS[props.accent ?? 'blue'] ?? ACCENTS.blue)
const options = computed(() => {
  if (props.question.answered) return [] as string[]
  return props.question.slots[props.question.pos]?.options ?? []
})
const result = computed(() => props.question.result)
</script>

<template>
  <div>
    <div class="text-sm text-slate-500 mb-2 flex justify-between items-center">
      <span>第 {{ index + 1 }} 問 / {{ total }} 問</span>
      <span class="flex items-center gap-3">
        <span :class="question.lifeUsed ? 'text-slate-300' : 'text-rose-500'" title="ライフ: 1 回だけミスできます (その問題のスコアは半減)">
          {{ question.lifeUsed ? '🤍' : '❤️' }}
        </span>
        <span>{{ question.revealed }} / {{ question.paragraphCount }} 段落</span>
      </span>
    </div>

    <!-- 視覚的プログレスバー: 入力開始で止まる -->
    <div v-if="canReveal" class="h-1.5 bg-slate-200 rounded overflow-hidden mb-3">
      <div :class="['h-full progress-bar', accent.bar]"
        :key="timerKey"
        :style="{ animationDuration: `${intervalMs}ms` }"></div>
    </div>
    <div v-else-if="question.locked && !question.answered" class="text-xs text-emerald-700 mb-3">
      ⏸ 入力開始でタイマー停止。この問題は {{ question.revealed }} 段落で確定です。
    </div>

    <div class="space-y-4">
      <article
        class="no-copy bg-white border border-slate-200 rounded p-4 leading-relaxed text-sm min-h-32 space-y-3 max-h-[50vh] overflow-y-auto"
        @copy.prevent
        @cut.prevent
        @contextmenu.prevent
        @dragstart.prevent>
        <p v-if="question.revealed < question.paragraphCount" class="text-slate-300 text-center">… (前段落は未開示)</p>
        <p v-for="(p, i) in question.paragraphs" :key="i" class="whitespace-pre-wrap">{{ p }}</p>
      </article>

      <!-- 入力エリア -->
      <div class="bg-white border border-slate-200 rounded p-4 space-y-3">
        <div class="text-xs text-slate-500">答えを 1 文字ずつ選んでください ({{ question.answerLength }} 文字)</div>

        <div class="text-2xl font-bold text-center tracking-wider flex flex-wrap justify-center gap-1">
          <template v-for="(s, i) in question.slots" :key="i">
            <span v-if="s.skip !== null" class="text-slate-400">{{ s.skip }}</span>
            <span v-else-if="i < question.pos" class="text-emerald-600">{{ s.options?.[0] }}</span>
            <span v-else-if="i === question.pos && !question.answered" :class="['underline', accent.text]">_</span>
            <span v-else class="text-slate-300">・</span>
          </template>
        </div>

        <div v-if="question.missedChar && !question.answered" class="text-xs text-rose-600 text-center">
          「{{ question.missedChar }}」は違います。ライフを 1 つ使いました (この問題のスコアは半分になります)。
        </div>

        <div v-if="!question.answered && options.length > 0"
          :class="['grid gap-2', options.length >= 4 ? 'grid-cols-4' : 'grid-cols-3']">
          <button
            v-for="opt in options"
            :key="opt"
            :disabled="busy"
            @click="emit('answer', opt)"
            :class="['px-2 py-3 rounded border-2 border-slate-300 bg-white text-xl font-bold transition disabled:opacity-60', accent.hover]">
            {{ opt }}
          </button>
        </div>

        <div v-if="!question.answered" class="flex gap-2 justify-end">
          <button v-if="allowManualReveal"
            @click="emit('reveal')"
            :disabled="!canReveal || busy"
            class="px-3 py-1 bg-slate-200 rounded hover:bg-slate-300 disabled:opacity-50 text-xs">
            すぐ次の段落を見る
          </button>
          <button @click="emit('giveUp')" :disabled="busy" class="px-3 py-1 bg-slate-200 rounded hover:bg-slate-300 text-xs">
            ギブアップ
          </button>
        </div>
      </div>

      <!-- 結果 -->
      <div v-if="question.answered && result" class="space-y-3">
        <div class="bg-slate-100 rounded p-3">
          <div class="text-sm">答え: <span class="font-bold">{{ result.title }}</span></div>
          <div v-if="result.correct" class="text-lg font-bold text-emerald-600">
            正解! {{ result.revealedCount }} 段落で当てた
            <span v-if="result.lifeUsed" class="text-sm font-normal text-slate-500">(ライフ使用で半減)</span>
          </div>
          <div v-else class="text-lg font-bold text-red-600">
            不正解<span v-if="result.wrongChar"> (「{{ result.wrongChar }}」を選んだ)</span>
          </div>
          <div v-if="!result.correct" class="text-sm text-slate-700">
            {{ result.correctChars }} / {{ result.totalInputChars }} 文字正解
          </div>
          <div class="text-sm text-slate-700">スコア: {{ result.score }}</div>
        </div>
        <a :href="result.pageUrl" target="_blank" rel="noopener" class="text-xs text-blue-600 hover:underline">
          Wikipedia で記事を開く
        </a>
        <button @click="emit('next')" :disabled="busy"
          class="w-full px-4 py-2 bg-emerald-600 text-white rounded hover:bg-emerald-700 disabled:opacity-60">
          {{ index + 1 >= total ? '結果を見る' : '次の問題へ' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.progress-bar {
  animation-name: shrink;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
  transform-origin: right;
}
@keyframes shrink {
  0% { transform: scaleX(1); }
  100% { transform: scaleX(0); }
}
</style>
