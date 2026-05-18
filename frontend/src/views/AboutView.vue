<script setup lang="ts">
const CONTACT_EMAIL = 'wikiplays416@gmail.com'
</script>

<template>
  <section class="space-y-6 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-6 space-y-2">
      <div class="text-xs font-mono text-slate-500">ABOUT</div>
      <h1 class="text-3xl font-bold tracking-tight brand-text">Wikiplays について</h1>
      <p class="text-sm text-slate-600">
        Wikipedia の記事から主題を当てるクイズゲーム。<br />
        毎日のチャレンジで全国ランキングを目指せます。
      </p>
    </div>

    <!-- サービス概要 -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">🎮 サービス概要</h2>
      <p class="text-sm text-slate-700 leading-relaxed">
        Wikiplays は、日本語版 Wikipedia の記事を素材に「これは何の記事か?」を当てる
        Web ゲームです。固有名詞や年代がマスクされた記事を読み解き、推理しながら答えを導きます。
      </p>
      <ul class="text-sm text-slate-700 space-y-1 list-disc list-inside">
        <li>5 種類のゲームモード (メインは A モード: 段階開示型)</li>
        <li>20 ジャンル × 日本/世界スコープで選択可能</li>
        <li>毎日 1 回のデイリーチャレンジ (全プレイヤー共通の問題)</li>
        <li>累計スコアによるランキング機能</li>
        <li>プレミアム会員は無制限プレイ + コミュニティジャンル作成可能</li>
      </ul>
    </div>

    <!-- 倫理 -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">⚖️ 倫理ポリシー</h2>
      <p class="text-sm text-slate-700 leading-relaxed">
        被害者・遺族の存在する記事をクイズ素材として消費することは適切でないと考えています。
        以下の記事は出題対象から自動的に除外しています:
      </p>
      <ul class="text-sm text-slate-700 space-y-0.5 list-disc list-inside">
        <li>殺人・傷害・性犯罪・誘拐などの事件</li>
        <li>戦争・テロ・虐殺・暴動</li>
        <li>災害・事故の犠牲者個人</li>
        <li>自殺・自死関連</li>
        <li>未成年が関わる事件</li>
      </ul>
      <p class="text-sm text-slate-700 leading-relaxed">
        万一不適切な記事が表示された場合は、後述の連絡先までお知らせください。
      </p>
    </div>

    <!-- 連絡先 -->
    <div class="glass-card overflow-hidden">
      <div class="h-1 bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500"></div>
      <div class="p-5 space-y-3">
        <h2 class="text-xl font-bold">📮 ご意見・ご要望募集中</h2>
        <p class="text-sm text-slate-700 leading-relaxed">
          Wikiplays は遊んでくださる皆さまの声でより良くなります。<br />
          <strong class="text-blue-700">「こんなジャンルが欲しい」「この機能が不便」「こういうモードはどう?」</strong>
          といったご意見・ご要望を歓迎します。バグ報告も大歓迎です。
        </p>
        <p class="text-sm text-slate-700 leading-relaxed">
          下記メールアドレスまでお気軽にお寄せください。すべて開発者本人が拝見します。
        </p>
        <a
          :href="`mailto:${CONTACT_EMAIL}`"
          class="block w-full sm:w-auto sm:inline-block text-center px-5 py-3 bg-gradient-to-r from-blue-600 to-purple-600 text-white rounded-lg font-bold shadow-md hover:shadow-lg transition">
          ✉ {{ CONTACT_EMAIL }}
        </a>
        <div class="text-xs font-bold text-slate-500 pt-3">▼ よくお寄せいただく内容</div>
        <ul class="text-sm text-slate-600 space-y-1 list-disc list-inside">
          <li><strong>機能要望 / 改善提案</strong> — 新ジャンル・新モード・UI 改善など</li>
          <li>バグ報告 / 動作不良</li>
          <li><strong class="text-red-600">不適切な記事の通報</strong> (出題されるべきでない記事)</li>
          <li>サブスクリプション・お支払いに関するお問い合わせ</li>
          <li>その他のご質問</li>
        </ul>
      </div>
    </div>

    <!-- ライセンス・帰属 -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">📜 ライセンスと帰属</h2>
      <p class="text-sm text-slate-700 leading-relaxed">
        出題コンテンツは
        <a href="https://ja.wikipedia.org/" target="_blank" rel="noopener" class="text-blue-600 hover:underline">日本語版 Wikipedia</a>
        の記事を元にしています。Wikipedia のコンテンツは
        <a href="https://creativecommons.org/licenses/by-sa/4.0/deed.ja" target="_blank" rel="noopener" class="text-blue-600 hover:underline">クリエイティブ・コモンズ 表示-継承 4.0 国際 (CC BY-SA 4.0)</a>
        ライセンスで提供されています。各問題画面から元記事へのリンクを表示しています。
      </p>
    </div>

    <!-- 運営 -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">👤 運営</h2>
      <p class="text-sm text-slate-700 leading-relaxed">
        Wikiplays は個人によって開発・運営されています。
        商用サービスではありますが、小規模かつ持続可能な運営を心がけています。
      </p>
      <p class="text-xs text-slate-500">
        ホスティング: Render &nbsp;/ ドメイン: wikiplays.me &nbsp;/ 決済: Stripe
      </p>
    </div>

    <!-- 法的情報リンク (将来用) -->
    <div class="text-center text-xs text-slate-400 pt-4">
      <p>© Wikiplays. All wiki content under CC BY-SA 4.0.</p>
    </div>
  </section>
</template>
