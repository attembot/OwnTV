# OwnTV — User Guide & Hidden Features

A quick tour of everything OwnTV can do. Most of these are **TV‑remote (D‑pad) shortcuts** that aren't
obvious at first glance — once you know them, the app is a lot faster to live in.

> **v4.0.0+ UI Update**: The app now features a completely redesigned shell with a **fixed sidebar** nav,
> a **top bar** with live clock, weather, search, and playlist name, and **rounded panels** for crisp content.
> Navigation is faster and more stable — panels don't jump around anymore.

> Navigation basics: **D‑pad** to move, **OK/Center** to select, **Back** to go up a level. The left
> column is the **navigation panel** (Search · Home · Live TV · Movies · Series · Downloads · Guide ·
> Settings). Press **Left** from a content list to jump back to it.

---

## 📱 Add a playlist from your phone (Remote setup)

Typing an Xtream server or a long M3U URL with a TV remote is painful. **Remote setup** lets you fill
the form on your phone instead.

1. **Add source → Remote.** In the first‑run wizard or **Settings → Manage sources → Add source**, pick
   **Remote** (the other option, **Manual**, is the type‑it‑here form).
2. **Open server.** The TV shows a **QR code**, a **URL**, and a **6‑digit PIN**.
3. **On a phone or laptop on the same Wi‑Fi**, scan the QR (or open the URL). The page asks for the
   **PIN** shown on the TV, then shows a form with **Xtream / M3U / Stalker** tabs.
4. **Fill the form and tap “Send to TV.”** The details appear in the Add Source screen on the TV, with
   the matching type selected and the fields filled.
5. **Press Start Import on the TV** with the remote — the phone only fills the form; it never starts the
   import. Leave the Remote screen (Back) and the server stops automatically.

