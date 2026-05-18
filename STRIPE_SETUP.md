# Stripe 設定手順

サブスク機能を実際に動かすには Stripe 側の設定が必要です。テスト環境で動作確認、本番切替の流れです。

## 1. Stripe アカウント作成

1. https://stripe.com に登録 (無料、本人確認は本番で課金開始する時に必要)
2. ダッシュボード → **「テスト環境」モード** で操作 (右上トグル)

## 2. 商品とプライスを作成

1. ダッシュボード → **商品** → **+ 商品を追加**
2. 入力:
   - 商品名: `Wikiplays プレミアム`
   - 説明: `無制限プレイ、過去アーカイブ、詳細統計、ジャンル作成`
   - 価格設定: **定期** / **月額** / **¥500**
3. 保存後、生成された `price_xxxxx` をメモ (これが `STRIPE_PRICE_ID_PREMIUM`)

## 3. API キー取得

1. ダッシュボード → **開発者** → **API キー**
2. **シークレットキー** (`sk_test_xxxxx`) をコピー (これが `STRIPE_SECRET_KEY`)

## 4. Webhook 設定

ローカル開発時は Stripe CLI を使うのが楽:

```bash
# Stripe CLI インストール (Windows)
# https://stripe.com/docs/stripe-cli からダウンロード

stripe login
stripe listen --forward-to localhost:8080/api/stripe/webhook
```

Stripe CLI が `whsec_xxxxx` を出力します。これが `STRIPE_WEBHOOK_SECRET`。

本番では:
1. ダッシュボード → **開発者** → **Webhook** → **+ エンドポイントを追加**
2. URL: `https://your-domain.com/api/stripe/webhook`
3. 受信イベント:
   - `checkout.session.completed`
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
   - `invoice.payment_failed`
4. 作成後表示される署名シークレットを `STRIPE_WEBHOOK_SECRET` に設定

## 5. 環境変数の設定

### ローカル開発

`backend/src/main/resources/application-local.yml` に追記 (このファイルは git 管理外推奨):

```yaml
wikiplays:
  stripe:
    secret-key: sk_test_xxxxx
    webhook-secret: whsec_xxxxx
    price-id-premium: price_xxxxx
```

または PowerShell で環境変数:
```powershell
$env:STRIPE_SECRET_KEY="sk_test_xxxxx"
$env:STRIPE_WEBHOOK_SECRET="whsec_xxxxx"
$env:STRIPE_PRICE_ID_PREMIUM="price_xxxxx"
.\mvnw.cmd spring-boot:run
```

### Render 本番

ダッシュボード → サービス → **Environment** で:
- `STRIPE_SECRET_KEY`: 本番用 `sk_live_xxxxx`
- `STRIPE_WEBHOOK_SECRET`: 本番 Webhook の `whsec_xxxxx`
- `STRIPE_PRICE_ID_PREMIUM`: 本番モードで作った `price_xxxxx`
- `JWT_SECRET`: 強力なランダム文字列 (例: `openssl rand -base64 64`)
- `FRONTEND_URL`: `https://wikiplays.com`

## 6. テスト用クレジットカード

Stripe テスト環境では実際のお金は動きません。以下のテストカードを使用:

- 番号: `4242 4242 4242 4242`
- 有効期限: 未来の任意の日付 (`12/34` など)
- CVC: 任意の 3 桁 (`123` など)
- 郵便番号: 任意

## 7. 動作確認の流れ

1. アプリでアカウント登録
2. アカウント画面で「プレミアムにアップグレード」
3. Stripe Checkout にリダイレクト
4. テストカードで支払い完了
5. アプリに戻ると `?subscribed=1` でメッセージ表示
6. Webhook が自動で `Subscription` を `PREMIUM` に更新
7. `/account` で「⭐ PREMIUM」表示確認
8. 「支払い・解約の管理」で Stripe Customer Portal へ
