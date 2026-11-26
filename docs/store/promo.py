"""ストア掲載用の宣材画像を作る。

docs/store/raw/ の実機スクリーンショットを端末の枠にはめ、見出しを付けて
fastlane/metadata/android/ja-JP/images/ の各フォルダに書き出す（Chrome のヘッドレスで描画）。

    python3 docs/store/promo.py
"""
import html
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
RAW = ROOT / "docs/store/raw"
OUT = ROOT / "fastlane/metadata/android/ja-JP/images"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

LIGHT = {"bg": "#eef4fb", "fg": "#18181b", "sub": "#52525b"}
THEMES = {
    "blue": LIGHT,
    "green": {"bg": "#e9f6ee", "fg": "#18181b", "sub": "#52525b"},
    "red": {"bg": "#fdeeed", "fg": "#18181b", "sub": "#52525b"},
    "sand": {"bg": "#f6f1e7", "fg": "#18181b", "sub": "#52525b"},
    "dark": {"bg": "#18181b", "fg": "#fafafa", "sub": "#a1a1aa"},
}

# (出力名, 元画像, 見出し, 補足, 配色)
SLIDES = {
    "home": ("phone-home", "信号の色と残り時間が<br>手元でわかる", "高度化PICS対応の信号機が送る情報を受信して表示", "green"),
    "focus": ("phone-focus", "渡る向きを選べば<br>変わったときに知らせる", "注目したチャンネルの変化を音声と振動でお知らせ", "red"),
    "map": ("phone-map", "全国596か所の<br>対応交差点を地図で", "現在地から近い順にすぐ見つかる", "blue"),
    "access": ("phone-dark", "TalkBack・大きな文字<br>ダークテーマに対応", "読み上げと見やすさに配慮しました", "dark"),
}

FOLD_TITLE = "フォルダブルは<br>2列で見やすく"
FOLD_SUB = "広げると信号とログを並べて表示"

CSS = """
* { box-sizing: border-box; margin: 0; }
html, body { width: %(w)dpx; height: %(h)dpx; overflow: hidden; }
body { background: %(bg)s; color: %(fg)s; position: relative;
  font-family: "Hiragino Sans", "Noto Sans JP", system-ui, sans-serif; }
h1 { font-weight: 800; letter-spacing: 0.01em; line-height: 1.32; }
p { color: %(sub)s; font-weight: 600; line-height: 1.5; }
.device { position: absolute; background: #1f1f22; box-shadow: 0 40px 80px rgba(0,0,0,.22), inset 0 0 0 2px #3f3f46; }
.device img { display: block; width: 100%%; height: auto; }
.cam { position: absolute; background: #0b0b0c; border-radius: 50%%; }
"""


