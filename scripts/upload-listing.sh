#!/usr/bin/env bash
# ストアの掲載情報（docs/store/listing.json）と画像を Play Developer API で登録する。
# 画像は種類ごとに一度消してから docs/store の内容で入れ直す。
#
#   scripts/upload-listing.sh
#
# 使う画像:
#   docs/store/icon-512.png         アプリアイコン（512×512）
#   docs/store/feature-graphic.png  フィーチャーグラフィック（1024×500、元データ feature-graphic.html）
#   docs/store/phone/*.png          スマートフォンのスクリーンショット（ファイル名順）
set -euo pipefail
cd "$(dirname "$0")/.."

PACKAGE_NAME="${PACKAGE_NAME:-com.sessapps.advanced_pics_viewer}"

bastion secret get --name gcp-sessapps-playconsoleapi-sa --raw |
  uv run -q --with google-auth --with requests python3 -I -c '
import glob, json, sys, requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

pkg = sys.argv[1]
listing = json.load(open("docs/store/listing.json"))
lang = listing["language"]

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
    "contactEmail": listing["contactEmail"],
    "contactWebsite": listing["contactWebsite"],
}))
check("Listing", requests.put(f"{base}/listings/{lang}", headers=h, json={
    "language": lang,
    "title": listing["title"],
    "shortDescription": listing["shortDescription"],
    "fullDescription": listing["fullDescription"],
}))

images = {
    "icon": ["docs/store/icon-512.png"],
    "featureGraphic": ["docs/store/feature-graphic.png"],
    "phoneScreenshots": sorted(glob.glob("docs/store/phone/*.png")),
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
' "$PACKAGE_NAME"
