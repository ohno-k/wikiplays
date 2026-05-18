<script setup lang="ts">
import { ref } from 'vue'
import html2canvas from 'html2canvas'
import { scoreEmoji } from '../scoring'

interface Result {
  score: number
  correct?: boolean
}

const props = defineProps<{
  title: string
  subtitle?: string
  totalScore: number
  maxScore: number
  results: Result[]
  themeGradient?: string
  shareText: string
}>()

const cardRef = ref<HTMLDivElement | null>(null)
const downloading = ref(false)
const copied = ref(false)

async function downloadImage() {
  if (!cardRef.value) return
  downloading.value = true
  try {
    const canvas = await html2canvas(cardRef.value, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
    })
    const url = canvas.toDataURL('image/png')
    const a = document.createElement('a')
    a.href = url
    a.download = `wikiplays_${Date.now()}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } catch (e) {
    console.error('image generation failed', e)
  } finally {
    downloading.value = false
  }
}

async function copyImageToClipboard() {
  if (!cardRef.value) return
  try {
    const canvas = await html2canvas(cardRef.value, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
    })
    canvas.toBlob(async (blob) => {
      if (!blob) return
      try {
        await navigator.clipboard.write([
          new ClipboardItem({ 'image/png': blob }),
        ])
        copied.value = true
        setTimeout(() => { copied.value = false }, 2000)
      } catch (e) {
        console.error('clipboard write failed', e)
      }
    }, 'image/png')
  } catch (e) {
    console.error('image generation failed', e)
  }
}

async function copyText() {
  await navigator.clipboard.writeText(props.shareText)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

async function shareViaWebShare() {
  if (!cardRef.value) return
  try {
    const canvas = await html2canvas(cardRef.value, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
    })
    canvas.toBlob(async (blob) => {
      if (!blob) return
      const file = new File([blob], 'wikiplays.png', { type: 'image/png' })
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file], title: 'Wikiplays', text: props.shareText })
      } else {
        // Web Share API 非対応 → ダウンロードフォールバック
        downloadImage()
      }
    }, 'image/png')
  } catch (e) {
    console.error('share failed', e)
  }
}
</script>

<template>
  <div class="space-y-3">
    <!-- 共有用カード (canvas 化対象) -->
    <div ref="cardRef" class="relative overflow-hidden rounded-2xl p-6 text-center text-white"
      :class="[`bg-gradient-to-br ${themeGradient ?? 'from-indigo-600 via-purple-600 to-pink-600'}`]">
      <div class="absolute -right-12 -top-12 w-48 h-48 rounded-full bg-white/10 blur-2xl"></div>
      <div class="absolute -left-12 -bottom-12 w-48 h-48 rounded-full bg-white/10 blur-2xl"></div>

      <div class="relative">
        <div class="text-xs font-mono opacity-80 tracking-widest">WIKIPLAYS</div>
        <div class="text-2xl font-bold mt-1">{{ title }}</div>
        <div v-if="subtitle" class="text-sm opacity-80 mt-0.5">{{ subtitle }}</div>

        <div class="my-5">
          <div class="text-6xl font-bold font-mono">{{ totalScore }}</div>
          <div class="text-xs opacity-70">/ {{ maxScore }}</div>
        </div>

        <div class="text-3xl tracking-wider mb-2">
          <span v-for="(r, i) in results" :key="i">{{ scoreEmoji(r.score, maxScore / results.length) }}</span>
        </div>

        <div class="text-xs opacity-70 mt-3">wikiplays.me</div>
      </div>
    </div>

    <!-- アクション -->
    <div class="flex flex-wrap gap-2">
      <button @click="downloadImage" :disabled="downloading"
        class="flex-1 min-w-[120px] px-4 py-2 bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:bg-slate-300 text-sm">
        {{ downloading ? '生成中…' : '🖼️ 画像で保存' }}
      </button>
      <button @click="copyImageToClipboard"
        class="flex-1 min-w-[120px] px-4 py-2 bg-slate-200 rounded hover:bg-slate-300 text-sm">
        📋 画像コピー
      </button>
      <button @click="shareViaWebShare"
        class="flex-1 min-w-[120px] px-4 py-2 bg-slate-200 rounded hover:bg-slate-300 text-sm">
        🔗 共有
      </button>
      <button @click="copyText"
        class="flex-1 min-w-[120px] px-4 py-2 bg-slate-200 rounded hover:bg-slate-300 text-sm">
        📝 テキストコピー
      </button>
    </div>

    <div v-if="copied" class="text-sm text-emerald-600 text-center">コピーしました</div>
  </div>
</template>