def device(src, x, y, screen_w, bezel, radius, cam=True):
    img = f'<img src="{(RAW / (src + ".png")).as_uri()}" style="border-radius:{radius - bezel}px">'
    cam_html = ""
    if cam:
        d = max(14, screen_w // 40)
        cam_html = f'<div class="cam" style="width:{d}px;height:{d}px;left:calc(50% - {d / 2}px);top:{bezel + d}px"></div>'
    return (f'<div class="device" style="left:{x}px;top:{y}px;width:{screen_w + 2 * bezel}px;'
            f'padding:{bezel}px;border-radius:{radius}px">{img}{cam_html}</div>')


def page(w, h, theme, body):
    t = THEMES[theme]
    return (f"<!doctype html><html lang='ja'><head><meta charset='utf-8'><style>"
            f"{CSS % {'w': w, 'h': h, **t}}</style></head><body>{body}</body></html>")


def portrait(w, h, title, sub, theme, shot, screen_ratio=0.66, bezel=None, radius=None, top=0.27, cam=True):
    """縦長: 上に見出し、下に端末（縦長の端末は下端をはみ出させる）。"""
    screen_w = int(w * screen_ratio)
    bezel = bezel or max(14, w // 60)
    radius = radius or int(screen_w * 0.11)
    pad = int(w * 0.08)
    title_px = int(w * 0.068)
    text = (f'<div style="position:absolute;left:{pad}px;right:{pad}px;top:{int(h * 0.065)}px;text-align:center">'
            f'<h1 style="font-size:{title_px}px">{title}</h1>'
            f'<p style="font-size:{int(title_px * 0.46)}px;margin-top:{int(title_px * 0.45)}px">{html.escape(sub)}</p></div>')
    x = (w - screen_w) // 2 - bezel
    return page(w, h, theme, text + device(shot, x, int(h * top), screen_w, bezel, radius, cam=cam))


def landscape(w, h, title, sub, theme, shot, screen_h_ratio, aspect, cam=True, radius_ratio=0.06):
    """横長: 左に見出し、右に端末（縦は収める）。aspect は画面の幅/高さ。"""
    screen_h = int(h * screen_h_ratio)
    screen_w = int(screen_h * aspect)
    bezel = max(16, h // 55)
    radius = int(min(screen_w, screen_h) * radius_ratio) + bezel
    right = int(w * 0.06)
    x = w - right - screen_w - 2 * bezel
    y = (h - screen_h) // 2 - bezel
    text_w = x - int(w * 0.12)
    title_px = int(h * 0.075)
    text = (f'<div style="position:absolute;left:{int(w * 0.07)}px;width:{text_w}px;top:50%;transform:translateY(-50%)">'
            f'<h1 style="font-size:{title_px}px">{title}</h1>'
            f'<p style="font-size:{int(title_px * 0.5)}px;margin-top:{int(title_px * 0.5)}px">{html.escape(sub)}</p></div>')
    return page(w, h, theme, text + device(shot, x, y, screen_w, bezel, radius, cam=cam))


def render(markup, w, h, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
        f.write(markup)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                    "--force-device-scale-factor=1", f"--window-size={w},{h}", f"--screenshot={out}",
                    pathlib.Path(f.name).as_uri()], check=True, capture_output=True)
    print(out.relative_to(ROOT))


def main():
    for d in ["phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots"]:
        for old in (OUT / d).glob("*.png"):
            old.unlink()

    # スマートフォン 1080×1920
    phone = OUT / "phoneScreenshots"
    for i, key in enumerate(["home", "focus", "map", "access"], 1):
        shot, title, sub, theme = SLIDES[key]
        render(portrait(1080, 1920, title, sub, theme, shot), 1080, 1920, phone / f"{i}-{key}.png")
    render(portrait(1080, 1920, FOLD_TITLE, FOLD_SUB, "sand", "fold-portrait",
                    screen_ratio=0.8, radius=48, cam=False), 1080, 1920, phone / "5-foldable.png")

    # 7 インチタブレット 1440×2560
    seven = OUT / "sevenInchScreenshots"
    for i, (key, shot) in enumerate([("focus", "tab7-focus"), ("map", "tab7-map")], 1):
        _, title, sub, theme = SLIDES[key]
        render(portrait(1440, 2560, title, sub, theme, shot, screen_ratio=0.72, radius=70), 1440, 2560, seven / f"{i}-{key}.png")

    # 10 インチタブレット 2560×1440（横）
    ten = OUT / "tenInchScreenshots"
    _, title, sub, theme = SLIDES["focus"]
    render(landscape(2560, 1440, title, sub, theme, "tab10-focus", 0.84, 1600 / 2560), 2560, 1440, ten / "1-focus.png")
    _, title, sub, theme = SLIDES["map"]
    render(landscape(2560, 1440, title, sub, theme, "tab10-map", 0.84, 1600 / 2560), 2560, 1440, ten / "2-map.png")
    render(landscape(2560, 1440, FOLD_TITLE, FOLD_SUB, "sand",
                     "fold-focus", 0.7, 2208 / 1840, cam=False), 2560, 1440, ten / "3-foldable.png")


if __name__ == "__main__":
    main()
