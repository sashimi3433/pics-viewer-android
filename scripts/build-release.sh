#!/usr/bin/env bash
# Bastion のアップロード鍵で署名済みの AAB と APK を作る。
# 鍵は一時ディレクトリにだけ書き出し、終了時に必ず消す。
#
#   scripts/build-release.sh [--github-release] [gradle の引数...]
#
# --github-release を付けると、ビルドした APK を GitHub のリリース v<versionName> に添付する
# （Obtainium と LP の「APK」ボタンはここを読む）。
#   - タグがなければ HEAD に付けて push する。既にあって HEAD と違えば止まる
#   - リリースノートは fastlane/metadata/android/ja-JP/changelogs/<versionCode>.txt から作る
#   - リリースが既にあれば APK だけ差し替える
# 先に main へマージし、main を最新にした状態で実行する。
# DRY_RUN=1 を付けると、GitHub へは何も送らずに実行内容だけ表示する。
set -euo pipefail
cd "$(dirname "$0")/.."

GITHUB_RELEASE=0
GRADLE_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --github-release) GITHUB_RELEASE=1 ;;
    *) GRADLE_ARGS+=("$arg") ;;
  esac
done

if [[ "$GITHUB_RELEASE" == 1 && -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "コミットしていない変更があります。リリースはコミット済みの内容から作ってください。" >&2
  exit 1
fi

SECRET="${UPLOAD_KEY_SECRET:-picsviewer-play-upload-keystore}"
TMP="$(mktemp -d)"
chmod 700 "$TMP"
trap 'rm -rf "$TMP"' EXIT

bastion secret get --name "$SECRET" --raw > "$TMP/key.json"
read_field() { /usr/bin/plutil -extract "$1" raw -o - "$TMP/key.json"; }
read_field keystore_base64 | base64 -d > "$TMP/upload.p12"

PICS_UPLOAD_STORE_FILE="$TMP/upload.p12" \
PICS_UPLOAD_STORE_PASSWORD="$(read_field store_password)" \
PICS_UPLOAD_KEY_ALIAS="$(read_field key_alias)" \
PICS_UPLOAD_KEY_PASSWORD="$(read_field key_password)" \
  ./gradlew --no-configuration-cache :app:bundleRelease :app:assembleRelease ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"}
rm -f "$TMP/key.json" "$TMP/upload.p12"

AAB=app/build/outputs/bundle/release/app-release.aab
APK=app/build/outputs/apk/release/app-release.apk
jarsigner -verify "$AAB" >/dev/null
jarsigner -verify "$APK" >/dev/null
echo "署名済み AAB: $AAB"
echo "署名済み APK: $APK"

[[ "$GITHUB_RELEASE" == 1 ]] || exit 0

# ---- GitHub のリリース ----
META=app/build/outputs/apk/release/output-metadata.json
VERSION_NAME="$(/usr/bin/plutil -extract elements.0.versionName raw -o - "$META")"
VERSION_CODE="$(/usr/bin/plutil -extract elements.0.versionCode raw -o - "$META")"
TAG="v$VERSION_NAME"
CHANGELOG="fastlane/metadata/android/ja-JP/changelogs/$VERSION_CODE.txt"
ASSET="$TMP/advanced-pics-viewer-$VERSION_NAME.apk"
run() { if [[ "${DRY_RUN:-0}" == 1 ]]; then echo "[DRY_RUN] $*"; else "$@"; fi; }

[[ -f "$CHANGELOG" ]] || { echo "$CHANGELOG がありません。リリースノートを書いてください。" >&2; exit 1; }
cp "$APK" "$ASSET"

git fetch -q origin --tags
HEAD_SHA="$(git rev-parse HEAD)"
if TAG_SHA="$(git rev-parse -q --verify "refs/tags/$TAG^{commit}")"; then
  if [[ "$TAG_SHA" != "$HEAD_SHA" ]]; then
    echo "$TAG は別のコミット（${TAG_SHA:0:7}）に付いています。HEAD は ${HEAD_SHA:0:7} です。" >&2
    exit 1
  fi
else
  run git tag "$TAG" "$HEAD_SHA"
fi
run git push -q origin "refs/tags/$TAG"

{
  echo "## 高度化PICSビューア $VERSION_NAME"
  echo
  sed 's/^/- /' "$CHANGELOG"
  cat <<EOF

### インストール

\`advanced-pics-viewer-$VERSION_NAME.apk\` をダウンロードして開いてください（提供元不明のアプリの許可が必要です）。
[Obtainium](https://github.com/ImranR98/Obtainium) にこのリポジトリを追加すると、新しいリリースを自動で受け取れます。

この APK は Google Play 版と署名が異なります。Google Play 版から切り替えるときは、一度アンインストールしてから入れてください。

動作環境: Android 8.0 以上、Bluetooth Low Energy 対応端末
EOF
} > "$TMP/notes.md"

if gh release view "$TAG" >/dev/null 2>&1; then
  run gh release upload "$TAG" "$ASSET" --clobber
  echo "GitHub のリリース $TAG の APK を差し替えました"
else
  run gh release create "$TAG" "$ASSET" --title "高度化PICSビューア $VERSION_NAME" --notes-file "$TMP/notes.md" --latest
  echo "GitHub のリリース $TAG を作りました"
fi
[[ "${DRY_RUN:-0}" == 1 ]] && { echo "--- リリースノート ---"; cat "$TMP/notes.md"; }
exit 0
