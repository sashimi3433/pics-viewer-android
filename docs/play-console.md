# Play Console 登録メモ

- パッケージ名: `com.sessapps.advanced_pics_viewer`（Play Console 上の既存アプリに合わせた。初回アップロード後は変更不可）
- アップロード鍵: Bastion `picsviewer-play-upload-keystore`（PKCS12、alias `upload`、有効期限 2054-04-13）
  - SHA-256: `4A:DE:56:E1:4C:08:FF:74:D3:15:C0:99:95:F0:B6:6A:B4:E4:85:F2:5C:7C:BC:3D:D0:CE:66:D5:77:EC:28:7B`
  - アプリ署名鍵は Play App Signing（Google 管理）を使う。初回アップロード時に既定のまま進める
- Play Developer API 用 SA: Bastion `gcp-sessapps-playconsoleapi-sa`（Play Console の「ユーザーと権限」でこのアプリへの権限が必要）

## リリース手順

```sh
scripts/build-release.sh          # Bastion の鍵で署名した AAB と APK を作る
scripts/build-release.sh --github-release  # さらにタグを push し、GitHub のリリースに APK を添付する
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

## ストアの掲載情報

`fastlane/metadata/android/ja-JP/` の内容を API で登録する（F-Droid も同じものを読む。Play Console 上で手で直すと、次に実行したとき上書きされる）。

```sh
scripts/upload-listing.sh
```

- 文言: `title.txt`、`short_description.txt`（80 字以内）、`full_description.txt`（4000 字以内）。連絡先はスクリプトの既定値（contact@sessapps.com、https://advanced-pics.sessapps.com/）
- アプリアイコン: `images/icon.png`（元データ `docs/store/icon.svg`）
- フィーチャーグラフィック: `images/featureGraphic.png`（元データ `docs/store/feature-graphic.html` を Chrome のヘッドレスで 1024×500 に書き出す）
- スクリーンショット（宣材画像）: `images/phoneScreenshots/`（1080×1920）、`sevenInchScreenshots/`（1440×2560）、`tenInchScreenshots/`（2560×1440）
  - `python3 docs/store/promo.py` で、`docs/store/raw/` の実機スクリーンショットを端末の枠にはめて見出しを付け、書き出す（Chrome のヘッドレスで描画）
  - 元のスクリーンショットはエミュレータで撮った。`wm size` / `wm density` で端末を切り替え（スマホ 1080×2400、7 インチ 1200×1920@320、10 インチ 1600×2560@320、広げたフォルダブル 2208×1840@420 と 1840×2208@420）、システム UI のデモモード（9:30・電池満タン）にして、アプリのデモ表示を撮る
- リリースノート: `changelogs/<versionCode>.txt`（F-Droid が使う。Play には `upload-play.sh` の `NOTES` で渡す）
- API で入れられないもの（Play Console で設定する）: カテゴリ（地図＆ナビ）、タグ、プライバシーポリシー URL、データ セーフティ

## アプリのコンテンツ（申告の目安）

| 項目 | 内容 |
|---|---|
| プライバシーポリシー | **必須**（位置情報の権限を使うため）。https://advanced-pics.sessapps.com/privacy/ （元データ `site/`） |
| 広告 | なし |
| アクセス権限 | 制限なし（ログイン不要） |
| コンテンツのレーティング | 全年齢（IARC 3+）にする。回答は下の「コンテンツのレーティング」 |
| ターゲット ユーザー（対象年齢） | 13 歳以上の年齢層だけを選ぶ。子どもの年齢層は選ばない（理由は下） |
| ニュースアプリ／健康アプリ等 | 該当しない |

### コンテンツのレーティング

誰でもインストールできる最も低い区分（IARC 3+、日本は「全年齢」）にする。アンケートは事実どおりに答えれば
すべて「いいえ」になる。

- カテゴリ: 「その他すべてのアプリの種類」（ゲーム、SNS、ニュース・教育、ストアのどれでもない）
- 暴力、性的な内容、不適切な言葉、薬物・アルコール・タバコ、ギャンブル、下品なユーモア: いいえ
- ユーザー同士のやり取りやコンテンツの共有: いいえ（受信ログの共有は Android の共有画面で利用者が自分で送るだけで、アプリ内で他の利用者に届く仕組みはない）
- 現在地を他のユーザーと共有する: いいえ（位置情報は端末内だけで使う）
- デジタル商品の購入: いいえ
- ウェブブラウザや検索エンジンのような、制限のないインターネット閲覧: いいえ（地図タイルを取得するだけ）

### ターゲット ユーザーを 13 歳以上にしている理由

レーティングが全年齢なら、対象年齢を 13 歳以上にしていても子どもはインストールして使える。
対象年齢に子どもの年齢層（12 歳以下）を入れると Google Play のファミリー ポリシーがかかり、
Bluetooth は Companion Device Manager 経由で使うことを求められる。このアプリは周りの不特定の
信号機の BLE アドバタイズを待ち受けるので、機器を 1 台ずつ登録する Companion Device Manager とは
合わない。子どもだけを対象にすると位置情報の権限も使えない。
ストアの掲載情報も、子ども向けに見える絵や言葉は使わない（対象年齢の判定に影響する）。

### データ セーフティ

| データ | 収集 | 共有 | 備考 |
|---|---|---|---|
| おおよその位置・正確な位置 | しない | しない | 近い交差点を並べるために端末内だけで使う。送信しない |
| 受信ログ | しない | しない | 端末内に保持。ユーザーが共有シートで明示的に送るときだけ外に出る |
| その他 | しない | しない | アカウント、分析 SDK、広告 SDK なし |

- 「転送時の暗号化」: 地図データは HTTPS で取得
- 地図のキャッシュ（表示した場所のタイル）は端末内だけに残るので「収集」に当たらない
- 「データの削除リクエスト」: 収集しないので該当なし

### 権限の説明（審査で問われたとき用）

- `BLUETOOTH_SCAN`（`neverForLocation`）/ `BLUETOOTH_CONNECT`: 信号機の BLE アドバタイズの受信と Bluetooth のオン要求
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: Android 11 以下の BLE スキャンと、地図の「現在地から近い交差点」表示。バックグラウンドでは使わない
- `INTERNET`: OpenFreeMap の地図データ取得

## 気をつけること

- 地図は OpenFreeMap（無料・キー不要）の公開タイルを使っている。表示した範囲のキャッシュは問題ないが、
  広い範囲をまとめてダウンロードする機能を足すときは [利用規約](https://openfreemap.org/tos/) の「Offline Downloads」の条件に従う
- 道路横断を支援するアプリなので、説明文とスクリーンショットに「最終判断は目視で」の注意書きを入れておく