*Security:* the QR contains only the URL, never the PIN; a fresh PIN is generated each time and every
submission must include it. *Core idea from a community suggestion (PR #66 by @zarga03).*

---

## ⚡ Adding a playlist — per-section sync

- **Run in background**: while a playlist is importing (setup wizard or Settings), press
  **Run in background** to enter the app right away — the import keeps going, and a small
  **status pill at the bottom of the screen** shows its progress ("Syncing *playlist* · N items").
- When adding an **Xtream** or **Stalker** playlist you can pick **What to sync** per section:
  **Now** imports first (you get into the app as soon as that part is ready), **Later** syncs
  in the background, and **Off** means the section is never fetched or shown. You can turn an
  Off section back on later from **Settings → Manage sources → Edit**.
- **Stalker portals default to Live Now, Movies/Series Later** because Stalker VOD has no bulk
  endpoint (~14 items per page) — the movies & series crawl runs as a background worker that
  survives app restarts. The status pill shows it running; re-syncs skip categories that haven't
  changed.
- **Re-syncs are incremental**: refreshing a playlist only writes what actually changed on the
  provider, so re-syncing big playlists is much faster.
- **Resync asks what you want**: choosing to refresh a playlist offers **Resync now** (add and update,
  keep everything you already have) or **Resync and remove missing titles** (also drop titles the
  provider no longer lists — that run only). Neither one re-imports from scratch, and neither touches
  your favourites, history, resume positions or manual ordering. If a provider answers badly,
  OwnTV refuses to remove titles rather than emptying your library.
- **M3U playlists can carry movies and series too** — tag entries in the playlist and OwnTV sorts
  them into the right tab:
  - `type="vod"`, `type="movie"` or `tvg-type="movie"` → the **Movies** grid.
  - `type="series"` or `tvg-type="series"` → the **Series** tab. Per-episode lines like
    `Show Name S01E05` (or `1x05`) are grouped into shows with seasons and episodes automatically.
  - Untagged entries stay in **Live TV**.

---

## 📡 Stalker / Ministra portal

Some IPTV providers use the Stalker (MAG portal) protocol — you add them with a **Portal URL**
and a **MAC address** (no username/password). Once added, a Stalker portal behaves like any other
playlist: Live TV, Movies, Series, downloads, TMDB metadata, backup, and the playlist switcher all
work the same.

### Add a Stalker source
1. **Settings → Manage sources → Add source → Stalker (MAC)** (also available in the first‑run
   setup wizard).
2. Enter the **Portal URL** (e.g. `http://host:port/c/`) and the **MAC address** your provider
   gave you (e.g. `00:1A:79:AA:BB:CC`).
3. (Optional) Pick a **Device model preset** for the User-Agent if your portal is picky about the
   MAG box model (MAG250/254/270/420). The default works on most portals.
4. Tap **Test connection** — it verifies the handshake before saving. A "Connected" message means
   the portal accepted the MAC (if the portal reports a subscription end date, it's shown too).
5. **Start Import** — Live channels, Movies, and Series all populate, just like an Xtream source.

### Notes & troubleshooting
- **Series episodes** load when you open a show (episode names show as "Episode 1, 2, …" —
  Stalker portals don't provide per-episode titles).
- **EPG**: now/next on the channel preview comes straight from the portal. For the full TV Guide,
  OwnTV uses the portal's XMLTV feed if it advertises one — otherwise paste an XMLTV URL in
  **Settings → EPG**.
- **Catch-up**: channels whose portal keeps an archive get the usual catch-up features (Guide
  "Watch from start", the Live TV catch-up picker, live rewind).
- **"Portal refused the login"** — check the MAC address (copy it exactly), and check the TV's
  **date & time** (Stalker validates request timestamps; a clock more than a few minutes off
  fails the handshake).
- **Stream drops after a long watch** — Stalker links expire after a few hours; OwnTV re-fetches
  a fresh link automatically (a brief re-buffer, then playback continues). Long downloads survive
  this the same way: the download resumes from where it stopped with a fresh link.

---

## 🗂️ Multiple playlists — switch & set a default

Have more than one playlist (e.g. a main one and a backup)? OwnTV can show them **all merged together**,
or **narrow the whole app to just one**.

- **Quick switcher (top bar):** when you have 2+ playlists, the **playlist chip in the top‑right** becomes a
  button with a **▾**. Open it to pick **All playlists** or a single playlist. Your choice applies **everywhere
  at once** — Live TV, Movies, Series, TV Guide, Search, and the Home rails (Continue Watching / Favourites) —
  and **sticks after a restart**. Home refreshes in place after you pick, so you don't have to leave and
  return to see the new source. No need to go into Settings to switch.
- **Set a default playlist (Settings → Sources):** open **Add / Edit** on a playlist and turn on
  **“Default playlist.”** That playlist becomes the one shown across the app. The Sources list shows a
  **DEFAULT** badge on it — it's a status marker, not a button.
- **Show everything again:** pick **All playlists** from the top‑bar switcher, or edit the default playlist and
  turn **“Default playlist” off**. With no default set, **every playlist is shown** (the merged view).
- **What the filter affects:** categories, channels, movies, series, the guide, search results, and the
  **Favourites** and **History** rails inside each section all respect the selected playlist. Nothing is deleted
  or re‑imported — it's only a view filter, so switching back to **All** brings everything right back.
- Your selected default is included in **Backup & Restore** (Sources section).

---

## 🏠 Home — Continue Watching

- The **Home** tab opens to a row of what you were watching — partly‑watched **movies, episodes and recent
  live channels**, newest first.
- **Dwell to expand:** hold focus on a hero card for **3 seconds** and it widens to a big 16:9 preview and
  starts a **muted video preview**. Quick D‑pad sweeps never expand, so browsing stays snappy. Press **OK**
  to **resume right where you left off**. When **TMDB metadata** is available, the expanded hero shows a
  **landscape backdrop**, the show's **title logo**, a short **plot** and a **Play** action.
- Below are more rows — **Favourite Channels**, **Continue Watching Movies/Series**, and an optional
  **Recent Channels** row (off by default). **Continue Watching series** tiles resolve **episode/show
  artwork from TMDB** when available and show as **landscape cards** (with a `S## E##` chip and a
  progress bar), falling back to the provider poster otherwise.
- ▶️ **"Continue" chip (top bar, every screen):** a compact chip resumes your **most‑recent** item in one
  press — **Resume** a movie, **Next up** an episode, or your **Last channel** — labelled with the title.
  Reach it from the navigation panel (like the search pill); it hides when there's nothing to resume.
- 🧩 **Make Home yours (Settings → Home screen, per profile):** **reorder or hide every row**, **filter the
  Keep Watching hero** (include/exclude live channels, movies, series), and switch the live‑channel rows
  between **Cards** and **On Now** — an inline mini‑guide showing what's airing now with a progress bar and
  the next hours (Up/Down picks a channel, Left/Right scrolls the timeline, OK tunes). The **Android TV
  home** toggle also lives on this page. Your layout is saved per profile and included in backups.
- 🧭 **Trim the side menu to your playlist (Settings → Sidebar Menu Customization):** a VOD‑only playlist
  no longer shows Live TV / Guide, and a Live‑only playlist hides Movies / Series / Downloads. Switch
  **Behavior** to **Dynamic** and the six side‑menu icons auto‑adapt to what the active playlist actually
  contains (Home & Settings always show; counts refresh after every sync), or keep **Static** (the default)
  and manually toggle any icon on or off. Your choice is included in backups.

---

## 📺 Live TV

- **Categories** are in the second column. Long category names **wrap to two lines** so they're never cut off.
- **Live preview**: focus a channel and its video plays in the preview pane (with the **real stream
  resolution**, e.g. `1080p`/`4K`, so a mislabelled "4K" channel can't fool you). Toggle this in
  **Settings → Playback → Live preview**; sound for the preview is **Settings → Playback → Preview audio**.
- 🖼️ **The preview pane is info-only now.** There are **no buttons** in it — Favorite, Rename, Hide,
  Match EPG and Catch-up all live in the **long-press menu** (below). Because nothing in the pane is
  focusable, **right-arrow no longer drops you onto the buttons by accident** — D-pad stays in the
  channel list. A short note at the bottom reminds you: **OK to watch fullscreen · long-press for options**.
- 🏷️ **Channel info row**: under the name, small chips show the channel's **real category** (so a
  channel you reached via Favorites / History / All still shows the category it actually belongs to),
  its **genre** with a colour dot, **catch-up** availability (with days, e.g. `Catch-up · 7d`), and
  **EPG coverage** (`EPG · Nd` when the stored guide span is known, else `EPG` / `No EPG`). Unmatched
  categories get a neutral grey **Other** dot — every channel has a genre marker.
- 📺 **Now playing on every row**: each channel in the list shows the **programme currently airing**
  (a small line under the name) when guide data is available — so you can see what's on at a glance.
  Channels with no guide show a single line as before.
- ⭐ **Add to Favourites (and more)**: **long‑press OK** on a channel to open the quick menu — **Favourite,
  Rename, Hide, Match EPG, Catch‑up, Play in external player**. (Closing it returns you to the same channel.)
- 📼 **Catch‑up from the channel list**: the long‑press **Catch‑up** picker lists recent programmes; picking
  one opens the **same programme popup as the Guide** — description and times, then **Watch from start**,
  **Watch channel**, favourite the channel, or close.
- 🎯 **Match EPG is smarter**: the picker lists **guide channels similar to the channel's name first**
  (best match on top; searching re‑ranks too), and the **Close / Clear match** buttons sit in a column on
  the **right** — press **Right** from any row to reach them. The same right‑side layout applies to the
  Guide's **Auto‑match review** popup (**Accept all / Skip all / Done**).
- 🔄 **Move channels** (reorder within folders/Favorites): **long‑press OK** on a channel and choose **Move** —
  a full‑screen reorder overlay opens with the full list. Use **D‑pad Up/Down** to move the item, **OK** to save,
  **Back** to cancel. Your reorder is saved across playlist re‑syncs and included in backups.
- **Open a channel full‑screen**: press **OK**.
- 🔀 **Page long lists with CH+ / CH−**: with hundreds of categories or thousands of channels, hold‑scrolling
  top‑to‑bottom is painful. **CH−** skips N items **down** (toward the last), **CH+** skips N items **up**
  (toward the first) — in whichever panel has focus (the category column **or** the channel list). **Long‑press
  CH−** jumps straight to the **last** item, **long‑press CH+** to the **first**. Skips are clamped at the ends,
  so a short list reaches the end in one press for free. (Long‑press is disabled on the built‑in **All**
  list — jumping to the 170,000th item is pointless — but short‑press skipping still works there.) Set the
  skip counts or turn it off in **Settings → Content → CH+- Key Paging** (default: on, 10 items per press).
  Same shortcut works in **Movies**, **Series** (grid + episode list), and the category list in
  **Settings → Customize Categories & Items**.

### Inside the full‑screen live player
- 🗓️ **Top bar**: bring up the controls (press OK) and one strip across the top shows **back · channel
  logo · quality/audio chips · channel name**, then the **Now / Next** programmes with times (from your
  EPG, or the provider's short guide). The Now line has a thin progress bar and the minutes left, and
  updates by itself. It's informational only — it never takes D‑pad focus.
- **Left key → channel list**: with the on‑screen controls hidden, press **Left** to pop up a **channel
  list overlay** for the **playing channel's own category** (its name is the heading) — scroll and **OK**
  to switch channels without leaving full‑screen. Each row also shows the **current programme** (small
  line) so you can pick by what's on. Press **Back** to close it.
- 🗂️ **Left again → category browser**: from that channel list, press **Left** a second time for a list of
  **every Live TV category** (hidden ones left out, renames and your manual order kept). **OK** reloads the
  channel list with that category's channels — the stream keeps playing — and **CH+/−** then surfs the
  category you picked. Your current category is highlighted and focused first; **Back** or **Left** returns
  to the channel list unchanged.
- **Right key → History list**: press **Right** with the controls hidden for the **last 30 channels you
  watched**, so you can hop back without leaving full screen. Press **Right** again, or **Back**, to close.
- ⓘ **Stream info** is the right‑most button on the control bar; **Back** exits full screen (there's no
  separate exit button).
- **CH+ / CH−** (or Up/Down on the channel‑list overlay) zap through the current category. **CH+, D‑pad
  Up and Next all move to the next channel**; **CH−, D‑pad Down and Previous** to the previous one. The
  list wraps, so CH− on the first channel lands on the last, and CH+ on the last returns to the first.
  D‑pad Up/Down zap only while the controls are hidden — with the control bar up they navigate it.
- 🔢 **Type a channel number to tune**: in full screen, just **key in the number** (number row or numpad)
  and OwnTV switches to that channel. The digits show top‑left with a **bar that drains over two seconds**
  before it submits — press **OK** to go immediately, **Back** to cancel, or keep typing (five digits submit
  on their own). When it resolves, the same card turns into the **channel card** (logo · name · number) and
  holds until the channel is on screen; if nothing matches you get **"Channel not found"**.
  - The number is your **provider's channel number**, looked up in the **playlist you're watching**. Only
    if that playlist has no such channel are your other active Live playlists searched.
  - **Hidden channels and categories are skipped** and renamed channels show your name. If a playlist uses
    one number for several visible channels you'll see **"Multiple channels"** instead of a guess.
  - **CH+ / CH−** keeps working straight after a numeric jump, even when the channel is far outside the
    list you opened. Numbers are shown in the **Live TV channel list** and the **channel‑list overlay** (in
    a column ahead of the name), in the **full‑screen top bar** before the channel name, and on the player's
    channel card — so you can learn the ones you use.
  - Number keys are only captured on a **live channel in full screen** — during **catch‑up or timeshift**
    they're left alone.
  - **"Channel numbers"** (on by default) controls all of this — in **Settings → Video Player → Live TV**,
    or as a **quick‑toggle chip** at the top of Settings. Off hides every number and ignores the number
    keys; nothing is lost, and turning it back on restores them.
- 🔧 **Compatibility mode (two playback engines)**: live channels play on the fast **ExoPlayer** engine by
  default. If a channel shows **UHD artifacts**, won't open, or stutters, bring up the controls and press the
  **engine toggle (the ⇄ MPV/EXO pill)** — this **pins that channel to the mpv engine**. The pill always shows
  the engine that's **actually playing** (teal while on mpv, whether you pinned it or OwnTV auto‑switched), and
  **one tap always flips** the engine — a small "Switched to MPV/ExoPlayer" note confirms it. It's **remembered
  per channel**, so that one channel always uses mpv while everything else stays fast.
  Picking **ExoPlayer** on a channel that had auto‑switched to mpv is treated as your decision: the automatic
  fallback stops interfering with that channel until you tune elsewhere, so it can no longer flip straight back
  to mpv. If ExoPlayer genuinely can't play it, press the toggle again to return to mpv.
- 📶 **Prefer HLS for Live TV (Xtream only)**: Xtream panels can serve a live channel either as raw MPEG‑TS or
  as an HLS playlist. OwnTV asks for **MPEG‑TS**, which is what most panels serve best — but if your provider's
  live channels are unstable, turn on **Prefer HLS for Live TV** when adding the source, or later in
  **Settings → Manage sources → (your source) → Edit**. It's stored **per source**, so with two providers you
  can prefer HLS on one and leave the other alone. Catch‑up and timeshift follow the same choice. The ⓘ
  **Stream info** overlay has a **Format** row showing which one you're actually receiving.
- 🔇 **Audio with no picture**: if a channel ever plays sound but shows a black screen, OwnTV now detects this
  automatically and switches engines for you (briefly shows a loading spinner). If neither engine can render
  video for that stream, you'll see a clear on‑screen message instead of a silent black screen.
- ⏪ **Catch‑up / rewind live**: on a channel that supports catch‑up (look for the marker, or use the
  long‑press **Catch‑up** menu), you can **rewind into the provider's archive** and play back from the past,
  then return to live.

---

## 🗓️ TV Guide (EPG)

- Open **Guide**. It loads instantly and opens scrolled to **now**.
- **Sort** the guide: A–Z · Provider · Live TV order · **Catch‑up** (archive‑capable channels first).
- ▶️ **Play catch‑up from the guide**: move **Right** into the timeline to a **past programme**, press
  **OK** to open its details, then choose **"Watch from start"** to replay it from the archive. Scroll
  **Left/Right** along the timeline to pick the programme you want.
- 📍 **"Now" line & Jump to Now**: a red vertical line marks the current time across the grid; the
  **Jump to Now** button (top‑right) scrolls the timeline back to now — handy after browsing the
  catch‑up archive.
- ↻ **Catch‑up & genre hints**: programmes you can rewind from show a ↻ badge, and each channel label
  carries a small colour dot hinting at its **genre**, based on the channel's **category name**:
  🟢 green = sport · 🔴 red = news · 🟣 violet = movies/film/cinema · 🟡 amber = kids/animation ·
  🔵 blue = music · 🩵 teal = documentary. Channels whose category doesn't match any of these show
  **no dot** (a missing dot is intentional — better than a misleading colour). The dot reflects the
  channel's category/group, not its individual name, so it depends on how your provider names its
  categories. (The **Live preview** info row uses the same colour system, but there unmatched
  categories show a neutral grey **Other** dot so every channel still has a genre marker.)
- 📋 **Cursor preview strip**: while browsing a row (move **Right** into the timeline), a strip at the
  bottom shows the programme under the cursor — title, channel, time, runtime, catch‑up, synopsis —
  without opening it. Press **OK** to open the full details.
- **EPG is opt‑in**: add guide feeds in **Settings → EPG Sources**. After importing a playlist you'll be
  offered a one‑tap **sync now** (with a live programme count), or you can sync later from Settings.
  During first‑run setup that sync also has a **Run in background** button — enter the app while the
  guide keeps downloading.
- 🔄 **EPG sync status**: when a guide feed is downloading (a manual resync or the automatic startup/
  staleness refresh), the same **status pill** that reports playlist syncs shows "Updating guide ·
  *source* · N programmes" at the bottom of the screen. When a **playlist sync finishes** the same pill
  shows the result for a few seconds — "Sync complete · *source* · N categories added", or "Sync
  failed"/"Sync cancelled" — so you always know how a resync ended; if several finish back-to-back
  they queue and show one after another.
- ⭐ **Favourites from the Guide**: **long‑press a channel label** to add/remove it from Favourites
  (the same menu also holds the EPG match options), or use the **Favourite** button inside a
  programme's details. Favourites apply everywhere — Live TV, Search, and the Home Favourites rail.
- **Auto‑match EPG**: the guide can smart‑match your channels to guide data; you can also fix one channel
  manually via the long‑press channel menu.
- 🙈 **Hidden categories stay hidden**: categories you hide via long‑press → Customize are excluded
  from the Guide too — the "Category" dropdown and the guide rows both respect them (category
  renames and manual order carry over from Live TV as well).
- 🖼️ **Use this guide's channel logos (per EPG source)**: when adding or editing an EPG feed
  (Settings → EPG Sources), turn this on to show that feed's own channel logos instead of your
  playlist's — everywhere channels appear. Channels the feed has no logo for keep the playlist logo.
  Your playlist logos are never overwritten, so turning it off restores them instantly. Re-sync the EPG
  source once after switching it on, so the logos get stored.
- 🔄 **Auto refresh (per source)**: each **playlist** (Settings → Manage sources) and each **EPG feed**
  (Settings → EPG sources) has an **Auto refresh** dropdown — **Off** (default), **Refresh at startup**,
  or an interval (playlists 6–48h, EPG 1–48h). Intervals refresh only when the source is actually stale,
  checked on app start and when you return to the app. Everything stays **Off** until you turn it on.

---

## 🎬 Movies & 📺 Series

- **Grid / List toggle**: switch the poster wall to a compact **List** view (top‑right button) to scan many
  titles at once.
- 🔀 **Page the grid/list with CH+ / CH‑** — see **Live TV** above. Works on the category column and the
  poster grid/list (and the episode list inside a series); long‑press jumps to first/last.
- **Detail pane**: focus a title to see its **poster, rating, plot** and **Play/Resume · Favourite ·
  Download** buttons.
- **Resume**: partly‑watched titles offer **Resume** (vs. Play). Choose how this behaves in
  **Settings → Resume** — **Ask**, **Auto** (silently continues), or **Never**.
- ⏭️ **Auto‑play next episode**: when an episode ends, the next one starts automatically — and it rolls into
  the **next season** when the current one finishes. Toggle in **Settings → Auto‑play next episode**.
- ⏳ **Next‑episode countdown**: in the last ~30 seconds of an episode a card counts down to the auto‑advance,
  with **Play now** (jump immediately) and **Cancel** (stop the auto‑advance for this episode).
- Series **open on your last‑watched episode**.
- ✅ **Watched state at a glance**: episodes (Series) and movie posters/list rows show a ✓ (dimmed) once
  watched to ≥95%, and a thin progress bar when part‑watched. Series season chips show a `watched/total`
  count (e.g. `Season 2 · 8/18`).
- ✏️ **Mark a movie watched / unwatched**: long‑press a movie → **Mark as watched** (or **unwatched**). A
  **Resume <time>** label appears under the poster in the detail pane while a movie is part‑watched.
- ▶️ **"Next up" card** (Series): the episode detail pane shows a **Next up** card with a one‑press
  **Play** for the episode to continue with — the one you're mid‑way through, or the next after the last
  finished one (resume time shown when in progress).
- 🙈 **Hide watched** (Series, header button): filters the episode list to what's left to watch.
- ↕️ **Sorting** (Series, header button): opens a popup with two rows — **Seasons** and **Episodes** — each
  switching between **Oldest first** and **Newest first**. The choice is saved **per show and per profile**,
  so a daily-news series can stay newest-first while everything else stays in normal order. It only changes
  the display order; nothing about watched state or playback changes.
- ✏️ **Mark as watched / unwatched** (Series): long‑press an episode → **Mark as watched** (or **Mark as
  unwatched** if already watched) to correct the auto‑detected state without playing it. Marking watched
  restarts the episode from the beginning next time you press Play.
- 🔄 **Move movies/series** (reorder within categories/Favorites): **long‑press OK** on any title and choose **Move** —
  a full‑screen reorder overlay opens. Use **D‑pad Up/Down** to move, **OK** to save, **Back** to cancel.
- 📥 **Download via long‑press**: **long‑press OK** on a movie or episode and choose **Download** to queue it
  immediately (Movies) or queue all cached episodes (Series). No need to open the detail pane.
- 📤 **Play with external player via long‑press**: the same long‑press menu can open the movie/episode in an
  external app (VLC, MX Player, …) — one‑off, regardless of the global **External player** setting.
- 🔧 **Two playback engines with automatic fallback**: movies/episodes play on **mpv** by default (or
  **ExoPlayer** if you switched the **Movies & Series player** setting). If the chosen engine can't play an
  item, the **other engine is tried automatically** before any error. You can also switch the **current**
  movie/episode manually: bring up the controls and press the **engine toggle (the ⇄ MPV/EXO pill)** — it
  flips between mpv and ExoPlayer at the same position (the pill shows the active engine; teal while on
  ExoPlayer, and a small "Switched to MPV/ExoPlayer" note confirms it). Handy when one engine doesn't show a
  subtitle or audio track you know exists — flip and check. Like Live TV's compatibility mode, the choice is
  **remembered for that movie/episode** — it opens on that engine from then on, while other items keep
  following the setting.
- 🏷️ **Which engine is playing?** The player top‑left mini chips now start with **MPV** or **EXO** (on Live
  TV too), so you always know the active engine at a glance.
- 🏷️ **Bitrate in the top‑bar chips.** The mini chips also show the stream's **bitrate** (Mbps) when the
  provider declares it — for Live TV (preview & full), movies and series, on both engines. Raw `.ts`
  live channels that don't declare a bitrate leave it blank; open the **info overlay (ⓘ)** for a live
  measured value.

---

## 🎬 TMDB metadata (posters, plots, cast, trailers)

- **Settings → Metadata (TMDB):** pick a **Metadata source** — *Provider only* (no TMDB), *Provider + TMDB*
  (default; your playlist's info wins, TMDB fills the blanks and adds cast/genres/backdrops), or *TMDB only*
  (prefer TMDB). Turn on **Advanced options** to use your own TMDB API key or a self-hosted server; otherwise
  the built-in shared server is used with no setup. A "Test lookup" button verifies it works.
- 🌍 **Language** — pick the language TMDB descriptions, titles and artwork come back in: **Default
  (English)**, **Device language**, or one of 40 languages (the list is searchable). Changing it clears
  the cached metadata so existing movies and series are re-fetched in the new language; your
  title→TMDB matches are kept, so nothing has to be re-matched.
- 💡 **Recommended: use your own TMDB API key** (free for personal / non-commercial use) or a self-hosted
  server. TMDB keys are typically issued instantly — no waiting period or manual approval — and your own
  key means you're never affected by shared-server rate limits. Create one at
  [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api), paste it into **Settings →
  Metadata → TMDB API key (v3)**, and hit **Test lookup**.
- 🌐 **Self-host your own metadata server (free):** the exact Cloudflare Worker OwnTV's shared server runs
  is in the repo at [`worker/tmdb/`](../worker/tmdb/) — [`worker/tmdb/README.md`](../worker/tmdb/README.md) has the full
  step-by-step (deploy with `wrangler`, set your TMDB key as a secret via
  [`worker/tmdb/wrangler.toml`](../worker/tmdb/wrangler.toml) + `wrangler secret put TMDB_KEY`, then paste your
  `https://….workers.dev` URL into **Settings → Metadata → Custom metadata server URL**). Your key stays
  on your Cloudflare account, and responses are edge-cached for 30 days.
- **Movies/Series details:** focus a title to see enriched info in the side pane. **Long-press** a poster for
  Favorite, Download and **TMDB Details** (a scrollable window with the backdrop, full plot, cast and genres;
  press **Back** to close). **Single-press** plays.
- 🙈 **Hide a movie or series:** long-press a title → **Hide** removes it everywhere at once — global Search,
  the section search, its category, the All list, Home rails (Continue Watching / Favourites), the Android TV
  Watch Next row, and Downloads. The downloaded file is kept, and the title comes back the moment you unhide it
  from **Settings → Customize Categories & Items**. (Hiding a whole **category** now hides its items everywhere too,
  matching Live TV.)
- **Series & episodes:** open a series to see the episode list with a detail pane on the right — focus an
  episode to see its TMDB still, plot and rating. Episode rows: **single-press plays**, **long-press** for
  Download / TMDB Details.
- **Sorting:** the sort chip cycles **Provider → A–Z → Rating → Date added**. Rating shows the highest-rated
  titles first. **Date added** shows the newest titles first, using the date your provider stamped on each
  movie/series; titles with no date fall to the bottom in playlist order.
  **A–Z also sorts the category folders** (in Live TV too) — categories you manually reordered in
  **Settings → Customize** stay pinned at the top; the rest sort alphabetically below them.
- **Refetch TMDB details:** long-press a movie, series, or episode → **Refetch TMDB details** forces a fresh
  TMDB search — it clears a wrong/stale match (or a 7-day "no match" cache) and re-searches at once, so you
  don't have to wait for the cache to expire. Use it when the art/plot is missing or looks wrong.
- **Set TMDB name:** long-press a movie or series → **Set TMDB name** opens a dialog
  pre-filled with the cleaned title; type the exact TMDB title (and an optional year to disambiguate) and
  Save forces a fresh TMDB search under that name. Clear removes the override and re-searches with the
  cleaned provider title. The escape hatch when matching still gets a title wrong (or it's stuck in the
  7-day "no match" cache).
- 🎞️ **Trailers:** long-press a movie or series → **Play Trailer** (shown only when TMDB has one). The trailer
  plays in a floating window: **Back or Exit** closes it, **◀/▶** seeks ±10 seconds. If the built-in player
  can't run on your box, OwnTV opens the trailer in the YouTube app instead.
- **Attribution:** OwnTV uses the TMDB API but is not endorsed or certified by TMDB.

---

## 🕐 History

- Browse **recently watched movies, series and channels**.
- ✂️ **Remove single item**: **long‑press OK** on any history item and choose **Remove from History** to
  delete just that entry (keeps the rest).
- 🧹 **Clear entire history** (by type): Settings → Content → **Clear watch history** — wipe all recently‑watched
  items, or just **Live TV, Movies or Series**. Playlists, Favorites and Downloads are untouched.

---

## 🔎 Search

- The **Search** tab searches **Live, Movies and Series together**.
- 🚀 **Launcher home**: with the box empty, Search shows a **"Jump to"** row — **Continue watching**,
  **Unwatched** and **Channels** — plus your **recent searches** as chips (tap **Clear** to wipe them).
  Tap a chip to jump straight in without typing.
- 🖼️ **Detail pane**: focus any result to see its **poster, plot and rating** on the right, with a
  **primary action** button (Play / Watch live / Open series). Pressing **OK** on the result still plays
  it directly.
- ↩️ **Back**: the first **Back** clears your query (back to the launcher); a second **Back** leaves Search.
- You can **favourite a channel straight from search** via **long‑press**.

---

## 📥 Downloads

- The **Downloads** tab groups items into **Active · Waiting · Completed · Failed**, with a **storage
  bar** at the top showing free space.
- **Long‑press / OK** a card for **Pause · Resume · Retry · Delete**. A failed download tells you to
  **Tap Retry**.
- 📍 **See it downloading without leaving the page**: when you start a download of a **movie**, a **whole
  series**, or a **single episode**, a small **status strip** (Downloading / Queued / Paused, with a
  progress bar) appears at the top of that item's **poster panel**. It only shows while a download is in
  flight and disappears once it finishes.
- 📍 **Downloads keep going when you leave the app.** A transfer runs in the background with a
  notification, one at a time, and survives OwnTV being closed or killed. Pausing saves exactly what has
  been written, so resuming picks up from there instead of starting over.
- ⚠️ If the **USB stick or SD card you are downloading to is removed**, that download is marked
  **Failed** — it will not quietly continue into internal storage.

---

## 🎛️ Player controls (reference)

Bring up the controls in any full‑screen player (press OK / a direction). The bottom bar has:

| Button | What it does |
|---|---|
| **Subtitles** | Pick a subtitle track (incl. **image subtitles**) and set **subtitle delay**. Live channels with **embedded closed captions (CC)** — common on US channels — show a CC track on both engines; on mpv, selecting it briefly switches the channel to software decoding (≤1080p) and hardware decoding returns when CC is turned off. On raw `.ts` channels the CC entry always appears, even when the channel carries no captions. |
| **Audio** | Pick an audio track, and **A/V sync** (audio delay, **±50 ms** steps) — use this if surround makes lips drift. |
| **Info** (ⓘ) | Toggle the **stream info overlay**: codec · resolution · fps · HDR · bitrate · decoder · audio · buffer. |
| **Favorite** (♥) | Add or remove what you're watching from **Favorites** without leaving the stream — a live channel, a movie, or a series (an episode favorites its parent show). The heart fills when it's already a favorite. |
| **Speed** | Playback speed (VOD). |
| **MPV/EXO (⇄)** | Live: **compatibility mode** — pin the channel to mpv. Movies/Series: **switch this item between mpv and ExoPlayer** (shows the active engine; teal on the non‑default one). Flipping it briefly confirms "Switched to MPV/ExoPlayer" at the bottom. |
| **Aspect/Zoom** | Change aspect ratio / zoom (works in every render mode). |
| **PiP** | Picture‑in‑picture for live. |
| **Headphones** | **Audio Mode** — see below. |
| **Volume** | mpv VODs/channels can be **boosted to 150%** for quiet streams. |

---

## 🎧 Audio Mode (listen with the screen free)

Audio Mode plays the **sound only** and stops video decoding entirely, so you can browse the app
while the current channel, movie or episode keeps playing. A compact **now‑playing bar** appears in
the top bar — an animated equaliser, the title, and controls (play/pause, previous, next, volume,
fullscreen, close). Live shows a pulsing **LIVE** badge; movies/episodes show a slim progress line
with the remaining time.

- **Turn it on** with the **headphones button** on the full‑screen player controls, or on the docked
  mini‑player.
- **Using the bar (D‑pad):** move focus onto it and the whole bar highlights; press **OK** to step
  **inside**. Now **Left/Right** move between the buttons and **OK** runs the highlighted one. Focus
  stays inside the bar — press **Back** to step out.
- **Fullscreen** returns to full video; **close** (✕) stops playback.

---

## 💬 External subtitles (OpenSubtitles & local files)

For **movies and series episodes** (streamed or downloaded), the player's **Subtitles** menu has an
**ADD SUBTITLES** section:

- **Search OpenSubtitles** — needs a free [opensubtitles.com](https://www.opensubtitles.com) account,
  connected per profile in **Settings → Video Player → Subtitles → OpenSubtitles**. The search is
  pre-filled for the playing title; use **Edit search** if the provider's name is odd, and **All
  languages** to widen it. Pick a result and it downloads, turns on immediately, and is remembered
  for that title. Your remaining daily downloads (set by OpenSubtitles per account) show in the
  account screen and after each download — re-downloading something you already fetched costs
  nothing. If you open this while not signed in, it shows a note pointing you to that Settings screen
  to connect your account.
- **Select local subtitle file** — no account or internet needed. Browse USB/internal storage for a
  `.srt` / `.ass` / `.ssa` / `.vtt` / `.webvtt` file. Non-UTF-8 files (Arabic, etc.) are converted
  automatically, and OwnTV keeps its own copy so the subtitle survives unplugging the USB.
- **ADJUST → Subtitle timing** — nudge the active subtitle **earlier/later** in 0.1 s / 0.5 s steps
  while the video plays. The offset is saved for that exact subtitle on that title.
- On **replay**, previously downloaded subtitles for the title are re-listed in the Subtitles menu
  (not auto-selected) — pick one and its saved timing comes back too.
- **Deleting**: long-press a movie/episode → **Delete OpenSub subtitles**, or manage everything in
  **Settings → … → OpenSubtitles → Delete subtitles** (per profile).

---

## 🎨 Personalize (make it yours)

- **Settings → Customize Categories & Items**: **hide, rename and reorder** categories, plus **unhide**
  individual channels, movies and series from one place. Pick a section at the top (Live TV / Movies /
  Series) — hidden items are listed first, each with an **Unhide** button, and your categories follow below.
  With a long provider category list, use **CH+ / CH−** to page it (long‑press = first/last folder).
  - **Hide a range of categories fast**: focus a category's **Hide** button and **long‑press (select‑hold)** it to
    enter **span/range mode**. Then scroll **up or down** — every category between your starting point and the
    category you land on gets hidden together as a range. Handy for quickly hiding a big block of categories (or
    even scrolling all the way to hide most of the list) instead of hiding them one by one.
  - ↕️ **Move a block of categories together**: the same span trick works on the **move** buttons
    (**⤒ ↑ ↓ ⤓**). **Long‑press** any of them on the first category, then press an arrow on the last
    category — every category in between moves as one block: up/down a step, or straight to the
    top/bottom. The block **stays selected**, so you can keep pressing the arrows to walk it further.
    Press **Back** (or **Cancel** in the banner) when you're done. Great for lifting all your sports or
    kids categories to the top at once instead of one at a time.
  - 🗂️ **New category behavior (Show / Hide)**: at the top of the screen, choose what happens to a
    category your provider adds on a **later re-sync** — **Show** (default) or **Hide** it automatically.
    Handy if you keep only a few categories visible and don't want new ones popping up. It's per profile
    and rides in Backup & Restore. When a re-sync changes categories, the completion message tells you how
    many were **added / removed**. With two or more playlists in view, category lists are **grouped by
    provider** and each Customize row shows which provider it belongs to.
  - 🔒 **Optional PIN lock**: tap **Set PIN** at the top-right to lock this screen. Once set, opening
    Customize Categories & Items asks for the PIN each time, so nobody else can unhide items or change your category
    setup. The PIN is per-profile and is **not** included in backups (so a restore can never lock you out).
    Change or remove it from the **Change PIN** / **Remove lock** buttons at the top-right.
- **Settings → Theme / Accent colour / UI Zoom**: dark/AMOLED/light, a tint colour, and scale the whole UI.
  - The **Accent colour** dialog has quick presets plus a full colour picker: focus the **hue bar** or the
    **saturation/brightness square**, press **OK** to step in (it glows amber), move with the **D-pad**, then
    **OK/Back** to step out. A live preview shows the result. You can also type an exact **hex code** and **Apply**.
  - ⚠️ Going **below 85% zoom** shows a warning first — lower zoom draws many more items at once, which can
    crash devices with limited memory (e.g. 2 GB TV sticks) with big playlists/EPG. Press **OK** to accept
    and continue, or **Back** to stay at 85%.
- **Settings → Glass Effect**: a **frosted‑glass look** — panels turn translucent with a
  real blurred backdrop over an optional **background photo**.
  - The dialog has **Liquid glass On/Off**, a **Background image** chooser, a **Transparency** stepper
    (20–95%, higher = more solid), a **Blur / Frost** stepper (0–100%), a **Surfaces** menu, and **Reset**.
  - **Background image — Local or Remote.** **Local** browses USB/device storage for a JPG/PNG/WebP/BMP
    (it's copied into the app, so unplugging the stick can't blank it). **Remote** shows a **PIN + QR** —
    scan it with your phone on the same Wi‑Fi, enter the PIN, send a photo, and it applies instantly.
    **Clear** removes the background.
  - **Surfaces** toggles the glass per area — content panels, sidebar, preview panes, dialogs & popups,
    top bar, cards, mini‑player — or all at once. Turning everything off turns glass off.
  - The frost (blur) needs a background image and **Android 12+**; otherwise panels are simply
    translucent. All glass settings are kept in backups.
- **Settings → Animations**: turn interface motion **off** for a snappier feel on lower‑end TV boxes.
- **Profiles** (Settings → Profiles): multiple viewers, a **Kids mode**, and **PIN locks**.

---

## ⚙️ Settings worth knowing

- 🔎 **Search settings** — type in the **"Search settings…"** box at the top to filter the whole screen to
  matching rows; results show their group (e.g. `Playback › HDR`) and open the setting directly. **Back**
  clears the search first. Above it, one‑press **quick toggles** (Live preview · Preview sound · HDR ·
  Auto‑play · Check for update) flip the most‑used options without opening a sub‑menu.
- 🧭 **Menu layout** — **Profiles** is the first row; **Live preview / Preview audio** are under
  **Playback**; **App startup** is under **App**; the **Home screen** page is under Content.
- 🔀 **CH+- Key Paging** (Content) — page the category & item lists in Live/Movies/Series (and the
  category list in **Customize Categories & Items**) with the remote's **CH+ / CH−** keys. Separate skip
  counts per direction (typed or ±‑stepped), long‑press jumps to first/last, with an advisory warning
  above 50. Turn it off here if your remote maps CH keys elsewhere.
- 🚀 **App startup** — where each profile opens: **Home**, **Last channel** (auto‑plays the channel you last
  watched), or **Live · Favorites** (lands you right inside your favourites list).
- 🗂️ **Browsing & lists** (Content) — six toggles, two for each of **Live TV / Movies / Series**.
  **Remember last category** (on) reopens the section on the category you left instead of *All*.
  **Remember last item** (off) makes each category keep its own scroll position instead of starting at
  the top — with Live TV also restoring the last focused channel. These are independent of **App startup
  → Last channel**.
- 🌈 **HDR** — use HDR output when the video and TV support it. Turn on for HDR/Dolby Vision content.
- 🎞️ **Auto frame rate** (Playback, on) — in full screen, asks the TV to switch to a refresh rate matching
  the video (24/25/30/50/60 fps) and hands the display back on exit, so 24fps films and 25/50fps
  broadcasts stop juddering on a 60Hz panel. Works for Live TV and VOD on both engines, and never
  changes resolution. Turn it off if your TV or receiver re-handshakes HDMI noisily on every channel
  change.
- 🧩 **Hardware decoder** (Video Player Settings) — hardware decoding is on for smooth 4K; switch to software
  only if a specific codec misbehaves.
- 📡 **Live latency** (Video Player Settings) — how close to the live edge Live TV plays, trading latency
  against stability: **Low latency**, **Balanced** (default), **Stable**, or a **Custom** buffer in seconds.
  It applies on the next channel open, to live streams only, on both engines. **Balanced** changes nothing
  (so it can't regress a working stream); picking **Low latency** or a below‑Balanced custom value warns
  first that a smaller buffer can stutter on weaker connections.
- 🪟 **Mini‑player** (Settings → Playback) — set the docked live‑PiP window's **size** (percentage of screen
  width) and **screen position** (four corners plus top/bottom centre). Both are also adjustable **on the
  fly** from the mini‑player's own resize / move controls, and the window scales with your TV size and UI zoom.
- 🎬 **Movies & Series player** (Video Player Settings) — which engine plays movies/episodes first:
  **mpv** (default — widest format support incl. DTS/TrueHD audio, plus the A/V sync fix) or
  **ExoPlayer** (try it **only if movies/episodes won't start** on your device — it can't decode
  DTS/TrueHD audio and has no A/V sync fix). Either way, if the chosen player fails, the other is
  tried automatically before an error is shown. The player's **info overlay** shows which engine is
  active.
- 📊 **Measured stream stats** (Video Player Settings → Diagnostics) — on by default. When on, the
  player's **info overlay** measures live fps, bitrate and dropped frames for streams that don't
  declare them (most Xtream live TV). Turn it **off** only if a low‑end TV ever stutters — it affects
  the diagnostic numbers only, never the actual video.
- 💬 **Subtitle appearance** (Video Player Settings) — a menu with a preview, a **Customize subtitles**
  switch, and then **Size**, **Text color**, **Position** (six anchors: top/bottom × left/center/right)
  and **Background transparency** (None → Solid in 10% steps). **Each one starts at "Default", and
  Default leaves that aspect alone** — turning the switch on changes nothing until you pick something,
  so you can set only the background and keep the stream's own colours, including the styling
  broadcasters embed in Live TV captions. Subtitle size lives here now. Image subtitles
  (PGS/VOBSUB/DVB) are pictures and always render as authored.
- 📤 **External player** (Video Player Settings) — opens a popup with **separate On/Off switches for
  Live TV, Movies and Series**, so you can send live channels to VLC (or MX Player, …) while keeping
  movies in OwnTV, or any other mix. **Downloads** follow the Movies or Series switch, depending on
  what was downloaded. Live TV is **off** by default. You can also play a **single item** externally
  without any setting: **long‑press OK** on a channel, movie or episode and choose **Play in external
  player** (Downloads have an **External** button). Note: resume position, the OwnTV controls and
  next/previous aren't available while an external app plays.
- 📼 **Catch‑up** (Playback → Catch‑up) — the same popup that holds the catch‑up **timezone/offset** now
  also has **Play catch‑up in**: **OwnTV player** (default), **External player**, or **Always ask**.
  With *Always ask*, pressing **Watch from start** on a recording asks which player to use each time.
- 🌦️ **Weather** — its own submenu: **Show weather** (top‑bar chip on/off), **Custom location** (city or
  "lat,lon"; blank = auto‑detect — set this if a VPN shows the wrong city), and **Temperature unit**
  (**°C / °F**).
- 🔊 **Surround sound** — ⚠️ **off by default, opt‑in.** Turn it on **only if you have a real 5.1/7.1
  receiver**. On TV speakers or a stereo soundbar it can make **audio lag behind video (lip‑sync drift)** —
  if you enable it and see drift, fix it live with the player's **Audio → A/V sync** nudge. Most people
  should leave this off.
- 🩺 **Playback error log** (Playback) — the last ~10 playback failures with their plain‑English
  reason, stream details and device info. If a channel or movie errored and you dismissed the
  message, open this to read (or clear) exactly what happened — perfect for bug reports, no computer
  needed.
- 🔄 **Check updates on startup** — get notified when a newer version is on GitHub Releases.
- 💾 **Backup & Restore** — export/restore your profiles, sources, customizations, favorites, history,
  resume positions, **manual Move positions** and app settings. Export starts by asking **which
  profiles** to include — the file contains only the selected profiles and their data. Including a
  **PIN-locked profile** that isn't your current one requires entering its PIN; without the PIN it
  simply stays out of the backup. Then choose the data sections as before. Backups are saved as a
  single **`owntv-backup.own`** file, which also carries your **background image** (with the App
  settings section) so the wallpaper comes back on the other TV instead of blank. On export you can set
  a **backup password**, which encrypts the **whole file** — playlists, profiles, history and the saved
  secrets (source & proxy passwords, your own TMDB API key and each profile's **OpenSubtitles login**)
  — so nothing in it can be read without that password. **Keep it safe: a protected backup cannot be
  opened at all if you lose the password.** Without a password the file is not encrypted and those
  secrets are simply left out. Restoring a protected `.own` asks for the password **first**, then shows
  what's inside; there is no Skip, since nothing can be restored without it. **Older `.json` backups
  still restore** — those ask for the password after you pick the sections, and **Skip** restores
  everything except the saved passwords, as before. **Restore merges — it never deletes your existing profiles or
  sources:** a profile with the same **name** as one already on the device is updated from the backup,
  profiles only in the backup are added, and everything else stays put (that's also why profile names
  must be unique — the app matches by name). Backups also preserve your **per‑source Auto refresh** choices,
  your **default source**, any **compatibility‑mode / per‑item engine pins** (Live and Movies/Series),
  your **custom TMDB names** (long‑press → Custom TMDB name) and recent searches,
  so a restored setup behaves exactly like the original. Older backup files still restore fine — anything
  they don't contain just keeps its default. (An older OwnTV version cannot read a new `.own` file, so keep
  a `.json` backup if you plan to go back to one.) **Move a backup between TVs over Wi‑Fi:** choose **Restore
  from another device** (also offered in the setup wizard) to show a PIN + QR — a phone or laptop on the
  same network uploads a backup file straight to the TV, which then runs the normal restore. **Send to
  another device** does the reverse, serving the exported backup for a remote device to download. No USB
  stick or cloud needed; the local USB/file flows still work as before.
- 🧹 **Clear watch history** — wipe a profile's recently‑watched / continue rows.
- 📥 **Downloads** — download movies/episodes for offline play; pick the **Download folder** (app storage or
  external). To browse outside app storage, choose **Grant full storage access** in the folder picker — it
  opens OwnTV's app-settings page where you enable **Allow management of all files** (on Android 10 and
  below a normal permission dialog appears instead), then press Back to return to the picker.

---

## 🛠️ Building your own custom M3U playlist

Making your own `.m3u`/`.m3u8` by hand (or with a script)? OwnTV decides which tab each entry lands in
**purely from the `#EXTINF` line** — the tag you put on it, not the file it points to. Get the line right
and your content sorts itself into **Live TV**, **Movies** or **Series** automatically.

**The rule OwnTV uses (in order):**

1. If the entry is tagged **series** → it goes to the **Series** tab.
2. Otherwise, if it's tagged as a **movie/VOD** → it goes to the **Movies** grid.
3. Otherwise (no VOD tag at all) → it stays in **Live TV**.

The tag can be written as either `type="…"` **or** `tvg-type="…"` — both are accepted:

| You want it under… | Add this attribute to the `#EXTINF` line |
|---|---|
| **Live TV** | *(nothing — any untagged entry is treated as a live channel)* |
| **Movies** | `type="movie"` **or** `type="vod"` **or** `tvg-type="movie"` **or** `tvg-type="vod"` |
| **Series** | `type="series"` **or** `tvg-type="series"` |

### Anatomy of a line

Every item is **two lines**: an `#EXTINF` metadata line, then the stream URL on the next line.

```
#EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Display Name
http://your-server/stream.ext
```

- **`group-title="…"`** — the **category name inside the tab** (e.g. a Live TV category, a Movies
  category, or a Series category). Entries with the same `group-title` are grouped together.
- **`tvg-logo="…"`** — poster/channel logo URL (optional).
- **`tvg-id="…"`** — for **Live TV**, this is the EPG channel id used to match guide data (optional).
- **Display Name** — the text after the final comma. This is the title shown in the app.

### Live TV example

```
#EXTM3U url-tvg="http://your-server/epg.xml"
#EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="http://logo/bbc1.png" group-title="UK Channels",BBC One
http://your-server/live/bbc1.ts
```

> `url-tvg="…"` on the `#EXTM3U` header line is picked up as the playlist's EPG source automatically if
> you haven't set one. Catch-up attributes (`catchup="default"`, `catchup-source="…"`, `catchup-days="7"`)
> are also read on live entries.

### Movies example

```
#EXTINF:-1 type="movie" tvg-logo="http://logo/inception.jpg" group-title="Action",Inception (2010)
http://your-server/movie/inception.mkv
```

### Series example — this is the important one

Tag each **episode line** with `type="series"`, and put the **season/episode marker in the Display Name**.
OwnTV reads the marker to group episodes into shows, seasons and episodes:

```
#EXTINF:-1 type="series" group-title="Drama",Stranger Things S01E01
http://your-server/series/st-s01e01.mkv
#EXTINF:-1 type="series" group-title="Drama",Stranger Things S01E02
http://your-server/series/st-s01e02.mkv
#EXTINF:-1 type="series" group-title="Drama",Stranger Things S02E01
http://your-server/series/st-s02e01.mkv
```

- The text **before** the marker becomes the **show name** — so all three lines above merge into one show
  *Stranger Things* with a Season 1 (2 episodes) and a Season 2 (1 episode).
- The text **after** the marker becomes the **episode title** (optional), e.g.
  `…,Stranger Things S01E01 - The Vanishing`.
- **Supported markers** (case-insensitive):
  - `S01E05` — also written `s1e5`, `S01 E05`, `S01.E05`, `S01-E05`.
  - `1x05` — the "1x05" style.
- **Keep the show name identical** across its episodes (spelling/case aside — matching is
  case-insensitive) so they group into the same show.
- If an episode line has **no marker**, it's still added, but as a plain sequential episode under that
  name — so always include a marker when you can.

> **Tip:** the `group-title` on a series entry becomes its **category** in the Series tab, not the show
> name — the show name always comes from the Display Name before the marker.

---

## 💡 Tips

- **Long‑press OK** is your friend — favourites, rename, hide, match EPG and catch‑up all live there.
- A channel buffering or showing artifacts on 4K? **MPV/EXO toggle → compatibility mode** usually fixes it.
- Live channels from one provider glitching every few seconds while another provider is fine? Try
  **Prefer HLS for Live TV** on that source — some Xtream panels are far steadier over HLS.
- Audio out of sync on a VOD? **Audio → A/V sync** and nudge ± until lips match.
- **Guide looks blank when you first open it?** (especially with catch‑up channels) Try: **Settings → EPG** → tap Edit → delete your EPG source(s), then **add them again** and sync fresh. The v4.0.0 update changed how EPG loads, and old cached data needs to be cleared and reimported. Once done, the guide displays immediately.

---
*OwnTV is free, open‑source and ad‑free, forever. Found something confusing or missing from this guide?
Open an issue on GitHub.*
