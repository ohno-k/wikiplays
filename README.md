# Wikiplays

Wikipedia の記事を見て、その記事の主題（タイトル）を当てる Web ゲーム。

## 技術スタック

- **フロントエンド**: Vue 3 + TypeScript + Vite + TailwindCSS + Vue Router
- **バックエンド**: Spring Boot 3.3 (Java 17) + Spring Data JPA + Spring Security
- **DB**: PostgreSQL (本番) / H2 (ローカル開発)
- **インフラ**: Docker Compose

## ディレクトリ構成

```
wikiplays/
├── frontend/             # Vue アプリ
├── backend/              # Spring Boot アプリ
└── docker-compose.yml    # PostgreSQL (本番想定)
```

## ローカル開発手順

### 1. バックエンド起動

ローカル開発では H2 (インメモリ) を使うので、Postgres は不要です。
Maven Wrapper (`mvnw`) を同梱しているので、Maven のインストールは不要です (Java 17 以上は必要)。

PowerShell の場合:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

bash / WSL の場合:
```bash
cd backend
./mvnw spring-boot:run
```

- 起動 URL: http://localhost:8080
- H2 コンソール: http://localhost:8080/h2-console
- 動作確認: `curl http://localhost:8080/api/quiz/random`

### 2. フロントエンド起動

```bash
cd frontend
npm install
npm run dev
```

- 起動 URL: http://localhost:5173
- `/api/*` は Vite の dev proxy で 8080 のバックエンドに転送されます。

### 3. (任意) PostgreSQL を使う場合

```bash
docker compose up -d db
cd backend
./mvnw spring-boot:run "-Dspring-boot.run.profiles="
```

`-Dspring-boot.run.profiles=` で空指定すると `local` プロファイルが外れ、`application.yml` のデフォルトの Postgres 接続が使われます。

## ゲーム仕様 (現状)

- `GET /api/quiz/random` がランダムな Wikipedia 記事を返す
- レスポンス:
  ```json
  {
    "title": "正解のタイトル",
    "maskedContent": "本文中のタイトル文字列を ???? に置換したテキスト",
    "pageUrl": "https://ja.wikipedia.org/wiki/..."
  }
  ```
- 答え合わせはフロント側で `title` と入力値を比較（MVP）

## 今後の予定

- ユーザー登録 / ログイン (JWT)
- スコア記録
- サブスクリプション課金 (記事ジャンル選択や履歴閲覧などのプレミアム機能)
