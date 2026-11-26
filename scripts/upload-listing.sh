#!/usr/bin/env bash
# ストアの掲載情報と画像（fastlane/metadata/android/ja-JP。F-Droid も同じものを読む）を
# Play Developer API で登録する。画像は種類ごとに一度消してから入れ直す。
#
#   scripts/upload-listing.sh
#
# 使う画像:
#   title.txt / short_description.txt / full_description.txt
#   images/icon.png                  アプリアイコン（512×512、元データ docs/store/icon.svg）
#   images/featureGraphic.png        フィーチャーグラフィック（1024×500、元データ docs/store/feature-graphic.html）
#   images/phoneScreenshots/*.png    スマートフォンのスクリーンショット（ファイル名順）
set -euo pipefail
cd "$(dirname "$0")/.."

PACKAGE_NAME="${PACKAGE_NAME:-com.sessapps.advanced_pics_viewer}"
CONTACT_EMAIL="${CONTACT_EMAIL:-contact@sessapps.com}"
CONTACT_WEBSITE="${CONTACT_WEBSITE:-https://advanced-pics.sessapps.com/}"

bastion secret get --name gcp-sessapps-playconsoleapi-sa --raw |
  uv run -q --with google-auth --with requests python3 -I -c '
import glob, json, sys, requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

pkg, email, website = sys.argv[1:4]
lang = "ja-JP"
meta = f"fastlane/metadata/android/{lang}"
text = lambda name: open(f"{meta}/{name}.txt").read().strip()

creds = service_account.Credentials.from_service_account_info(
    json.load(sys.stdin), scopes=["https://www.googleapis.com/auth/androidpublisher"])
creds.refresh(Request())
h = {"Authorization": f"Bearer {creds.token}"}
api = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + pkg
upload = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/" + pkg

edit = requests.post(f"{api}/edits", headers=h, json={})
edit.raise_for_status()
eid = edit.json()["id"]
base = f"{api}/edits/{eid}"

def check(step, r):
    if r.status_code // 100 != 2:
        print(f"{step} failed ({r.status_code}): {r.text}", file=sys.stderr)
        requests.delete(base, headers=h)
        sys.exit(1)

check("Details", requests.patch(f"{base}/details", headers=h, json={
    "defaultLanguage": lang,
    "contactEmail": email,
    "contactWebsite": website,
}))
check("Listing", requests.put(f"{base}/listings/{lang}", headers=h, json={
    "language": lang,
    "title": text("title"),
    "shortDescription": text("short_description"),
    "fullDescription": text("full_description"),
}))

images = {
    "icon": [f"{meta}/images/icon.png"],
    "featureGraphic": [f"{meta}/images/featureGraphic.png"],
    "phoneScreenshots": sorted(glob.glob(f"{meta}/images/phoneScreenshots/*.png")),
}
for kind, files in images.items():
    check(f"Delete {kind}", requests.delete(f"{base}/listings/{lang}/{kind}", headers=h))
    for path in files:
        with open(path, "rb") as f:
            r = requests.post(f"{upload}/edits/{eid}/listings/{lang}/{kind}?uploadType=media",
                              headers={**h, "Content-Type": "image/png"}, data=f)
        check(f"Upload {path}", r)
        print(f"Uploaded {kind}: {path}")

check("Commit", requests.post(f"{base}:commit", headers=h))
print(f"Committed listing ({lang})")
' "$PACKAGE_NAME" "$CONTACT_EMAIL" "$CONTACT_WEBSITE"
