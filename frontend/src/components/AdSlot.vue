<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuth } from '../composables/useAuth'

defineProps<{
  /** Google AdSense ad slot ID (例: "1234567890") */
  slot: string
  /** "auto" | "rectangle" | "horizontal" 等。 */
  format?: string
  /** インスペクタ表示用ラベル (任意)。 */
  label?: string
}>()

const { isPremium } = useAuth()
const containerRef = ref<HTMLDivElement | null>(null)

// AdSense クライアント ID (本番では環境変数経由で埋め込む)
const CLIENT_ID = (import.meta as { env?: Record<string, string> }).env?.VITE_ADSENSE_CLIENT ?? ''

onMounted(() => {
  if (isPremium.value || !CLIENT_ID || !containerRef.value) return
  try {
    const w = window as unknown as { adsbygoogle?: unknown[] }
    w.adsbygoogle = w.adsbygoogle || []
    w.adsbygoogle.push({})
  } catch {
    // AdSense スクリプトが未ロードでも無視
  }
})
</script>

<template>
  <div v-if="!isPremium" ref="containerRef" class="my-3">
    <div v-if="!CLIENT_ID" class="glass-card p-3 text-center text-xs text-slate-500">
      <div class="font-mono opacity-60">[広告枠] {{ label ?? slot }}</div>
      <div class="text-[10px] mt-1">プレミアム加入で広告非表示</div>
    </div>
    <ins v-else
      class="adsbygoogle"
      style="display:block"
      :data-ad-client="CLIENT_ID"
      :data-ad-slot="slot"
      :data-ad-format="format ?? 'auto'"
      data-full-width-responsive="true"></ins>
  </div>
</template>
