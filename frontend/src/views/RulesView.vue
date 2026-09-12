<script setup lang="ts">
import { MODES } from '../types'
</script>

<template>
  <section class="space-y-6 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-6 space-y-2">
      <div class="text-xs font-mono text-slate-500">RULES</div>
      <h1 class="text-3xl font-bold tracking-tight">遊び方</h1>
      <p class="text-sm text-slate-600">
        Wikiplays は Wikipedia の記事から主題を当てるクイズゲームです。
        ゲームの遊び方とサービス全体の機能を紹介します。
      </p>
    </div>

    <!-- 基本ルール -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">🎮 基本ルール</h2>
      <ol class="list-decimal list-inside space-y-2 text-sm leading-relaxed">
        <li>Wikipedia からランダム or ジャンル指定で選ばれた記事が出題されます</li>
        <li>記事のタイトル・読み仮名・固有名詞は <span class="font-mono">■■■■</span> でマスクされています</li>
        <li>マスクを手がかりに、何の記事かを推理します</li>
        <li>1 セッションは <span class="font-bold">5 問</span>。スコアが累計されます</li>
        <li>戦争・事件・現代の悲劇など出題に不適切な記事は自動で除外されます</li>
      </ol>
    </div>

    <!-- A モード詳細 -->
    <div class="glass-card overflow-hidden">
      <div class="h-1 bg-gradient-to-r from-sky-500 to-indigo-600"></div>
      <div class="p-5 space-y-3">
        <h2 class="text-xl font-bold flex items-center gap-2">
          <span class="text-2xl">⏱️</span>
          <span>A モード (じわじわ開示)</span>
        </h2>
        <p class="text-sm text-slate-600">メインモード。記事の末尾から段落を順次開示し、4 択で答えていく早押し系。</p>
        <div class="space-y-2 text-sm">
          <div>
            <span class="font-bold">開示方式:</span> 記事の <span class="text-blue-700 font-bold">末尾の段落から</span> 開示。
            選択した難易度のペースで自動的に段落が追加されます (のんびり 20s / ふつう 10s / 早押し 5s)。
          </div>
          <div>
            <span class="font-bold">記事の知名度:</span> 出題前に <span class="text-blue-700 font-bold">超メジャー 〜 超マニアック</span> の 5 段階 (または おまかせ) を選べます。
            各記事の知名度は他言語版の数・記事の長さ・別名の多さから算出し、選んだジャンル内で順位づけして 5 等分しています。
            B〜E モードでも同じ選択が使えます。
          </div>
          <div>
            <span class="font-bold">回答方式:</span> 答えのタイトルを <span class="text-blue-700 font-bold">1 文字ずつ 4 択</span> から選択。
            <span class="text-emerald-700 font-bold">最初の 1 文字を選んだ時点でタイマーが止まり</span>、その段落数でスコアが確定します。
            入力に時間がかかっても減点されません。
          </div>
          <div>
            <span class="font-bold">ライフ:</span> ミスは <span class="text-rose-600 font-bold">1 回だけ</span> 許されます (その問題のスコアは半分)。
            2 回目のミスで不正解 (途中までの文字数で部分点あり)。
          </div>
          <div>
            <span class="font-bold">スコア:</span> 少ない段落で当てるほど高得点 (1 段落=1000、2=800、3=600、4=400、5=200、6+=100)。
            部分点は最大の半分まで。採点はサーバー側で行われます。
          </div>
        </div>
      </div>
    </div>

    <!-- 他モードの簡単な紹介 -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">🎲 ゲームモード</h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <div v-for="m in MODES" :key="m.id" class="border border-slate-200 rounded p-3">
          <div class="flex items-center gap-2">
            <span class="text-2xl">{{ m.emoji }}</span>
            <div>
              <div class="text-xs font-mono text-slate-500">{{ m.shortName }}</div>
              <div class="font-bold">{{ m.name }}</div>
            </div>
          </div>
          <div class="text-xs text-slate-600 mt-2">{{ m.description }}</div>
        </div>
      </div>
    </div>

    <!-- ジャンル -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">🏷️ ジャンルとスコープ</h2>
      <ul class="list-disc list-inside text-sm space-y-1">
        <li><span class="font-bold">スコープ:</span> 🇯🇵 日本 / 🌍 世界 のどちらか</li>
        <li><span class="font-bold">ジャンル:</span> 鉄道 / 歴史 / 地理 / 科学 / 生物 / 植物 / 古生物 / 天体 / 芸術 / 文学 / 音楽 / 映画 / アニメ・漫画 / スポーツ / 神話 / 建築 / 乗り物 / 言語 / IT / 食 (全 20)</li>
        <li><span class="font-bold">総合 (おまかせ):</span> 全 Wikipedia からランダム出題</li>
        <li><span class="font-bold">正解判定 (B モード):</span> Wikipedia の別名・略称 (リダイレクト) も正解として扱います</li>
        <li><span class="font-bold">コミュニティジャンル:</span> プレミアム会員が作成、誰でもプレイ可能</li>
      </ul>
    </div>

    <!-- デイリー -->
    <div class="glass-card overflow-hidden">
      <div class="h-1 bg-gradient-to-r from-amber-500 to-rose-500"></div>
      <div class="p-5 space-y-3">
        <h2 class="text-xl font-bold flex items-center gap-2">
          <span class="text-2xl">⭐</span>
          <span>デイリーチャレンジ</span>
        </h2>
        <ul class="list-disc list-inside text-sm space-y-1">
          <li>毎日、全プレイヤーが <span class="font-bold">同じ問題</span> に挑戦できます</li>
          <li>総合と 20 ジャンル × 日本/世界のチャレンジがあり、それぞれ 1 日 1 回</li>
          <li>フリープランの回数制限にはカウントされません</li>
          <li>記録されたスコアは <span class="font-bold">グローバルランキング</span> に反映</li>
          <li>結果は画像・テキストで簡単にシェア可能</li>
          <li>🇵 過去のデイリーは <span class="text-amber-600 font-bold">プレミアム限定</span> でアーカイブ閲覧</li>
        </ul>
      </div>
    </div>

    <!-- ランキング -->
    <div class="glass-card overflow-hidden">
      <div class="h-1 bg-gradient-to-r from-yellow-400 to-orange-500"></div>
      <div class="p-5 space-y-3">
        <h2 class="text-xl font-bold flex items-center gap-2">
          <span class="text-2xl">🏆</span>
          <span>ランキング</span>
        </h2>
        <ul class="list-disc list-inside text-sm space-y-1">
          <li>ログインユーザーの累計スコア / 最高スコア / プレイ数を集計 (全モード対象)</li>
        <li>プレイするたびに XP が貯まり、レベルと称号が上がります。毎日遊ぶと 🔥 連続日数も伸びます</li>
          <li>期間: 今日 / 今週 / 今月 / 全期間</li>
          <li>ジャンル別・スコープ別の絞り込み可能</li>
          <li>匿名プレイはランキングに反映されません (アカウント登録でランクイン)</li>
        </ul>
      </div>
    </div>

    <!-- フレンド -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">👥 フレンド機能</h2>
      <ul class="list-disc list-inside text-sm space-y-1">
        <li>メールアドレスで他のユーザーを<span class="font-bold">招待</span></li>
        <li>承認されると<span class="font-bold">フレンド</span>として登録</li>
        <li>フレンド同士で <span class="font-bold">非同期チャレンジ</span> を送り合える (1 vs 1 のスコア対決)</li>
      </ul>
    </div>

    <!-- プラン -->
    <div class="glass-card overflow-hidden">
      <div class="h-1 bg-gradient-to-r from-amber-400 to-rose-500"></div>
      <div class="p-5 space-y-3">
        <h2 class="text-xl font-bold flex items-center gap-2">
          <span class="text-2xl">⭐</span>
          <span>Free / プレミアム</span>
        </h2>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-slate-200 text-xs text-slate-500">
                <th class="text-left py-2">機能</th>
                <th class="text-center">Free</th>
                <th class="text-center">プレミアム (¥500/月)</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr><td class="py-2">通常モード (A〜E) のプレイ</td><td class="text-center">合計 1 日 5 セッション</td><td class="text-center font-bold text-emerald-600">無制限</td></tr>
              <tr><td class="py-2">デイリーチャレンジ</td><td class="text-center">各 1 日 1 回</td><td class="text-center">各 1 日 1 回</td></tr>
              <tr><td class="py-2">過去デイリーアーカイブ</td><td class="text-center">−</td><td class="text-center font-bold text-emerald-600">無制限</td></tr>
              <tr><td class="py-2">ランキング参加</td><td class="text-center">✓</td><td class="text-center">✓</td></tr>
              <tr><td class="py-2">コミュニティジャンル閲覧</td><td class="text-center">✓</td><td class="text-center">✓</td></tr>
              <tr><td class="py-2">コミュニティジャンルのプレイ</td><td class="text-center">−</td><td class="text-center font-bold text-emerald-600">✓</td></tr>
              <tr><td class="py-2">コミュニティジャンル作成</td><td class="text-center">−</td><td class="text-center font-bold text-emerald-600">✓</td></tr>
              <tr><td class="py-2">フレンド機能</td><td class="text-center">✓</td><td class="text-center">✓</td></tr>
              <tr><td class="py-2">広告</td><td class="text-center">あり</td><td class="text-center font-bold text-emerald-600">なし</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- アカウント -->
    <div class="glass-card p-5 space-y-3">
      <h2 class="text-xl font-bold">🔐 アカウント</h2>
      <ul class="list-disc list-inside text-sm space-y-1">
        <li>メールアドレス + パスワード、または Google アカウントで登録</li>
        <li>登録時に確認メールが送信されます</li>
        <li>パスワードを忘れた場合はリセットメールで再設定可能</li>
        <li>アカウント情報はブラウザ間で同期 (匿名プレイは LocalStorage のみ)</li>
      </ul>
    </div>

    <!-- 倫理 -->
    <div class="glass-card p-5 space-y-3 ring-1 ring-slate-200">
      <h2 class="text-xl font-bold">⚖️ 出題対象外の記事</h2>
      <p class="text-sm text-slate-600">
        被害者・遺族の存在する記事をゲーム素材として消費することは適切でないと考えます。
        以下のような記事は出題対象から自動的に除外しています:
      </p>
      <ul class="list-disc list-inside text-sm space-y-0.5 text-slate-700">
        <li>殺人・傷害・性犯罪・誘拐などの事件</li>
        <li>戦争・テロ・虐殺・暴動</li>
        <li>災害・事故の犠牲者個人</li>
        <li>自殺・自死関連</li>
        <li>未成年が関わる事件</li>
        <li>存命人物のうち、本文に事件・逮捕などの記述がある記事</li>
        <li>「○○の一覧」のような索引記事 (ゲームに不向き)</li>
      </ul>
      <p class="text-xs text-slate-500">
        万一不適切な記事が表示された場合はお手数ですがご連絡ください。
      </p>
    </div>

    <!-- 帰属 -->
    <div class="text-xs text-slate-400 text-center pt-2">
      コンテンツは <a href="https://ja.wikipedia.org/" target="_blank" rel="noopener" class="underline">日本語版 Wikipedia</a> (CC BY-SA 4.0) を利用しています。
    </div>
  </section>
</template>
