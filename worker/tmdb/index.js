/**
 * OwnTV TMDB caching proxy (Cloudflare Worker).
 *
 * This is the source of the default metadata server baked into OwnTV
 * (https://owntv-tmdb-meta.xiannero.workers.dev) — see future-plan/tmdb-metadata-plan.md §5.
 * It is committed here so the deployed Worker is version-controlled and so anyone can
 * self-host their own copy (OwnTV Settings → Metadata → custom metadata server URL).
 *
 * What it does:
 *  1. Accepts the same path/query shape as TMDB's /3/... API (e.g. /3/search/movie?query=...).
 *  2. Injects the TMDB API key from the Worker secret `TMDB_KEY` — the key never ships in the
 *     app APK and never appears in this repo.
 *  3. Edge-caches the JSON response for 30 days, keyed by path+query. Thousands of users opening
 *     the same title cost one real TMDB call.
 *  4. Returns the TMDB JSON unchanged, so the app parses Worker and direct-TMDB responses the
 *     same way.
 *
 * Images are NOT proxied — the app loads poster/backdrop art directly from image.tmdb.org,
 * which needs no API key. This Worker only ever sees small JSON lookups.
 */

/** Edge-cache TTL for TMDB JSON. Metadata is near-static; stale posters are harmless. */
const CACHE_TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days

export default {
  async fetch(req, env) {
    if (req.method !== "GET") {
      return new Response("Method not allowed", { status: 405 });
    }

    const url = new URL(req.url);

    // Only the TMDB v3 API surface is proxied.
    if (!url.pathname.startsWith("/3/")) {
      return new Response("Not found", { status: 404 });
    }

    // Edge cache lookup, keyed by the full request path+query (without the api_key, which the
    // client never sends on this tier).
    const cache = caches.default;
    const cacheKey = new Request(url.toString(), { method: "GET" });
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    // Forward to TMDB with the server-side key injected.
    const upstream = new URL("https://api.themoviedb.org" + url.pathname + url.search);
    upstream.searchParams.set("api_key", env.TMDB_KEY);

    const resp = await fetch(upstream.toString());

    // Pass errors through uncached so a transient TMDB failure doesn't stick for 30 days.
    if (!resp.ok) {
      return new Response(resp.body, { status: resp.status, headers: resp.headers });
    }

    const out = new Response(resp.body, resp);
    out.headers.set("Cache-Control", `public, max-age=${CACHE_TTL_SECONDS}`);
    out.headers.set("Access-Control-Allow-Origin", "*");
    await cache.put(cacheKey, out.clone());
    return out;
  },
};
