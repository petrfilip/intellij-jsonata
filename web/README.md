# web/ — presentation site

A single static page that presents the **JSONata for IntelliJ** plugin, with a live
in-browser playground. No build step, no framework — just open `index.html`.

```
web/
├─ index.html          # the page
├─ styles.css          # design system + layout
├─ app.js              # playground wiring, syntax highlighting, reveal
├─ fonts/              # self-hosted woff2 (Bricolage Grotesque, Hanken Grotesk, JetBrains Mono)
└─ vendor/
   └─ jsonata.min.js   # jsonata.js 2.2.1 — runs the demo in the browser
```

The page is fully self-contained: every asset is local, so it works offline and has no
CDN dependency. The demo runs **jsonata.js**; the plugin itself uses the
[dashjoin/jsonata-java](https://github.com/dashjoin/jsonata-java) port of the same engine.

## Preview locally

```bash
# any static server works; from the repo root:
python3 -m http.server -d web 8080
# → http://localhost:8080
```

(Opening `web/index.html` directly via `file://` works too — there are no cross-origin loads.)

## Deploy to GitHub Pages

Two common options:

**Pages via Actions (set up in this repo).** [`.github/workflows/pages.yml`](../.github/workflows/pages.yml)
uploads `web/` as the Pages artifact and deploys it on every push to `main` that touches `web/`
(and on demand from the Actions tab).

One-time setup: in *Settings ▸ Pages ▸ Build and deployment*, set **Source = "GitHub Actions"**.
No secrets needed — the workflow uses the `GITHUB_TOKEN` that Actions injects.

## Editing content

- **Copy & sections** live in `index.html`.
- **Colors & type** are CSS custom properties at the top of `styles.css` (`:root`).
- **Demo sample data and preset expressions** are the `SAMPLE` and `PRESETS` arrays at the top
  of `app.js`.
- **Updating the engine:** replace `vendor/jsonata.min.js` with a newer release from npm
  (`https://cdn.jsdelivr.net/npm/jsonata/jsonata.min.js`).

## Marketplace link

Install is presented as the JetBrains Marketplace flow (search for **JSONata** inside the IDE).
The primary call-to-action currently scrolls to the in-IDE steps (`#install`) because the exact
listing URL isn't wired in yet.

To point the buttons straight at the listing, replace the `href="#install"` on the hero CTA with
the `https://plugins.jetbrains.com/plugin/<id>-jsonata` URL (and add the same to `foot__links`).
