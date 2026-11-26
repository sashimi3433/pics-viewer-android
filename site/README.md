# 高度化PICSビューア サイト（`site/`）

紹介ページとプライバシーポリシー（静的 HTML / CSS）。Cloudflare Workers の静的アセットで
https://advanced-pics.sessapps.com に配信する。Play Console のプライバシーポリシー URL は
https://advanced-pics.sessapps.com/privacy/

```sh
cd site
pnpm dlx wrangler dev      # ローカル確認
pnpm dlx wrangler deploy   # 手動反映（通常は不要: main へのマージで Workers Builds が自動デプロイする）
```

アプリでデータの扱い（権限・通信先・保存する情報）を変えたら、`public/privacy/index.html` と
`docs/play-console.md` のデータ セーフティも合わせて直す。
