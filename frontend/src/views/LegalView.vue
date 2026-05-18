<script setup lang="ts">
const CONTACT_EMAIL = 'wikiplays416@gmail.com'
const SITE_NAME = 'Wikiplays'
const SITE_URL = 'https://wikiplays.me'
// 運営責任者の本名はここに記載 (公開必須項目)
const OPERATOR_NAME = '大野 虹輝'
</script>

<template>
  <section class="space-y-6 animate-fade-in">
    <router-link to="/" class="text-sm text-blue-600 hover:underline">← ホームに戻る</router-link>

    <div class="glass-card p-6 space-y-2">
      <div class="text-xs font-mono text-slate-500">LEGAL NOTICE</div>
      <h1 class="text-3xl font-bold tracking-tight brand-text">特定商取引法に基づく表記</h1>
      <p class="text-sm text-slate-600">
        特定商取引法第 11 条 (通信販売についての広告) に基づく表記を以下に掲載します。
      </p>
    </div>

    <div class="glass-card p-5 space-y-4">
      <dl class="grid grid-cols-1 sm:grid-cols-[10rem_1fr] gap-x-4 gap-y-3 text-sm leading-relaxed">

        <dt class="font-bold text-slate-700">販売事業者</dt>
        <dd class="text-slate-800">{{ SITE_NAME }} ({{ OPERATOR_NAME }})</dd>

        <dt class="font-bold text-slate-700">運営責任者</dt>
        <dd class="text-slate-800">{{ OPERATOR_NAME }}</dd>

        <dt class="font-bold text-slate-700">所在地</dt>
        <dd class="text-slate-800">
          ご請求をいただいた場合、遅滞なく開示いたします。<br />
          <span class="text-xs text-slate-500">
            開示をご希望の場合は下記メールアドレスまでご連絡ください。
          </span>
        </dd>

        <dt class="font-bold text-slate-700">電話番号</dt>
        <dd class="text-slate-800">
          ご請求をいただいた場合、遅滞なく開示いたします。<br />
          <span class="text-xs text-slate-500">
            お問い合わせは原則メールにて承っております。
          </span>
        </dd>

        <dt class="font-bold text-slate-700">メールアドレス</dt>
        <dd class="text-slate-800">
          <a :href="`mailto:${CONTACT_EMAIL}`" class="text-blue-600 hover:underline">{{ CONTACT_EMAIL }}</a>
        </dd>

        <dt class="font-bold text-slate-700">ウェブサイト URL</dt>
        <dd class="text-slate-800">
          <a :href="SITE_URL" class="text-blue-600 hover:underline">{{ SITE_URL }}</a>
        </dd>

        <dt class="font-bold text-slate-700">販売価格</dt>
        <dd class="text-slate-800">
          各プランの購入画面に表示される価格 (消費税込) のとおりです。
          <ul class="mt-1 list-disc list-inside text-slate-700">
            <li>月額プラン: 500 円 / 1 ヶ月 (税込)</li>
            <li>3 ヶ月プラン: 1,300 円 / 3 ヶ月 (税込)</li>
            <li>6 ヶ月プラン: 2,000 円 / 6 ヶ月 (税込)</li>
          </ul>
        </dd>

        <dt class="font-bold text-slate-700">商品代金以外の<br />必要料金</dt>
        <dd class="text-slate-800">
          インターネット接続料金、通信料金等はお客様のご負担となります。
        </dd>

        <dt class="font-bold text-slate-700">お支払い方法</dt>
        <dd class="text-slate-800">
          クレジットカード決済 (Stripe 経由) <br />
          <span class="text-xs text-slate-500">
            利用可能ブランドは Stripe の決済画面に表示されるものに準じます。
          </span>
        </dd>

        <dt class="font-bold text-slate-700">お支払い時期</dt>
        <dd class="text-slate-800">
          各プランの登録手続き完了時に初回課金が行われます。<br />
          <span class="text-emerald-700 font-bold">
            初回 7 日間の無料体験期間中はお支払いは発生せず、無料体験期間終了時に自動的に課金されます。
          </span>
          以降は選択されたプランの期間 (1 / 3 / 6 ヶ月) ごとに自動更新・自動課金されます。
        </dd>

        <dt class="font-bold text-slate-700">サービス提供時期</dt>
        <dd class="text-slate-800">
          決済完了後 (または無料体験開始後) 即時にプレミアム機能が解放されます。
        </dd>

        <dt class="font-bold text-slate-700">解約・キャンセル<br />について</dt>
        <dd class="text-slate-800 space-y-1">
          <p>
            アカウントページの「支払い・解約の管理」よりいつでも解約手続きが可能です。
          </p>
          <p>
            <strong class="text-emerald-700">無料体験期間中に解約された場合、課金は発生しません。</strong>
            無料体験期間終了後 (もしくは有料期間中) に解約された場合、現在の有料期間終了時点までは引き続きサービスをご利用いただけます。
            <strong>期間途中の解約による日割り計算・返金は行っておりません。</strong>
          </p>
        </dd>

        <dt class="font-bold text-slate-700">返品・返金</dt>
        <dd class="text-slate-800">
          デジタルサービスの性質上、お客様のご都合による返金は原則として承っておりません。
          ただし当社の責に帰すべき重大な不具合等によりサービスを利用いただけなかった場合は、個別にご相談の上対応いたします。
        </dd>

        <dt class="font-bold text-slate-700">動作環境</dt>
        <dd class="text-slate-800">
          最新の主要ウェブブラウザ (Chrome / Safari / Firefox / Edge) の最新版を推奨します。
          JavaScript / Cookie が有効である必要があります。
        </dd>

      </dl>
    </div>

    <div class="text-xs text-slate-500 px-1">
      最終更新日: 2026 年 5 月 19 日<br />
      本表記は予告なく変更される場合があります。最新の内容は本ページにてご確認ください。
    </div>

    <div class="text-center text-xs text-slate-400 pt-4">
      <p>© {{ SITE_NAME }}. All wiki content under CC BY-SA 4.0.</p>
    </div>
  </section>
</template>
