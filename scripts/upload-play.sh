#!/usr/bin/env bash
# 署名済み AAB を Play Developer API でアップロードする（2 回目以降のリリース用）。
# 最初の 1 本は Play Console の画面からアップロードする必要がある（パッケージ名がそこで確定する）。
#
#   TRACK=internal STATUS=draft NOTES="..." scripts/upload-play.sh [aab]
#
# 未公開のアプリには status=draft しか送れない。公開後は STATUS=completed にする。
set -euo pipefail
cd "$(dirname "$0")/.."

AAB_PATH="${1:-app/build/outputs/bundle/release/app-release.aab}"
PACKAGE_NAME="${PACKAGE_NAME:-com.sessapps.advanced_pics_viewer}"
TRACK="${TRACK:-internal}"
STATUS="${STATUS:-draft}"
NOTES="${NOTES:-不具合の修正と改善}"

[[ -f "$AAB_PATH" ]] || scripts/build-release.sh
jarsigner -verify "$AAB_PATH" >/dev/null

bastion secret get --name gcp-sessapps-playconsoleapi-sa --raw |
  uv run -q --with google-auth --with requests python3 -I -c '
import json, sys, requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

pkg, track, status, notes, aab = sys.argv[1:6]
creds = service_account.Credentials.from_service_account_info(
    json.load(sys.stdin), scopes=["https://www.googleapis.com/auth/androidpublisher"])
creds.refresh(Request())
h = {"Authorization": f"Bearer {creds.token}"}
base = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{pkg}/edits"

edit = requests.post(base, headers=h, json={})
edit.raise_for_status()
eid = edit.json()["id"]

def fail(step, r):
    print(f"{step} failed ({r.status_code}): {r.text}", file=sys.stderr)
    requests.delete(f"{base}/{eid}", headers=h)
    sys.exit(1)

with open(aab, "rb") as f:
    up = requests.post(
        f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{pkg}/edits/{eid}/bundles?uploadType=media",
        headers={**h, "Content-Type": "application/octet-stream"}, data=f)
if up.status_code != 200:
    fail("Upload", up)
vc = up.json()["versionCode"]
print(f"Uploaded versionCode={vc}")

tr = requests.put(f"{base}/{eid}/tracks/{track}", headers=h, json={
    "track": track,
    "releases": [{
        "versionCodes": [str(vc)],
        "status": status,
        "releaseNotes": [{"language": "ja-JP", "text": notes}],
    }],
})
if tr.status_code != 200:
    fail("Track update", tr)

cm = requests.post(f"{base}/{eid}:commit", headers=h)
if cm.status_code != 200:
    fail("Commit", cm)
print(f"Committed to {track} ({status}), versionCode={vc}")
' "$PACKAGE_NAME" "$TRACK" "$STATUS" "$NOTES" "$AAB_PATH"
