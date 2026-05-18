# Wikiplays

日本語版 Wikipedia の記事から主題を当てる Web クイズゲーム。

公開サイト: https://wikiplays.me

## 主な機能

- **メインモード (Aモード)**: 段落を下から開示しながら 1 文字ずつタイトル入力 (部分点あり)
- **20+ ジャンル × 日本/世界スコープ** から挑戦するジャンルを選択
- **コミュニティジャンル**: プレミアム会員は独自カテゴリを作成・共有
- **デイリーチャレンジ**: 全プレイヤー共通の 1 日 1 問 + 全国ランキング
- **デイリーアーカイブ** (プレミアム): 過去のデイリーをいつでも挑戦
- **フレンド / 非同期チャレンジ**: スコアを送りつけて競う
- **倫理フィルタ**: 事件・戦争・災害など被害者のいる記事は出題から除外

## 技術スタック

- **フロントエンド**: Vue 3 (Composition API) + TypeScript + Vite + TailwindCSS + Vue Router + Pinia
- **バックエンド**: Spring Boot 3.3 (Java 17) + Spring Data JPA + Spring Security (JWT)
- **DB**: PostgreSQL (本番) / H2 インメモリ (ローカル開発)
- **認証**: メール/パスワード (Spring Mail でメール確認) + Google OAuth2
- **決済**: Stripe Checkout + Customer Portal + Webhook
- **記事取得**: MediaWiki API (キャッシュは DB)
- **ホスティング**: Render (Web Service + Static Site + Postgres)

## ディレクトリ構成

```
wikiplays/
├── frontend/                   # Vue アプリ
│   ├── src/views/              # 画面 (Home, Genre, Play, Account, About, ...)
│   ├── src/composables/        # useAuth, useArticleQueue, etc.
│   └── src/components/         # 共通 UI
├── backend/                    # Spring Boot アプリ
│   ├── src/main/java/com/wikiplays/
│   │   ├── controller/         # REST API
│   │   ├── service/            # WikipediaService, StripeService, AuthService, ...
│   │   ├── entity/             # JPA エンティティ
│   │   ├── repository/         # Spring Data リポジトリ
│   │   ├── config/             # SecurityConfig, WebConfig, JwtFilter
│   │   └── filter/             # ArticleFilter (倫理フィルタ含む)
│   └── src/main/resources/
│       ├── application.yml             # デフォルト + 本番想定
│       ├── application-local.yml       # ローカル開発 (H2)
│       └── application-secrets.yml     # ローカル秘密 (gitignore)
├── docker-compose.yml          # ローカル Postgres 用 (任意)
└── .agents/skills/             # Stripe AI Skills
```

## ローカル開発

### 必要環境

- Java 17 以上
- Node.js 18 以上
- (任意) Docker (Postgres を使う場合)

### バックエンド起動

```bash
cd backend
./mvnw spring-boot:run
# Windows PowerShell の場合:
# .\mvnw.cmd spring-boot:run
```

- 起動 URL: http://localhost:8080
- H2 コンソール: http://localhost:8080/h2-console
- デフォルトプロファイルは `local` (H2 インメモリ)

### フロントエンド起動

```bash
cd frontend
npm install
npm run dev
```

- 起動 URL: http://localhost:5173
- Vite の dev proxy で `/api/*` が 8080 のバックエンドに転送される

### Stripe / OAuth / SMTP の秘密設定

`backend/src/main/resources/application-secrets.yml` (gitignore 済) に記述:

```yaml
wikiplays:
  stripe:
    secret-key: sk_test_xxx
    webhook-secret: whsec_xxx
    price-id-1m: price_xxx
    price-id-3m: price_xxx
    price-id-6m: price_xxx
  jwt:
    secret: <32+ 文字のランダム文字列>
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: xxx
            client-secret: xxx
  mail:
    host: smtp.gmail.com
    username: xxx
    password: xxx
```

Stripe Webhook をローカルで受け取るには Stripe CLI を使う:

```bash
stripe listen --forward-to localhost:8080/api/stripe/webhook
```

## 本番デプロイ

Render を使用。詳細手順は `docs/` 配下を参照 (準備中)。

環境変数 (Web Service):

| 変数 | 用途 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | Render Postgres の Internal URL |
| `WIKIPLAYS_JWT_SECRET` | JWT 署名鍵 |
| `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe ライブキー |
| `STRIPE_PRICE_ID_1M` / `_3M` / `_6M` | 各プラン Price ID |
| `GOOGLE_OAUTH_CLIENT_ID` / `_SECRET` | (任意) Google OAuth |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | SMTP |
| `WIKIPLAYS_ALLOWED_ORIGINS` | 例: `https://wikiplays.me,https://www.wikiplays.me` |
| `WIKIPLAYS_FRONTEND_URL` | 例: `https://wikiplays.me` |

## サブスクリプションプラン

| プラン | 価格 | 月あたり |
| --- | --- | --- |
| 1 ヶ月 | ¥500 | ¥500 |
| 3 ヶ月 | ¥1,300 | ¥433 (13% お得) |
| 6 ヶ月 | ¥2,000 | ¥333 (33% お得) |

プレミアム特典: 無制限プレイ、デイリーアーカイブ、コミュニティジャンル作成、詳細統計、広告非表示。

## 倫理ポリシー

被害者・遺族の存在する記事をクイズ素材として消費しないため、以下を自動除外:
- 殺人 / 傷害 / 性犯罪 / 誘拐
- 戦争 / テロ / 虐殺 / 暴動
- 災害 / 事故の犠牲者個人
- 自殺 / 自死関連
- 未成年が関わる事件

不適切な記事を見つけた場合は `wikiplays416@gmail.com` まで。

## ライセンス

- 出題コンテンツは [日本語版 Wikipedia](https://ja.wikipedia.org/) を元にしており、[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.ja) で提供されています。
- アプリケーションコードのライセンスは未定 (現状非公開リポジトリ運用)。
