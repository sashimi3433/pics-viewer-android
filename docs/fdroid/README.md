# F-Droid での公開

F-Droid は公式リポジトリ側でソースからビルドし、F-Droid の鍵で署名して配る。
アプリの説明・画像・リリースノートは、このリポジトリの `fastlane/metadata/android/ja-JP/` を読む（Play と共通）。

## 初回の申請

1. GitLab で [fdroiddata](https://gitlab.com/fdroid/fdroiddata) をフォークし、
   `metadata/com.sessapps.advanced_pics_viewer.yml` として [下書き](com.sessapps.advanced_pics_viewer.yml) を置く
2. 手元で確かめる場合（任意）:
   ```sh
   fdroid readmeta && fdroid lint com.sessapps.advanced_pics_viewer
   fdroid build -v -l com.sessapps.advanced_pics_viewer
   ```
3. マージリクエストを出す。テンプレートの「新しいアプリ」のチェックリストに答える

## リリースのたび

1. `app/build.gradle.kts` の `versionCode` / `versionName` を上げる
2. `fastlane/metadata/android/ja-JP/changelogs/<versionCode>.txt` にリリースノートを書く
3. main にマージしたら `v<versionName>` のタグを打って push する
   ```sh
   git tag v1.5.2 && git push origin v1.5.2
   ```
   F-Droid はタグを見て新しい版を見つけ（`UpdateCheckMode: Tags`）、ビルド設定を自動で足す（`AutoUpdateMode: Version`）。
   反映までは数日かかる

## 決めてあること・気をつけること

- ライセンス: GPL-3.0-or-later（`LICENSE`）
- 署名: F-Droid 版は F-Droid の鍵で署名されるので、Play 版との間では上書き更新できない（入れ替えるときはアンインストールが要る）。
  同じ鍵にしたくなったら、再現可能ビルドにして `Binaries:` と `AllowedAPKSigningKeys:` を設定する
- APK に依存関係メタデータ（Google の鍵で暗号化された情報）を入れない設定にしてある（`dependenciesInfo.includeInApk = false`）
- 交差点データは警察庁の公開データ（公共データ利用規約 第1.0版）を加工したもの。出典と「加工して作成」の表記をアプリ・README・説明文に入れてある
- 地図は MapLibre（オープンソース）と OpenFreeMap（無料の公開タイル）。Google Play 開発者サービスには依存しない
- ビルドには AGP 9.5 / Gradle 9.9 / compileSdk 37.1 を使っている。F-Droid のビルドサーバーがこの SDK に追いついていないとビルドが失敗するので、
  マージリクエストのパイプラインで確認する
