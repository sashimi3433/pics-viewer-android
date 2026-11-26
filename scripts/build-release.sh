#!/usr/bin/env bash
# Bastion のアップロード鍵で署名済み AAB を作る。
# 鍵は一時ディレクトリにだけ書き出し、終了時に必ず消す。
set -euo pipefail
cd "$(dirname "$0")/.."

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
  ./gradlew --no-configuration-cache :app:bundleRelease :app:assembleRelease "$@"

AAB=app/build/outputs/bundle/release/app-release.aab
jarsigner -verify "$AAB" >/dev/null
echo "署名済み AAB: $AAB"
