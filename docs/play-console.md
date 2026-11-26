# Play Console 登録メモ

- パッケージ名: `com.sessapps.advanced_pics_viewer`（Play Console 上の既存アプリに合わせた。初回アップロード後は変更不可）
- アップロード鍵: Bastion `picsviewer-play-upload-keystore`（PKCS12、alias `upload`、有効期限 2054-04-13）
  - SHA-256: `4A:DE:56:E1:4C:08:FF:74:D3:15:C0:99:95:F0:B6:6A:B4:E4:85:F2:5C:7C:BC:3D:D0:CE:66:D5:77:EC:28:7B`
  - アプリ署名鍵は Play App Signing（Google 管理）を使う。初回アップロード時に既定のまま進める
- Play Developer API 用 SA: Bastion `gcp-sessapps-playconsoleapi-sa`（Play Console の「ユーザーと権限」でこのアプリへの権限が必要）

## リリース手順

```sh
scripts/build-release.sh          # Bastion の鍵で署名した AAB を作る
```

1. **初回だけ** Play Console → 内部テスト → 新しいリリース で
   `app/build/outputs/bundle/release/app-release.aab` を手動でアップロードする。
   アップロードしていないアプリは API から見えない（パッケージ名が決まるのがこの時点のため）
2. 2 回目以降は API でアップロードする
   ```sh
   NOTES="変更内容" scripts/upload-play.sh
   ```
   アプリを公開するまでは `STATUS=draft`（既定値）しか受け付けない。公開後は `STATUS=completed`
3. `versionCode` はリリースごとに `app/build.gradle.kts` で上げる

## ストアの掲載情報（下書き）

- アプリアイコン（512×512）: `docs/store/icon-512.png`（元データ `docs/store/icon.svg`）

- アプリ名: PICS ビューア
- 簡単な説明（80 字以内）:
  高度化PICS対応の歩行者用信号の色と残り時間を、手元のスマホで確認できます。
- 詳しい説明:
  高度化PICS（歩行者等支援情報通信システム）に対応した歩行者用信号機が Bluetooth で送っている信号情報を受信して、
  信号の色と残り時間を表示します。受信するだけのアプリで、信号機への送信や操作はしません。

  ・信号の色（赤／青／青点滅）と残り時間を大きく表示
  ・自分が渡る向きのチャンネルを選ぶと、色が変わったときに音声と振動で知らせます
  ・TalkBack、文字サイズの拡大、アニメーションの削除に対応
  ・全国の高度化PICS整備交差点を地図と近い順の一覧で確認できます
  ・信号機がなくても動作を確認できるデモ表示

  ※ 実際に道路を渡るときは、必ず信号機と周囲の安全を目で確かめてください。

- カテゴリ: 地図＆ナビ（またはツール）
- タグ候補: アクセシビリティ、ナビゲーション

## アプリのコンテンツ（申告の目安）

| 項目 | 内容 |
|---|---|
| プライバシーポリシー | **必須**（位置情報の権限を使うため）。https://advanced-pics.sessapps.com/privacy/ （元データ `site/`） |
| 広告 | なし |
| アクセス権限 | 制限なし（ログイン不要） |
| 対象年齢 | 13 歳以上を推奨（道路での利用を想定） |
| ニュースアプリ／健康アプリ等 | 該当しない |

### データ セーフティ

| データ | 収集 | 共有 | 備考 |
|---|---|---|---|
| おおよその位置・正確な位置 | しない | しない | 近い交差点を並べるために端末内だけで使う。送信しない |
| 受信ログ | しない | しない | 端末内に保持。ユーザーが共有シートで明示的に送るときだけ外に出る |
| その他 | しない | しない | アカウント、分析 SDK、広告 SDK なし |

- 「転送時の暗号化」: 地図タイルは HTTPS で取得
- 「データの削除リクエスト」: 収集しないので該当なし

### 権限の説明（審査で問われたとき用）

- `BLUETOOTH_SCAN`（`neverForLocation`）/ `BLUETOOTH_CONNECT`: 信号機の BLE アドバタイズの受信と Bluetooth のオン要求
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: Android 11 以下の BLE スキャンと、地図の「現在地から近い交差点」表示。バックグラウンドでは使わない
- `INTERNET`: OpenStreetMap の地図タイル取得

## 気をつけること

- 地図タイルは `tile.openstreetmap.org` を直接使っている。OSM の
  [タイル利用ポリシー](https://operations.osmfoundation.org/policies/tiles/) は配布アプリでの大量利用を想定しておらず、
  利用者が増えるとブロックされることがある。公開規模が大きくなる前に、商用のタイル提供元か自前のタイルに切り替えるのが安全
- 道路横断を支援するアプリなので、説明文とスクリーンショットに「最終判断は目視で」の注意書きを入れておく
