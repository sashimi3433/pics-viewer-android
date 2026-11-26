# PICS ビューア サイト（`site/`）

紹介ページとプライバシーポリシー（静的 HTML / CSS）。Cloudflare Workers の静的アセットで
https://advanced-pics.sessapps.com に配信する。Play Console のプライバシーポリシー URL は
https://advanced-pics.sessapps.com/privacy/

```sh
cd site
pnpm dlx wrangler dev      # ローカル確認
pnpm dlx wrangler deploy   # 本番へ反映（自動デプロイはない）
```

アプリでデータの扱い（権限・通信先・保存する情報）を変えたら、`public/privacy/index.html` と
`docs/play-console.md` のデータ セーフティも合わせて直す。
