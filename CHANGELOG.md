# Changelog

## v4.0.1 — 2026-07-03

### 🐛 Fixes

- **D-pad focus no longer jumps to the top bar while scrolling long lists** — holding Up in a big
  category rail or channel/movie/series list (e.g. 500 categories) could make focus outrun the list
  and teleport to the top bar's Search button. Focus now stays inside the panel you're in; you leave
  it only deliberately with Left/Right or Back.
- **Top-bar Search button now appears only while the highlight is on the left nav panel** — inside
  Live TV, Movies, Series, Guide, Downloads or Settings it fades out (keeping its space, so the
  clock/weather chips never shift) and can't take focus. It fades back in when you return to the
  nav panel, where it still opens Search as before.
- **Autoplay next episode no longer fails with a "malformed or corrupted" error** — when an episode
  ended and autoplay advanced, some providers still held the finished episode's connection slot, so
  opening the next episode hung and the player gave up with a misleading corruption error (the same
  episode then played fine manually). A hung open now gets one automatic silent reset-and-retry —
  the transition shows a few extra seconds of spinner instead of an error. Only a second consecutive
  hang still surfaces the error.
- **Player HUD no longer steals D-pad focus from overlays drawn above it** (community PR #41 by
  [@attembot](https://github.com/attembot) — Michael Botta).

## v4.0.0 — 2026-07-02

### 📄 License

- OwnTV has moved from the **MIT License** to the **GNU General Public License v3.0 (GPLv3)**. OwnTV
  remains fully open-source — anyone can use, study, modify, and redistribute it, including commercially —
  but any redistributed version (forks, modified builds, or commercial products built on it) must also
  be licensed under GPLv3 with its source made available. Versions released before this change remain
  available under MIT. See [LICENSE](LICENSE).

Big release — the community‑feedback **UI upgrade** (3 phases; Phase 1's quick wins are the first two
entries below) folded together with a large batch of new features, performance work and fixes.

### ⚡ Much faster syncing & background updates (community PR #40 by [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV, integrated & hardened)

- **Priority sync during setup** — when adding an Xtream playlist you can choose what to import first
  (e.g. Live TV only). You land in the app as soon as the priority content is ready, and the rest
  (movies/series) finishes automatically in the background — even if you leave the screen or the
  device sleeps (WorkManager-backed, survives sleep/reboot).
- **Incremental re-syncs** — re-syncing a source now compares content hashes and only writes what
  actually changed, instead of re-importing everything. Re-syncs of large playlists are dramatically
  faster and no longer churn the database.
- **Incremental EPG sync** — guide refreshes also skip unchanged programmes and prune removed ones.
  Memory use is strictly bounded, so even multi-million-programme guides stay safe on low-RAM boxes.
- **More resilient downloads** — playlist/EPG downloads retry automatically on transient network
  errors, and sync progress reporting is smoother and more accurate.
- Integration hardening on top of the PR: database migrations were renumbered so both v3.2.0 users
  and dev builds upgrade cleanly (final schema v9); staged priority syncs now correctly mark the
  source as synced once the background remainder finishes; favorites/history/resume are re-attached
  after *every* sync attempt (permanent cleanup only after a fully successful full sync); and EPG
  hash tracking loads per-channel with a hard memory cap.
- Post-integration fixes from on-device testing:
  - **Favorites/history could vanish when several playlists refreshed at once** — cleanup of stale
    user data is now strictly scoped to the playlist that actually synced (an empty sync snapshot
    never triggers a global cleanup anymore), and parallel startup refreshes can no longer purge
    against each other's in-flight state.
  - **M3U playlists: movies tagged as VOD landed in Live TV again** — the sync rewrite had dropped
    the VOD detection; entries tagged `type="vod"` / `type="movie"` / `tvg-type="movie"` go back to
    the Movies grid with their own categories.
  - **NEW: M3U series playlists import as real series** — entries tagged `type="series"` /
    `tvg-type="series"` (per-episode lines like *"Stranger Things S01E05"*, also `1x05` style) are
    now grouped into shows with seasons and episodes under the **Series** tab, instead of piling up
    as live channels or loose movies. Entries without an episode pattern become a show with
    sequentially numbered episodes.
  - **TV Guide header showed a date up to a week in the past** — with catch-up channels the header
    displayed the archive's start date. It now shows today when the Guide opens, and follows the day
    you're browsing when you scroll back into the archive.
  - **Subtitle/audio selection could open with nothing focusable on HDR/HDR10/DTS content** — the
    player's pickers (subtitles, audio, speed, zoom, volume) were overlays competing with the HUD
    for D-pad focus, and heavy streams could win that race and lock the picker out. They are now
    real dialog windows that own the remote's focus outright — on both engines, live and VOD — so
    selection always works.
  - **Episode list had no panel background** — opening a series showed its episodes on a bare
    background; the list now sits in the same rounded content panel as every other screen.

> ⚠️ **Upgrade note for EPG users:** v4.0.0 redesigned EPG loading. If the Guide shows blank on first open 
> or after re-entry, **delete your EPG sources and re-add them** (Settings → EPG → Edit → delete, then add 
> again) and resync. Old cached EPG data is incompatible with the new loader — a fresh import fixes it. 
> This is a one-time fix after upgrading.

### 🐛 Fixes

- **Live TV could give up reconnecting too early during a real outage** — a single failed reconnect
  attempt was being counted twice against the retry budget (ExoPlayer fires both an error and an idle
  event for the same failure), so a provider hiccup that needed ~30–60s to recover could exhaust all
  retries and show "Lost connection to this channel" well before the stream was actually back. Reconnect
  attempts are now deduplicated so each real failure only counts once, and the retry budget was raised
  slightly to cover longer outages.

- **Audio-plays-but-no-video no longer leaves you stuck on a black screen** — some streams/files could
  play sound with no picture (both Surround Sound on and off), because the existing freeze watchdogs only
  caught a *total* stall or a freeze *after* a frame had already been seen — never "audio/position is
  advancing fine, but a video track exists and has never produced a single frame." All three playback
  paths now detect this specifically:
  - **Live TV, ExoPlayer (primary engine):** if no video frame renders within ~8s while audio/position
    keeps advancing, it automatically tries the mpv compatibility fallback once (shows the spinner during
    the switch, no loop). If mpv plays it fine, playback continues normally; if mpv also fails, a clear
    on-screen message is shown.
  - **Live TV, mpv (compatibility-mode / fallback channels):** the same condition now triggers the existing
    bounded reconnect/reload path; if video still doesn't appear after the retry budget, shows "Audio is
    playing, but video could not be rendered on this device."
  - **VOD, image-subtitle handoff (PGS/VOBSUB/DVB subtitles):** the brief ExoPlayer handoff used only for
    these subtitle types now has the same first-frame timeout, falling back to mpv with a clear message if
    it can't render video either. The main VOD (mpv) path already had a working no-video watchdog.

- **Favorites could disappear after a source re-sync failed partway through** — a source's clear-then-insert
  import is deferred per chunk (old content is only wiped once new data starts arriving), so a sync that
  failed midway (e.g. flaky Wi-Fi right as a Fire TV woke from sleep) could leave content partially cleared.
  Favorites/history/resume are re-attached to the new content ids only after a *successful* sync, so a
  failed one left them silently orphaned (rows still existed but resolved to nothing) until a later sync
  healed them — in the meantime they simply looked gone. Re-attaching now runs after every sync attempt,
  successful or not; only a fully successful sync is still allowed to permanently drop favorites for
  content the provider actually removed.

- **Live TV no longer freezes silently mid-stream** — a live channel could play smoothly and then
  freeze/hang with no spinner, no reconnect and no error (replaying the channel fixed it). This happened
  when a feed stalled in a way the player didn't *signal* — the stream stops advancing while the socket
  stays open, so there was no buffering event, no error and no end-of-file to react to. Both playback
  backends now detect this:
  - **ExoPlayer (the primary live engine):** the silent-freeze watchdog now keys off *intent to play*
    instead of the stricter "is-playing" flag (which briefly flickered off during a stall and kept
    resetting the freeze timer), and adds an absolute "no forward progress for ~8s" backstop that can't be
    missed even if per-frame detection isn't available. On a stall it shows the spinner and auto-reconnects
    to the live edge (bounded retries with back-off), surfacing "Lost connection to this channel." only
    after repeated failures.
  - **mpv (compatibility-mode / fallback channels):** added an equivalent live progress watchdog that
    detects a frozen stream, shows the spinner and reconnects with a bounded retry budget.
  - The loading spinner is now shown consistently while a live stream is buffering, reconnecting or
    retrying in either backend, and clears once playback resumes or a final error is shown. Detailed
    Logcat is emitted around buffering / freeze detection / reconnect attempts for diagnosis.
  - **Follow-up:** closed a second silent dead-end in the ExoPlayer (primary live) engine — if a feed
    dropped into `STATE_ENDED` or unexpectedly into `STATE_IDLE` mid-playback, it was previously ignored
    entirely (no spinner, no reconnect, no error). Both are now treated as a recoverable stall and
    auto-reconnect, while a normal stop/back/release still exits cleanly with no reconnect attempt. Added
    a debug-only diagnostic log (state transitions, watchdog/reconnect events) plus a small bounded
    on-device diagnostic file, so a future recurrence can be captured even if it happens unobserved —
    see `extras/LIVE_TV_HANG_DIAGNOSTICS.md`.

- **EPG match no longer removes a channel from the Guide** — matching a channel's EPG (auto or manual)
  could silently delete its stored programmes and leave the channel blank and then invisible in the
  Guide. This happened when multiple EPG sources were configured and a cache re-fill across a large
  source file was interrupted before it could restore the deleted rows. The cache re-fill is now
  parse-then-apply: programmes are only deleted for ids where fresh replacement data was successfully
  parsed first. Channels that had no in-window data in any fresh cache keep whatever they already had.

- **Show/Hide password toggle on all password fields** — a **Show / Hide** button now appears on the
  right of every password field (Xtream password when adding/editing a playlist; PIN fields in profile
  setup and profile settings). The toggle is D-pad focusable independently of the text field, so the
  password can be revealed and re-hidden without opening the keyboard. Previously there was no way to
  see the password you had typed on either the first-run setup screen or the Settings → Playlists edit
  screen.

### ✨ New features

- **Backup now covers more settings and encrypts saved passwords** — the backup file now also includes
  surround sound, auto-play-next, Guide sort, animation level, Movies/Series view mode, catch-up timezone
  & offset, the global proxy (host/port/user/enabled), and each profile's startup landing screen. Saved
  passwords (source/playlist and proxy) are no longer written in plaintext: on export you can set a
  **backup password** to encrypt them (AES-GCM, field-level only — the rest of the file stays readable),
  or export without passwords. On restore you're prompted for that password; a wrong password never wipes
  anything and lets you retry, and you can skip it to restore everything except saved passwords. Old
  backups still import as before. Both restore entry points (Settings and the first-run setup wizard)
  prompt for the backup password.
- **Manually reorder channels, movies and series** — long-press any item in a **category folder** or **Favorites**
  and choose **Move**. A full-screen reorder overlay appears with the full list; **D-pad Up/Down** moves the item
  up or down, **OK** saves, **Back** cancels. The order persists across playlist re-syncs and is included in
  profile backups / restores.
- **Remove a single item from History** — long-press any item in the **History** folder and choose
  **Remove from History** to delete just that entry. The existing bulk "Clear watch history" in Settings is
  unchanged.
- **Download from long-press menu** — Movies and Series now show a **Download** / **Download all episodes**
  button directly in the long-press context menu, alongside the existing detail-pane download button.
  Movies queues the file immediately; Series queues every locally-cached episode (open the series once first
  if no episodes appear).
- **Settings → Customize Category** — the "Customize" settings row has been renamed **Customize Category** to
  clarify it affects categories (hide, rename, reorder), not individual items.
- **Global HTTP proxy support** — **Settings → Network → Proxy** lets you route all OwnTV traffic
  (playlist sync, Xtream API, EPG, images, downloads, updates) and fullscreen playback through an HTTP proxy.
  Enter a proxy host and port (optionally with username / password); a **Test Proxy** button verifies connectivity
  before saving. Disabling the proxy restores direct connections. The proxy is applied globally across all
  playlists — per-playlist proxy overrides and SOCKS5 support are planned for future versions. See
  `extras/PROXY_SUPPORT_PLAN.md` for full details and limitations.
- **Home screen with Continue Watching** — a new **Home** tab opens to a hero carousel of your partially‑watched
  movies, episodes and recent live channels (newest first); the selected card is shown large with its poster and
  starts a muted video preview when focused, and pressing **OK** resumes right where you left off. Below it is a
  **Favourite Channels** rail. On **stock Android TV** launchers it also feeds the system **"Continue Watching"**
  (Watch Next) row, so you can resume straight from the TV home screen — Settings → Android TV home → **Refresh
  now** rebuilds those cards (with a *Rebuilding… → Done* status). (Sideloaded Fire TV / Google TV don't surface
  system Watch Next rows, so the in‑app Home screen is the universal landing for everyone.)
  🙏 **Huge thanks to [@codeVerine](https://github.com/codeVerine) (Sagar Mukundan UV) for building and
  contributing this entire Home screen feature ([PR #31](https://github.com/ahXN00/OwnTV/pull/31)).**
- **Stream technical info overlay** — in the player, the bottom-bar **info** button toggles a live readout of
  the current stream: video codec · resolution · fps · bit-depth, HDR type, bitrate, decoder (hardware/software
  · direct), audio codec · channels · sample rate, buffer & dropped frames, and the (credential-masked) source.
  Works on both playback engines and updates live.
- **Volume boost to 150%** — for movies, series and any channel played on the mpv engine, the player volume
  can go above 100% (Kodi-style amplification, **capped at 150%**) for quiet streams, with mpv's internal soft
  limiter so it never harshly distorts.
- **Fixed, roomy layout — no more "sandwiched" Live TV** (Phase 2) — the navigation and category panels no
  longer expand and collapse as you move the D‑pad, so the interface never jumps around. Live TV is now a
  stable grid: a slim **icon nav**, a **full‑label category column** (no more 2–3 letter abbreviations), the
  **channel list**, and a large **preview** — each a fixed size. The same fixed nav + category column apply
  across **Movies, Series and the Guide**. The result also feels noticeably faster on lower‑end boxes.
- **Shell redesign — new sidebar, top bar, and rounded panels** (Phases 0–7) — the entire app shell has been
  rebuilt with a fixed icon-only left rail: **brand logo** at the top, **nav items** vertically centered
  (scrollable at high UI zoom), **profile avatar** pinned at the bottom (click = "Who's watching?" profile
  switcher, even for a single profile; long-press = avatar picker with a new **"no avatar"** option showing a
  silhouette). **Search moved out of the rail** into a new **top bar** that shows the active section name,
  a Search pill on the left, and a **live clock**, **weather chip** (with Canvas weather symbols — sun, moon,
  cloud, rain, snow, thunder — via Open-Meteo, free no-key API), and **playlist name** on the right. All
  content now sits inside **rounded panels** (Option A "Clean + Premium"): the category rail, content grid,
  and preview pane each get their own rounded box with 22dp corners and hairline borders, floating on a dark
  `#040E0B` surface. Settings submenus share the same rounded look. **Theme** renamed from `AMOLED_DARK` →
  `DARK` with a `#040E0B` charcoal default (no more pure black). **Neo Signal Duotone** nav icons
  (Home, Live TV, Movies, Series, Downloads, Guide, Settings, plus a Profile fallback silhouette) drawn on
  crisp 100-unit Canvas. **Top bar is uniform** — all 5 chips (section, search, clock, playlist, weather)
  share identical height. Light mode fully supported with matching panel tints.
- **Clear watch history** — Settings → Content → **Clear watch history** lets you wipe this profile's
  recently-watched / "continue watching" rows — **all of it, or just Live TV, Movies or Series** (with a
  Yes/No confirmation). Playlists, favorites and downloads are untouched.
- **Favorite a channel straight from Search** — long-press a channel in search results to add or remove it
  from Favorites; a star shows the current state. No need to open Live TV first.
- **Detailed channel search results** (Phase 3) — channel results now show **category · channel number** under
  the name, so near‑identical feeds (e.g. several "ABC" or "Sky Sports") are easy to tell apart; long‑press
  still toggles the favourite.
- **Move categories to top / bottom** — in Settings → Customize, each category now has ⤒ / ⤓ buttons to jump
  it straight to the top or bottom of the list, alongside the existing one-step ↑ / ↓.
- **Animations setting (On / Off)** — Settings → Appearance → **Animations** turns interface motion on or off.
  **Off** makes navigation instant — a reduce‑motion / accessibility toggle (the v4.0.0 fixed grid already
  removed the menu lag that a middle "Reduced" tier used to address).
- **Channel list in the player** — while watching a channel full-screen, press **Left** (with the controls
  hidden) to slide out a **channel list over the video** — browse and switch channels without leaving
  full-screen. The current channel is highlighted; Back or Left again closes it.
- **Per‑profile startup (default landing)** (Phase 3) — Settings → **Startup** sets, **per profile**, where the
  app opens: **Home**, the **Last channel** you watched (so a profile that always watches one channel boots
  straight into it), or **Live TV on Favorites**. Replaces the old global "Resume last channel" toggle —
  existing "On" carries over to **Last channel**.
- **Remembers where you were in Live TV** — Live TV reopens on the **category you last had selected** (instead
  of resetting to All) and lands focus back on the **last channel you were on**.
- **Guide by category** — the EPG/Guide has a new **Category** filter so you can view just one group at a
  time instead of every channel at once, with a **search box** in the category list to find a group fast.
- **Favourites in the Guide** — the Guide's **Sort** button now includes a **Favorites** option, filtering
  the guide to just your favourited channels.
- **List view for Movies & Series** — a new **Grid / List** toggle on the Movies and Series screens: switch
  the poster wall to a compact list to see many more titles at a glance.
- **A/V sync nudge in the player** — open the **Audio** menu on a movie/episode for an **A/V sync** stepper to
  nudge the audio earlier/later in 50 ms steps when a badly-encoded file has the sound out of sync. It resets
  per file, so it never throws off your other movies.
- **One-tap guide sync after adding a playlist** — after importing a playlist (first-run setup or Settings →
  Playlists), OwnTV now asks **"Sync the TV guide now?"** if the playlist has a guide feed. **Sync now** shows
  a **live programme count** (just like the playlist import) and a brief "Done"; **Not now** keeps it manual.
- **Long-press a channel in Live TV** — long-press any channel in the Live TV list for a quick menu:
  **Add/Remove Favourite, Rename, Hide, Match EPG**, and **Catch-up** (on channels that support it) — without
  moving over to the preview pane.
- **Closed captions (CC) on Live TV** — channels that embed CEA-608/708 closed captions in the video stream
  (e.g. many US channels like HBO/Showtime/Cinemax) now expose a selectable caption track in the player's
  **Subtitles** menu, instead of showing only "Off". (#28)
- **Compatibility mode (per-channel mpv engine)** — if a live channel shows artifacts or won't play right on
  the fast engine, press the **gear** in the player controls to switch that channel to the mpv engine. It's
  **remembered per channel**, so it opens cleanly on mpv every time after — every other channel keeps the
  near-instant start.

### ⚡ Performance

- **Movies & Series open instantly** — the grids are now **pre-warmed at startup** (like the Guide), and the
  query planner's table stats are refreshed after every playlist sync. A bulk sync does `REPLACE` on 100k+
  rows which invalidates SQLite's stats and made the planner ignore the existing `(sourceId, name)` /
  `(categoryId, name)` composite indices — so the grid fell back to a full-table sort on cold open (the 2–3s
  delay). Stats are now re-analyzed post-sync and at launch so the indices stay chosen. (Mirrors the EPG fix.)
- **The Guide opens instantly** — the guide is now **pre-loaded in the background at startup**, so even the
  first open is immediate, and re-opening no longer flashes a loading spinner or rebuilds from scratch — it
  shows your channel list right away and refreshes silently.
- **Much faster EPG sync** — the guide sync now stores programmes **only for the channels you actually have**
  instead of the entire feed (public XMLTV feeds often carry 10–20× more channels than your playlist). Far
  fewer rows to parse and write means a dramatically quicker, lighter sync.
- **Leaner TV Guide internals** — the guide now loads every row's programmes in **one batched query**
  (grouped into a cache) instead of a separate query per channel row (an N+1 storm), and draws each row's
  timeline in a **single Canvas pass** instead of dozens–hundreds of per‑cell composables. The catch‑up
  lookback streams in on a background thread (memory‑safe on low‑RAM boxes), the channel list is built off
  the main thread, and re‑sorting/filtering reuses the cache. Mostly an efficiency/memory win — lighter on
  large channel lists and multi‑day catch‑up windows.

### 🔧 Internal

- Room database version **6 → 7**: new `content_order` table stores per-profile manual item ordering; included in backup/restore.
- Long-press context menus on Movies and Series replaced the previous instant-favourite-toggle with a full menu (Favourite, Move, Remove from History, Download, Close).

### 🐛 Bug fixes

- **Per-source User-Agent for playback** — each source now supports a **custom User-Agent** (entered in source
  settings), and it is consistently applied to Live TV, Movies, Series, and EPG playback on both mpv and
  ExoPlayer. If playback fails with a format/demuxer error and no custom UA was set, the app retries once
  with the short `vlc` User-Agent — some providers block the full `VLC/3.0.20 LibVLC/3.0.20` string but
  accept the short form. If that also fails, the error message hints: *"This provider may require a custom
  User-Agent in source settings."*
- **No more false "Playback error" over a movie that's actually playing** — on some TVs (e.g. Realtek-based
  panels) the hardware decoder takes a few seconds to negotiate and deliver its first frame, which made the
  VOD watchdog wrongly conclude the file wasn't streamable and show *"This video isn't formatted for
  streaming…"* on top of perfectly-playing video. The watchdog now waits a little longer before that verdict
  and, more importantly, automatically dismisses the popup the moment a real video frame decodes. Genuinely
  non-streamable files still surface the error as before.
- **Startup focus rests on the nav** — on a cold start (or switching to the Home tab) focus now stays on the
  **Home item in the sidebar** instead of being pulled into the content; it only jumps into the hero when you
  return from the player. (Builds on [@codeVerine](https://github.com/codeVerine)'s empty‑Home focus fix,
  [PR #32](https://github.com/ahXN00/OwnTV/pull/32).)
- **Clear watch history now empties Movies/Series from Home too** — clearing history (all, or just Movies /
  Series) now also wipes the **resume positions** that feed Home's "Continue Watching", so those titles
  actually leave the row (previously only Live cleared).
- **Live preview shows full stream spec** — the preview pane's badge now shows **aspect · resolution · fps ·
  audio** (e.g. `16:9 · 4K · 50 FPS · STEREO`) instead of resolution alone.
- **Startup → Live · Favorites lands inside the list** — choosing this startup mode now drops focus on the
  first favourite channel instead of the navigation panel, so you can start zapping immediately.
- **Long‑press channel menu keeps focus on the channel** — closing the Live TV long‑press menu (Cancel /
  Favourite / Hide) now returns focus to that channel instead of jumping back to the navigation panel.
- **Clearer Surround sound warning** — the setting now explains that multichannel can drift audio behind
  video (lip‑sync) on some TVs/soundbars, and points to the player's **Audio → A/V sync** nudge to correct it.
  (Surround stays **off by default**; the drift is a hardware‑latency reality of multichannel LPCM over HDMI/ARC.)
- **Imports survive a provider that errors on the full Movies/Series list** — some providers (e.g. peoplestv)
  return a non-standard **HTTP 512** on the giant bulk `get_series` / VOD response, which used to abort the
  whole import after the channels had loaded. Now a bulk error **automatically falls back to fetching that
  section one category at a time** (small requests those panels serve fine) — and if even that fails, the
  import keeps your channels/movies instead of failing outright. Credentials are also no longer shown in
  import errors.
- **EPG no longer fails on a single malformed tag** — a guide feed with one bad/odd entry used to abort the
  whole sync with a cryptic "END_TAG expected …" error. The parser is now tolerant (relaxed mode + resilient
  text reading) and keeps everything it can, so one bad programme no longer loses the entire guide.
- **Playback survives the screensaver** — leaving the TV long enough for the screensaver no longer leaves you
  on a dead stream. A paused **movie/episode** is restored **paused at the exact spot**, and a **live channel**
  is **re-tuned to the live edge**, when you come back — instead of doing nothing until a manual reload.
- **Live TV no longer freezes with no recovery** — some live streams stop advancing while the player still
  thinks it's playing (no buffering, no error), so the auto-reconnect never kicked in and the channel just
  hung. A new freeze watchdog detects the stalled picture and reconnects automatically.
- **No sound when opening a channel very fast** — pressing OK on a channel a split-second before its preview
  loaded could carry the muted-preview state into full-screen, so the channel played silently. Full-screen
  now always plays with sound.
- **One corrupted file no longer breaks all playback** — a malformed MP4 (broken UDTA metadata pointing to
  a multi-GB offset) sends FFmpeg's demuxer into a 3+ GB HTTP seek that blocks mpv's core thread. Previously
  this poisoned every subsequent video (even healthy ones wouldn't play until app restart). Now the video
  watchdog detects the stuck demuxer (no `FILE_LOADED` after 7s) and **destroys+recreates the mpv instance
  entirely** (the only way to abort a blocked HTTP read), showing a clear error for the bad file while every
  other video continues to play fine.
- **Audio/video drift on some movies** — a few high-bitrate / high-frame-rate movies could play with the
  picture slightly behind the sound, because nothing was dropping the late frames on the direct hardware
  path. The player now drops late frames at the decoder so audio and video stay in sync.
- **Long-press to favourite in Movies and Series** — long-press OK on any movie or series poster (grid or
  list view) to toggle it as a favourite. Same as the details-pane button, just faster — no need to focus
  into the details pane first. The existing star indicator still shows the current state.
- **Sync no longer wipes data on failure** — old channels/movies/series are only cleared when the first new
  row is actually written, not at the start. If a sync fails completely (wrong password, network down,
  timeout), your existing content stays intact instead of vanishing. The Add Source screen now also
  remembers what you typed so a typo doesn't mean re-typing everything from scratch on the remote.
- **Sync times out fast instead of spinning forever** — OkHttp connect/read/write timeouts are now 15/20/20s
  (down from 30/60/30s) and silent auto-retries are disabled. When the network drops mid-sync, the error
  dialog appears in ~20s instead of hanging for minutes. Category-by-category fallback also aborts on
  network errors (continues only for HTTP errors like 512) instead of retrying every category against a
  dead server.
- **M3U VOD entries now route to Movies** — M3U playlists with `type="vod"` or `tvg-type="movie"` entries
  now create movie/stream rows in the Movie grid instead of being incorrectly filed under Live TV. The
  `group-title` becomes the movie category (e.g. "Movies", "Peliculas").
- **Offline banner now works on all devices** — Android TV boxes whose Ethernet interface stays "up"
  forever (never fires network callbacks) now get a 20-second connectivity poll, so the banner actually
  appears when the internet is unreachable.
- **Profile dialog focus no longer escapes** — the edit/create profile popup now uses a `Popup` window
  with `focusable=true` so D-pad stays inside the dialog instead of wandering out to the sidebar.
- **Two-stage video watchdog** — broken files caught faster and more accurately: **Stage 1** (T_OPEN, 10s)
  catches a demuxer that never opens the file; **Stage 2** (T_DECODE, 7s) catches a decoder that opened
  the file but never produced a frame. **Moov-at-end detection** catches MP4s with trailing headers
  from servers without Range support (shows a clear error instead of retrying endlessly); **`END_FILE`
  instant-catch** aborts immediately when the demuxer rejects a malformed file outright. A **thrash
  guard** (3 consecutive hard-resets) prevents infinite tear-down/recreate loops on bad playlists.
  Added `seekable=1` to VOD demuxer options so FFmpeg attempts HTTP Range requests even on servers
  that don't advertise byte-serving.
- **Guide shows programmes on first open** — the EPG guide was blank until you navigated into a row (on large 
  catch-up windows with a lookback), because the auto-scroll-to-now fired before the timeline layout was ready. 
  The scroll now waits for layout, so programmes appear immediately. **Note:** if upgrading to v4.0.0 and the 
  guide remains blank after this fix, **delete the EPG sources and re-add them** (Settings → EPG → Edit → delete, 
  then add the feed again); v4.0.0's new batched EPG loader is incompatible with old cached data, and a fresh 
  re-import ensures compatibility. Resync only after re-adding.

## v3.2.0 — 2026-06-22

### ✨ New features

- **Live rewind (timeshift)** — on a channel your provider records (Xtream catch-up / archive), you can now
  **rewind the live stream** to re-watch a moment you missed (a goal, a play) and then jump back to the live
  edge — without leaving the channel for the Guide. On a catch-up live channel the player gains a **⏪ rewind**
  control; while rewound it shows how far behind live you are, the clock time you're watching, and a **● Live**
  button to snap back to the edge. There's both a **scrubbable timeline** (the last 2 hours up to the live
  edge, with a red live marker — hold ◀/▶ to scrub) **and** ⏪/⏩ buttons for precise 30-second steps, plus a
  **"behind live" counter** that ticks down as the archive catches up (and grows if you pause).

### ✨ Improvements

- **Switch profile without leaving the app** — the profile card (top-left) now has a **Switch Profile**
  button that stops playback and returns to the "Who's watching?" screen, so you can change profile without
  force-quitting the app.
- **Wider category folders** — the Live TV / Movies / Series category rail now expands wider when focused,
  so long category names are fully readable; it still shrinks back when you move into the list.
- **Catch-up defaults to your device timezone** — catch-up / live-rewind timestamps now default to the
  **device's timezone** (was UTC), which matches most providers' server-local archives out of the box; you
  can still override it in **Settings → Catch-up time**.
- **Longer Guide catch-up** — the guide now keeps up to **7 days** of just-aired programmes (was ~2 days), so
  you can browse and replay further back when your provider records that long and its EPG feed supplies it.
- **Clearer audio-track icon** — the player's audio-track button is now a music note, so it's no longer
  easily confused with the volume button.

### 🐛 Bug fixes

- **Audio & subtitle selection now works on Live TV** — the ExoPlayer live engine wasn't exposing any
  tracks, so multi-language live channels (and a dual-audio file added via an **M3U** playlist, which
  imports as a live channel) showed **"No tracks available."** Live now enumerates **audio** and
  **subtitle** tracks: the HUD's Audio/Subtitle menus list them with language labels and switch them on
  the fly, and a selected subtitle renders on screen (the overlay mounts only while subtitles are on, so
  4K live keeps its direct hardware-overlay path).
- **No more silent playback for AC3/DTS files played as live** — a movie file with **AC3 / E-AC3 / DTS**
  audio (e.g. a dual-audio rip added via an M3U playlist, which imports as a live channel) played **video
  with no sound** on devices whose hardware can't decode those codecs, because the live ExoPlayer engine
  relies on the device's audio decoders. Such streams now **automatically fall back to the mpv engine**
  (which decodes them in software), so they play **with sound** — and on hardware that *can* decode the
  codec, playback stays on the fast ExoPlayer engine as before.
- **Live audio no longer keeps playing after you exit/log out** — a **live channel** plays on the ExoPlayer
  engine, but leaving the app only stopped the mpv player, so the live stream's **audio kept playing in the
  background**. Exiting/backgrounding now stops **both** engines.
- **Clearer error for an unplayable movie** — when a movie/episode can't be decoded, the player showed the
  *catch-up* "recording/archive" error text; it now shows a video-appropriate message (only real catch-up
  recordings use the archive wording).
- **Playback errors now show the real reason** — the error screen now lays the failure out in three parts so
  the actual cause is visible **without adb/logcat**: a **plain-English reason**, the **media spec** (codec •
  resolution • decoder, e.g. `HEVC 3840×1920 • hardware decoder`), and the **raw** engine line. It surfaces,
  in order of usefulness:
  the **hardware codec / audio error** (Android MediaCodec/AudioTrack — e.g. the cryptic `0x80001000` is shown
  as *"video decoder error — the TV's hardware decoder is busy or can't handle this stream [MediaCodec: …]"*),
  the **network/format** reason from mpv (`http: HTTP error 400`, `unrecognized file format`), or the
  **ExoPlayer** code for live (`ERROR_CODE_DECODING_FORMAT_UNSUPPORTED`). On live, codec/audio failures are
  read **programmatically** from ExoPlayer (reliable across devices, no logcat needed). Common cryptic cases
  are translated to plain English — e.g. **HTTP 509** → "Provider blocked — too many streams at once", **403**
  → "Provider denied access", an expired **SSL** certificate, out-of-memory, and unsupported codec profiles.
  Works for video **and** audio failures, on movies, series and Live TV — turning "guess and rebuild" into
  "read the line."

## v3.1.2 — 2026-06-21

### 🐛 Bug fixes

- **Surround sound is now off by default (opt-in), with a safety net** — v3.1.1's multichannel-LPCM surround
  (on by default) broke playback on some TVs that *claim* 5.1 over HDMI but mis-play it: series with
  multichannel (Dolby/DTS) audio played at **double speed with no sound** (movies/live were fine). Surround
  is now **off by default** — leave it off on TV speakers / stereo soundbars (clean stereo), turn it **on**
  for a real 5.1/7.1 receiver. When on, OwnTV pins a widely-compatible **16-bit / 48 kHz** output and, if it
  still detects that double-speed/no-sound runaway, **auto-switches that session to stereo** so playback
  never breaks. (#25)
- **Live TV recovers from connection drops** — if a live channel froze mid-watch (a brief Wi-Fi/provider
  hiccup), it used to stay stuck until you backed out and re-opened it. Live now **auto-reconnects** from the
  live edge after a drop or stall, retrying with back-off; if it still can't recover, the on-screen **Retry**
  takes over.
- **Screen no longer sleeps during Live TV** — because live plays on the ExoPlayer engine, the TV
  screensaver could start mid-channel; the screen is now held awake while watching live (full-screen and
  PiP), just as it already was for movies and series.

## v3.1.1 — 2026-06-21

### ✨ New features

- **Near-instant Live TV (two playback engines)** — live channels now play on a dedicated **ExoPlayer**
  engine: the channel-list **preview** comes up almost instantly as you scroll, and pressing **OK promotes
  that same stream straight to full-screen** with no reload — so opening a channel and **zapping** (CH± /
  D-pad) are immediate, especially on HLS/M3U. The robust **mpv** engine still runs **all movies & series**
  (4K/HDR direct path, broad stream compatibility) and automatically backs up any live stream ExoPlayer
  can't open. Live PiP/dock works on either engine.
- **Import a playlist from a local file** — adding an **M3U / M3U8** source now has a **"Choose a local
  file"** button that opens an in-app, TV-friendly file browser, so you can load a `.m3u`/`.m3u8` saved on
  the device (USB drive, Downloads, etc.) instead of a URL. The file is re-read on each refresh. (#24)

### 🔧 Changes

- **EPG is now opt-in** — adding a playlist **no longer auto-downloads its guide** (that could make every
  import slow). Add a guide when you want it via **Settings → EPG sources**, where the form **pre-fills the
  playlist's own guide URL** (Xtream `xmltv.php` / M3U `url-tvg`) — so it's still one step, just on demand.

### 🐛 Bug fixes

- **Surround sound no longer stutters video** — the v3.1.0 *Surround passthrough* toggle bit-streamed raw
  Dolby/DTS to the TV/receiver, but on some TVs (e.g. Realtek) the passthrough audio path returns no
  timing to the player, which starved the video into a **1–2 fps slideshow** on Dolby/DTS titles (most
  noticeable on 4K). The setting is now simply **Settings → Surround sound** (on by default): OwnTV
  **decodes** Dolby/DTS to **multichannel LPCM (5.1/7.1)** over HDMI, so your TV or AV receiver still gets
  surround **and** the picture stays smooth on the fast 4K/HDR path. Turn it off for a stereo downmix.
  (Raw bitstream passthrough has been removed.)
- **M3U live channels that wouldn't play now work** — after v3.1.0's faster channel-zapping, some live
  channels from a plain **M3U/HLS** playlist could hang on a black screen (the trimmed startup probe
  couldn't open those streams), while Xtream live was unaffected. OwnTV now uses the full probe for
  HLS/non-TS live (as it did before), and keeps the fast trimmed probe for direct **MPEG-TS** (`.ts`) live
  — so M3U live plays again *and* TS zapping stays quick.
- **4K channel zapping no longer hangs** — switching between **4K** channels with the D-pad / CH± in
  full-screen could freeze the picture until you backed out and re-entered. The player now starts each
  4K-class channel on a fresh video surface, so zapping plays cleanly (a TV-decoder quirk on back-to-back
  4K decodes).
- **Episodes now appear for every Xtream series** — some providers return a series' episode data in a
  different JSON shape, which OwnTV didn't read, so those shows opened with **no episodes** (they worked in
  other apps). The parser now handles both shapes, so episodes populate. (#23)
- **Global search opens the right series** — picking a series from the **main search** now opens that
  show's **episode list** directly, instead of just jumping to the Series tab.

## v3.1.0 — 2026-06-20

### ✨ New features

- **Catch-up straight from Live TV** — focus a catch-up channel in **Live TV** and the preview now has a
  **Catch-up** button: it opens a simple list of recent programmes — pick one and it **replays from the
  start**. No more hunting through the Guide timeline. (The Guide still works for browsing too.)
- **Hide/show a whole range of categories at once** — in **Settings → Customize**, long-press a category's
  Show/Hide button to start a span, then press Show/Hide on another category to select everything in
  between and hide or show it all in one go — a big time-saver for providers with hundreds of categories.
  (by @dan-maloney, #20)
- **Auto-play next episode** — when an episode finishes, OwnTV automatically starts the next one, and
  **rolls into the next season** after a season's last episode — great for binge-watching. There's a new
  **Settings → Auto-play next episode** toggle (on by default) for anyone who prefers manual playback. (#21)
- **Series open on your last-watched episode** — reopening a show now jumps straight to the episode you
  last watched (correct season, scrolled into view and focused) instead of always starting at episode 1,
  and that episode is tagged **"Last watched"** so it's easy to spot. (#22)
- **Surround sound passthrough** — a new **Settings → Surround passthrough** toggle sends **Dolby
  (AC-3/E-AC-3, incl. Atmos) and DTS** audio straight to your TV or AV receiver to decode, instead of
  mixing down to stereo. OwnTV only passes through the formats your audio output reports it can handle,
  and you can switch it off if a stream goes silent. (Off by default.)

### 🐛 Bug fixes

- **Faster channel zapping** — live channels and HLS streams now start with a **trimmed stream probe**,
  so the picture comes up noticeably quicker when switching channels. If a trimmed probe ever misses a
  stream's audio (rare, on sparse feeds), OwnTV automatically **re-probes that channel in full** so it
  still plays with sound. On-demand movies/series keep the full probe for rock-solid HDR/audio detection.
- **Live channels that dropped out every few seconds now play continuously** — some live servers close the
  connection on a schedule (common with 4K feeds); OwnTV now **reconnects automatically at the stream level**
  and keeps playing, instead of stalling and re-buffering on a loop.
- **Smoother video on TVs** — the player now asks the display to **match the video's frame rate** (e.g.
  switch a 60 Hz panel to 24/48 Hz for 24fps content). On TVs that support it, this removes the subtle
  *judder* of film-rate content on a fixed 60 Hz screen (the "looks slightly slow/uneven, but not
  buffering" feel). No effect on panels that can't switch — it just stays as-is.
- **Installs on non-TV devices now** — OwnTV required the Android **TV (leanback)** feature, so it
  wouldn't install on plain phones / non-TV boxes (incl. some armv7a Android 11 devices) and showed
  **no launcher icon** on phones. It's now installable on regular Android too, with a normal home-screen
  icon — while still appearing in the TV launcher on Android TV. (Also resolves #16.)
- **EPG sources that failed with a "protocol error" now load** — some EPG/host CDNs have flaky HTTP/2
  and would reset large downloads (e.g. a big US guide) with *"stream was reset: PROTOCOL_ERROR"*.
  OwnTV now uses HTTP/1.1 for its downloads, which those servers handle reliably. (#17)
- **Image-based subtitles now play smoothly** — text subtitles (SRT/ASS) display on the fast HDR path as
  before. **Image-based** subtitles (PGS/VOBSUB/DVB) on **movies & series** now display *without* slowing
  the video down: picking one seamlessly hands that title to a second engine (ExoPlayer) that keeps the
  picture on the same zero-copy/HDR path and draws the bitmap subtitle on its own layer — no more stutter,
  and still only **one** connection to your provider. (The old approach composited inside the video and
  could make 4K/HDR unwatchable on TV hardware — that's gone.) Image tracks are tagged **"image"** in the
  picker; turning subtitles off or choosing a text track hands straight back. If a title's audio is a
  format the second engine can't play (e.g. DTS), it stays on the main engine and tells you. (Image
  subtitles aren't shown on live channels, where they're virtually never present.)
- **Big-library import no longer gets stuck** — the per-category fallback (for providers that truncate
  the bulk movie/series list, #15) used to make the import counter look like it was *restarting* each
  category, and on panels that **ignore the category filter** it could loop forever re-fetching the same
  list. Progress now climbs **continuously** across the whole import, and the fallback **stops** when the
  provider clearly isn't honoring per-category requests (keeping everything fetched so far). (#15)

## v3.0.0 — 2026-06-17

*Big release — bundling the open feature requests + Catch-up TV.*

> 💬 **Join us on Telegram** — **Settings → About** now shows the OwnTV **Telegram group** link with a
> **QR code** you can scan from your phone to join the community (also added to the README).

### ✨ New features

- **Browse the TV Guide timeline** — navigating the guide is now two-stage: press **Right** on a channel
  to select its **whole programme row**, then **OK** to step in and move through programmes with
  **Left/Right** (the row scrolls with you). **OK** on a programme opens it (watch / *Watch from start*
  for catch-up), and **Up/Down** jumps to the next channel at the same time. **Back** steps back out.
- **Catch-up TV (archive)** — for providers that offer it, the TV Guide now lets you **watch programmes
  that already aired**. When you have catch-up channels, the guide extends **back in time** (up to ~2
  days, depending on your EPG) — scroll **left** to reach earlier programmes, open one and pick **Watch
  from start** to replay it from the archive (seekable, with a progress bar). The guide opens at *now*,
  with past shows to the left. Works with Xtream (`tv_archive`) and M3U playlists with `catchup` tags.
  If catch-up plays the wrong programme, **Settings → Playback → Catch-up time** lets you set the
  timezone it uses — your **device's**, or a **manual UTC offset** (UTC−12…+14) — that your provider needs.
- **Auto-match your channels to the guide** — the TV Guide has a new **Auto-match EPG** button that
  links channels whose tvg-id is missing or doesn't line up with your EPG feed by matching them **by
  name** (ignoring HD/country tags etc.). Confident matches are applied automatically; the rest are
  shown in a quick **review** list to accept or skip (with **Accept all** / **Skip all** shortcuts).
  Matches are saved per profile and survive re-syncing. (Fixes #13.)
- **Match a channel's EPG from the Guide** — **long-press a channel** in the TV Guide, then choose
  **Auto-match** (match just that channel by name) or **Pick manually** (choose its guide channel from
  the full list, or clear the override). The choice is saved per profile and survives re-syncing. (Fixes #10.)
- **See what's coming up in Live TV** — the channel info overlay now shows a **"Later"** row with the
  next few programmes after *Now/Next*, so you can see the upcoming schedule without opening the Guide.
  (Fixes #11.)
- **Change channels with the D-pad** — while watching a channel fullscreen with the controls
  hidden, **D-pad Up/Down** — plus the **media ⏮/⏭** keys and **CH+/CH−** — now switch channels, so
  remotes without dedicated channel buttons (e.g. Fire TV) can zap too. When the controls are showing,
  Up/Down navigate them as before. Zapping also **wraps around** — past the last channel it loops to the
  first (and vice-versa) instead of dead-ending. (Fixes #9.)
- **Sort the TV Guide** — the Guide has its own **sort** button: **A–Z**, **Provider** order, **Live TV**
  (mirrors your Live TV sort), or **Catch-up** (channels with archive first, so you can find them fast).
  (Fixes #12.)
- **See a channel's real resolution before you watch** — the Live TV preview now shows the **actual
  stream resolution** (e.g. `1080p`, `720p`, `4K`) as a badge on the preview, so a channel named
  "…4K" that's really 1080p no longer fools you.

### 🐛 Bug fixes

- **New playlists show up immediately** — after deleting a playlist and adding another, Live TV / Movies /
  Series now refresh **right away** instead of staying empty until you restarted the app.
- **Huge playlists import fully again** — some Xtream panels cut off very large movie/series lists
  mid-download, which aborted the whole import with an *"Unterminated string…"* error and left you
  unable to sign in. Now, if the bulk list truncates, OwnTV automatically **fetches it category by
  category** (small requests the server can handle) so you get your **full library** — and items keep
  populating as it goes. (Fixes #15.)
- **Faster channel switching in Live TV** — switching channels no longer feels slow or briefly "broken".
  The player now recognises that the *previous* stream's cleanup isn't the *new* stream failing, so it
  skips the needless retries/backoff (and the occasional false "Couldn't play this stream" flash) that
  could delay the preview. The Live preview pane also shows a **loading spinner** while a stream is
  opening. *(Thanks to **[@codeVerine](https://github.com/codeVerine)** — PR #14.)*
- **Left from the channel list returns to your category** — pressing **Left** into the category rail now
  lands on the folder you're actually in (e.g. the current channel's category) instead of jumping to the
  search box at the top. The category search is still there — press **Up** from the top category to reach it.
- **"Now watching" card shows the right channel** — the channel info card no longer keeps the *previous*
  channel's name after a quick zap; it updates the instant the stream changes. (#9)

## v2.2.4 — 2026-06-14

- **Back from a series returns to the right poster** — pressing **Back** inside a series (or its
  on-screen back button) now puts focus back on the **series you opened** in the grid instead of jumping
  to the sidebar (it now scrolls to and focuses it, matching how Movies already behaves).
- **No more sidebar flicker in Settings** — moving between a Settings sub-screen (Playlists, EPG,
  About…) and the Settings menu no longer makes the left rail briefly expand and collapse; it only
  expands once focus actually settles on it. (The sidebar is shared, so this covers every section.)
- **…and no category-rail flicker** — the same settle-before-expand fix now applies to the **category
  rail** (Live TV / Movies / Series), so it no longer briefly widens then collapses when focus passes
  through it during a screen transition.

## v2.2.3 — 2026-06-14

> 🔁 **Please re-sync your playlists after updating.** This release switches live channels to the more
> widely-supported **MPEG-TS** stream format — but each channel's link is built when you sync, so your
> existing channels keep the old format until you re-sync. Open **Settings → Playlists** and press
> **Re-sync** on each one so every channel picks up the change.

- **Channels that wouldn't load now play** — live streams use the universal **MPEG-TS (`.ts`)** endpoint
  instead of HLS (`.m3u8`); some Xtream providers only serve raw MPEG-TS and don't offer the `.m3u8`
  wrapper, so their channels failed to load entirely. And if a `.ts` channel still won't start, the
  player now **automatically falls back to the `.m3u8` variant** before erroring — so the rare HLS-only
  panel keeps working too.
- **Back hides the player controls first** — while watching, when the player UI is showing, **Back** now
  just hides it instead of leaving the channel; press **Back** again (with the controls hidden) to exit
  the player.
- **Smarter playback retries** — when a stream stalls, the silent auto-retry now uses **exponential
  backoff** (1s · 2s · 4s) to better ride out cold-boot decoder lag, **skips retrying when you're
  offline** (shows a "No internet" message immediately instead of spinning), and **fails faster on
  movies/episodes** — a bad VOD link errors after one try instead of three.
- **Channel zapping from the Guide** — the **CH+ / CH−** keys now surf channels while watching a channel
  opened from the **TV Guide**, stepping through the guide's channel list — just like from the Live TV
  list.

## v2.2.2 — 2026-06-14

- **Category rail highlight follows your focus** — the rail no longer keeps your current category lit
  up when you're not on it (while you're on the sidebar, on the new category-search box, or arrowing
  past other categories). Now only the pill you're focused on is highlighted, and your active category
  turns green the moment you land on it — so there's always exactly one highlight, right where the
  remote is.

## v2.2.1 — 2026-06-14

- **Search your categories** — the category rail (Live TV / Movies / Series) now has a **search box**
  at the top. Opening the rail lands right on it, so you can **type to filter** hundreds of categories
  by name and jump straight to the one you want instead of scrolling; **Down** drops into the list. The
  filter clears when you leave the rail.

## v2.2.0 — 2026-06-14

- **Multiple EPG sources** — EPG is now its own thing: **Settings → EPG Sources** lets you add any
  number of XMLTV guide feeds (with **Edit · Delete · Re-sync**), and they merge into the TV Guide.
  Adding a playlist **auto-syncs its EPG** (Xtream `xmltv.php` / M3U `url-tvg`), and the new-source
  message now breaks down what was imported — e.g. *"40K channels · 100K movies · 30K series · 30K
  EPG synced"*. The Guide's manual download button is gone (EPG syncs on add); when there's no EPG it
  shows an **Add EPG** shortcut.
- **Match a channel to a guide manually** — when a channel doesn't auto-match the EPG, open it in the
  Live preview and press **Match EPG** to pick its guide channel (searchable). Saved per profile,
  survives re-syncs; the Guide grid and the now/next card both honor it.
- **"What's New" before updating** — the startup update card now opens the **full changelog** when you
  press *What's New*, matching the manual check — so both paths show what changed before you update.
- **Back up your settings too** — Backup & Restore gained an **App settings** section (theme, accent,
  UI zoom, all Video Player settings, HDR, live-preview, sort orders…), and your **EPG sources** are
  now included with the profiles & sources backup.
- **Aspect-ratio button in the player** — the player's zoom control now works in every mode (live,
  movies and series): **Fit · Fill/Crop · Stretch · Original · Force 16:9 · Force 4:3**. It resizes the
  video surface directly, so it works with the fast direct renderer too. (Fixes #4.)
- **D-pad is now strictly for navigation while watching live** — **D-pad Up/Down** move through the
  player controls (like Left/Right) instead of changing channels. Channel surfing stays on the
  dedicated **CH+ / CH−** keys. (No CH keys on your remote? Go back to the list to pick a channel.)
- **Picture-in-Picture for live TV** — the **PiP** button now works while watching a channel: dock it
  to a corner and keep browsing the app while it streams. **Selecting another channel updates the
  docked window in place**, and its expand button maximizes it again. (Fixes #6.)
- **Playlists show what's in them** — each row in **Settings → Playlists** now lists its **channel /
  movie / series counts** (e.g. *"40K channels · 100K movies · 30K series"*) instead of the old, stale
  "EPG not downloaded" note (EPG lives on its own screen now).

### 🛠️ Fixes

- **Favorites & history survive a re-sync** — content ids change every refresh, which used to orphan
  your data: the Favorites folder showed a count (e.g. *"(2)"*) but listed nothing. Favorites, watch
  history and resume positions now **re-attach to the refreshed content automatically** (and stale
  leftovers are cleaned up), so your starred channels/movies/series and recently-watched stay put —
  including across the refresh-on-startup.
- **Hiding a group now hides its channels everywhere** — hidden categories only dropped the rail
  folder before, so their channels still showed under **All Channels**, in search and in
  recently-watched (hiding the adult groups didn't actually hide the channels). Hidden groups' channels
  now drop out of those lists and counts too.
- **Plays more streams on weak boxes** — when a device's hardware decoder can't start a stream (some
  Fire TV Sticks reject otherwise-fine channels/VOD with *"playback error… unsupported format"*), the
  player now **retries that stream in software automatically** before showing an error — so you no
  longer have to turn off hardware decoding to watch those channels.
- **Movie backdrop no longer looks clipped** — the artwork in a movie's details pane now fills its
  banner cleanly instead of showing letterbox bars (or a thin sliver when only a poster was available).
  (Fixes #5.)
- **Simpler, crash-proof video** — the renderer picker (Smooth/Auto/**Quality**) is gone. The app now
  always uses the direct, *YouTube-style* decoder-to-surface path — the best quality (full native 4K,
  HDR handled by the panel) **and** the lightest on TV hardware. mpv's heavyweight GL renderer, which
  could hard-crash the whole app on some GPUs (e.g. an emulator's translated GL), is no longer a user
  option — it's kept only as the **automatic software-decode rescue**, and is skipped entirely on
  emulators (a clean "can't decode on this device" message shows instead).

## v2.1.0 — 2026-06-13

- **Channel up/down with the remote** — while watching a channel fullscreen, press **D-pad up/down**
  (or the **CH +/−** keys) to zap to the next/previous channel in the list you opened, with a brief
  "now watching" card — no need to go back to the category.
- **TV-friendly text entry** — focusing a text field (Add source, profile creation, dialogs) no
  longer pops the keyboard and traps you; it highlights like any control, **OK** opens the keyboard,
  **Back** closes it — so you can move straight to the Save button. (Fixes #3.)
- **Easier Fire TV install** — releases now also publish a stable `OwnTV.apk` so a fixed
  `…/releases/latest/download/OwnTV.apk` link always serves the newest signed build. Fire TV users
  can install via the **Downloader code `4308278`** (`aftv.news/4308278`); README has full
  sideload instructions.

## v2.0.1 — 2026-06-14

Playback polish and fixes from real-TV testing on top of v2.0.0.

- **Keep the screen awake while watching** — the TV screensaver no longer kicks in during playback
  (live, movies or series); it returns to normal when you pause or stop.
- **Renderer modes** — the renderer picker (Settings → Video Player) now offers **Smooth** (default —
  the direct, TV-optimized path), **Auto** (picks per device), and **Quality** (the full mpv GL
  renderer — heavier on weak TVs). Each option shows a one-line hint.
- **Recovers from a busy decoder** — a stream that doesn't start (e.g. the hardware decoder is still
  busy right after a TV cold-boot) is now retried automatically a few times before any error shows,
  instead of getting stuck. A transient hiccup no longer drops you to the slower renderer for the
  rest of the session.
- **Smoother subtitles, quieter logs** — the app-drawn subtitle overlay is fed more efficiently
  (no more constant background polling).

## v2.0.0 — 2026-06-13

This update delivers the complete, long-term vision for the app. I’ve been working on this feature set for a long time! My original goal was to launch with everything ready, but I decided to get the core IPTV features into your hands early so we could catch and fix any bugs first. Now, the full roadmap is finally here. This update brings you content customization, a smarter guide, resume & complete backup, in-app updates, custom accent colors, and a top-to-bottom D-pad navigation overhaul, plus all the bug fixes from the last update.

### ✨ New features

- **Playlist-order sorting** — sync now preserves your provider's original order (channels, movies,
  series, and category/group order). Each section (Live TV / Movies / Series) has a sort chip next to
  the search bar to toggle **Playlist/Provider order ↔ A–Z**, remembered per section. Live TV defaults
  to playlist order. *(Re-sync a source once to pick up the stored order.)*
- **Full category names** — the category rail expands when focused (like the sidebar) and shows full
  names; Favorites/History show icon + label.
- **Content customization (per profile, survives re-syncs)**
  - Hide, rename, and reorder **categories** in Live TV / Movies / Series (Settings → Customize).
  - Hide and rename **channels** straight from the Live preview pane.
  - Hidden-channels list (top of Settings → Customize) to unhide.
  - Hidden channels disappear everywhere: lists, folders, favorites, section & global search,
    recently watched, and the EPG guide.
- **Custom EPG URL per source** — for **Xtream and M3U**; your own XMLTV link overrides the defaults
  (Xtream `xmltv.php` / M3U `url-tvg`).
- **Tune from the Guide** — OK on a channel name tunes straight to it; programme details have a
  **Watch channel** button.
- **Guide search** — a search bar in the Guide filters channels across the *whole* guide (not just
  the visible rows).
- **Guide lists every channel** — rows load their programmes lazily as they scroll into view, so the
  guide shows your full lineup (no more 300-channel cap) with flat memory use.
- **Resume, your way** — replaying a movie/episode with a saved position now shows a small
  *"Resume at 23:45?"* prompt (Resume / Start over). A new **Resume playback** setting in Video Player
  settings picks the behavior: **Always resume · Ask to resume (default) · Never resume**.
- **In-app updates** — OwnTV updates itself straight from GitHub Releases: automatic check shortly
  after launch (toggleable via **Settings → Check updates on startup**), or manually via
  **Settings → Check for updates**. The startup check shows a small **top-right status card**
  ("Checking… / You're up to date", auto-hides) that stays with *Update now / Later* when a release
  is newer; the manual dialog shows the **full changelog**. Updating downloads the APK with progress
  and hands it to the system installer — no storage permission needed (the APK stays in app-private
  storage).
- **Custom accent colors** — the accent picker grew from 5 presets into a full **palette + hex code**
  input (e.g. `#52DBC8`); the whole Material theme is generated from your color.
- **Simpler Settings** — the Personalization sub-menu was dissolved: **Theme** (picker), **Accent
  color** and **UI Zoom** now live directly under Appearance (avatars are edited per profile in
  Profiles).
- **Selective backup & restore** — exporting asks *what* to include (profiles & sources,
  customizations, favorites, history, resume positions — or everything), and restoring shows the
  file's contents and lets you pick which parts to apply.
- **Restore on first launch** — setup now starts with a choice: create a new profile, or **restore
  everything from a backup file** (profiles included) without creating a throwaway profile first.
- **TV-style search bars** — focusing a search bar no longer opens the keyboard; it highlights like
  any control and the keyboard opens on **OK** (applies to Live/Movies/Series, the Guide and global
  Search).
- **About screen** — Settings gained a proper About dialog (version, license, author, project link);
  the old "Star on GitHub" / "Report a bug" browser links were removed (TV browsers are no place to
  send people).
- **EPG status** — the Guide shows *"Guide loaded: N channels · M programmes"*; each source row in
  Settings shows its EPG state (✓ + count, or "not downloaded").
- **Complete backup** — Backup & Restore now covers *everything*: profiles, playlists/sources,
  customizations, **favorites, watch history, and resume positions**. Favorites/history/resume
  re-attach automatically once the restored sources finish syncing (episode data attaches when you
  open the show).

### 🛠️ Fixes & stability

- **Runs properly on real TVs** — a top-to-bottom playback overhaul for TV-class hardware:
  - **Direct-to-display rendering**: on TV devices the hardware decoder now writes frames straight
    to the screen (the same zero-copy pipeline YouTube/Netflix use) — smooth 4K HDR with the TV's
    own native HDR handling, faster channel starts, and a far lighter memory footprint. Text
    subtitles are drawn by the app Netflix-style; a **Renderer** setting (Auto / Quality) can force
    mpv's full GL renderer (complete ASS/PGS subtitle styling + zoom modes) on devices that can
    afford it, and the app falls back to it automatically where direct rendering isn't available.
  - The player's memory scales to the device (the old emulator-tuned 256 MB stream buffer
    OOM-killed budget 4K TVs): lean buffers and cheaper framebuffers on low-RAM devices.
  - A **decode watchdog** stops playback with a clear message if a 4K/8K stream would fall back to
    software decoding (which overloads TV chips).
  - The image cache is capped, going to the background releases the stream immediately, and the
    app sheds caches when the system signals memory pressure instead of getting killed.
- **No more freezes (ANRs)** — all player commands run off the UI thread; a stalling stream can no
  longer lock up the remote. Fast preview-scrolling coalesces loads (only the channel you land on is
  opened).
- **Blank player fixed** — preview → fullscreen now **reuses the running stream** instead of
  reconnecting (no overlapping connections, which tripped strict 1-connection providers with
  HTTP 509). The transition is seamless now, too.
- **Live-drop recovery** — temporary provider errors (e.g. connection-limit responses right after a
  channel switch) are now retried at the network layer and usually ride over invisibly; if a live
  stream still dies, the player shows the buffering spinner and auto-reconnects, and only then a
  proper error + Retry — never a silent black screen.
- **Guide fixes** — the grid now picks only channels that actually have programmes (was scanning the
  first 300 by number) with case-insensitive EPG-id matching (fixed "guide loaded but empty"); Back
  in the Guide no longer blocks exiting the app.
- **Episode resume actually works now** — resume positions for series episodes were read on play but
  never saved; episodes now save progress every 10s like movies (and track prev/next in the queue).
- **Crash fixed** when hiding a live channel (Paging re-collection).
- **Profile PIN locks can now be removed** — the profile editor gained a *Remove PIN lock* toggle
  (previously a blank PIN field just kept the old PIN forever).
- **Restoring a backup keeps you in Backup & Restore** — it no longer bounced the app back to the
  Settings menu mid-restore (the profile swap briefly emptied the profile list, which reset the UI).
- **Category rail performance** — virtualized list + overlay expansion: buttery smooth with hundreds
  of categories (the channel grid is no longer re-laid-out during the animation).
- **Layout fixes** — the Movies download button no longer stretches; preview-pane buttons reflowed;
  the sort chip matches the search bar height.
- **Focus fixes** — rename dialogs focus their text field; the source edit form focuses the Name
  field; Settings → Sources restores focus after add / edit / re-sync / failed import.
- **D-pad navigation fixed everywhere** — moving between panels no longer lands on whatever happens
  to be horizontally aligned: entering the category rail always lands on the **selected folder**,
  entering the sidebar lands on the **current section**, entering a content pane lands on the
  **last-focused (or first) item — never the search bar**, every Settings sub-screen opens on its
  first control, and closing any dialog returns focus to the row that opened it. Returning from
  playback puts focus back on the **exact item you played** — the channel row in the Guide, the
  episode in a show, the poster in Movies/Series, the row in Downloads.

---

## v1.0.0 — First public release

Native Android TV IPTV **player** (bring your own M3U / Xtream sources):

- Live TV, Movies, Series with folder rail, favorites, history, and per-folder + global search
- Full **EPG guide** (time × channel grid) + now/next in the Live preview
- **libmpv (FFmpeg)** playback — plays nearly anything, full audio/subtitle track support, custom TV
  HUD, mini-player/PiP, HDR passthrough
- Multiple **profiles** with PIN lock & kids flag; sources shareable between profiles
- Offline **downloads** for movies & episodes
- **Backup & Restore** (profiles + sources), per-source User-Agent, refresh-on-startup,
  default source
- Material 3 design (AMOLED dark / light), accent colors, UI zoom, avatars
- Scales to huge playlists (tested ~64k channels / ~169k movies)
