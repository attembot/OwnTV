# OwnTV Cloudflare Workers

OwnTV keeps its third-party API keys out of the app by proxying those APIs through small
Cloudflare Workers. Each Worker holds its key as a server-side secret and edge-caches responses,
so a large user base costs only a handful of real upstream calls. Each has its own self-host
guide so anyone can run their own free copy.

| Folder | Worker | Secret | Self-host guide |
|---|---|---|---|
| [`tmdb/`](tmdb/) | TMDB metadata proxy (default metadata server) | `TMDB_KEY` | [`tmdb/README.md`](tmdb/README.md) |
| [`opensub/`](opensub/) | OpenSubtitles proxy (subtitle search/download) | `OPENSUB_API_KEY` | [`opensub/README.md`](opensub/README.md) |

Each folder is a self-contained Worker: `index.js` (the whole server), `wrangler.toml` (CLI deploy
config), and a `README.md` with browser and `wrangler` deploy steps.
