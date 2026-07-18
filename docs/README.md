# VniDrop website

The product website for VniDrop, built with Next.js and exported as a static site.

## Local development

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Checks

```bash
npm run lint
npm run typecheck
npm run build
```

The production build is written to `out/` and can be hosted by any static web server.

Set `NEXT_PUBLIC_SITE_URL` to the canonical production origin when building for deployment so
Open Graph and Twitter image URLs resolve to the public site. Vercel deployment URLs are detected
automatically.
