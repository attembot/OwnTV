# OwnTV — App Changelog (minimal)

> Short release notes shown inside the app's update dialog: two parts per version — New features
> (by name) and Fixes. The full, detailed changelog is [CHANGELOG.md](CHANGELOG.md). Hand-maintained —
> edit this file directly alongside CHANGELOG.md, condensing per version (do not copy bullets verbatim).

## v4.0.2 — 2026-07-07

### ✨ New features

- 🏠 **Customizable Home screen — reorder/hide rows, dwell-to-expand hero, On Now mini-guide** (community PR #58 by @codeVerine)
- ⚙️ **Settings menu reorganized** (Profiles on top; Live preview under Playback; App startup under App)
- 🗂️ **Multiple playlists — switch the whole app to one playlist (or all)**
- ✨ **VOD engine fallback (movies & series play on more devices)**
- 🔄 **Per-source Auto Refresh (playlists & EPG)**
- 💾 **Backup & Restore now covers every persistent setting**
- 🎬 **TMDB metadata enrichment (Movies, Series & Episodes)**
- 🎞️ **In-app trailers for Movies & Series**
- 🙈 **Hide individual movies & series — and a Customize PIN lock**
- ✨ **External player — play movies, series & downloads in VLC / MX Player** (global setting + per-item long-press; Live TV stays in-app)
- 📺 **Live TV closed captions now work (#57)** (CEA-608 CC on both ExoPlayer & mpv engines)
- 🌦️ **Weather settings submenu — Celsius / Fahrenheit**
- ⚠️ **Low-zoom memory warning (#51)**

### 🐛 Fixes

- **Live channel-list overlay now matches the channel you launched from Home (#55)**
- **Active nav section stays visible when focus moves away (#47)**
- **4K Live channels no longer break playback on some TVs**
- **Live engine pill now shows the engine that's actually playing**
- **Live TV zoom / aspect modes now work**
- **Fill / Crop now actually zooms in and crops**
- **Weather chip: VPN-friendly location override + hide toggle (#45)**
- **Modal D-pad focus can no longer escape into the UI behind it (#48)**
- **Focus returns to the right item after a long-press context menu (#46)**
- **Fixed D-pad navigation from the Movies/Series grid to the detail pane**
- **Fixed episode long-press menu losing focus after Refetch TMDB details**
- **Failed TMDB lookups are no longer remembered as "no match" for 7 days**

## v4.0.1 — 2026-07-03

### 🐛 Fixes

- **D-pad focus no longer jumps to the top bar while scrolling long lists**
- **Top-bar Search button now appears only while the highlight is on the left nav panel**
- **Autoplay next episode no longer fails with a "malformed or corrupted" error**
- **Player HUD no longer steals D-pad focus from overlays drawn above it**

## v4.0.0 — 2026-07-02

### ✨ New features

- ⚡ **Much faster syncing & background updates (community PR #40 by @codeVerine)**
- **Backup now covers more settings and encrypts saved passwords**
- **Manually reorder channels, movies and series**
- **Remove a single item from History**
- **Download from long-press menu**
- **Settings → Customize Category**
- **Global HTTP proxy support**
- **Home screen with Continue Watching**
- **Stream technical info overlay**
- **Volume boost to 150%**
- **Fixed, roomy layout — no more "sandwiched" Live TV**
- **Shell redesign — new sidebar, top bar, and rounded panels**
- **Clear watch history**
- **Favorite a channel straight from Search**
- **Detailed channel search results**
- **Move categories to top / bottom**
- **Animations setting (On / Off)**
- **Channel list in the player**
- **Per‑profile startup (default landing)**
- **Remembers where you were in Live TV**
- **Guide by category**
- **Favourites in the Guide**
- **List view for Movies & Series**
- **A/V sync nudge in the player**
- **One-tap guide sync after adding a playlist**
- **Long-press a channel in Live TV**
- **Closed captions (CC) on Live TV**
- **Compatibility mode (per-channel mpv engine)**
- **Movies & Series open instantly**
- **The Guide opens instantly**
- **Much faster EPG sync**
- **Leaner TV Guide internals**

### 🐛 Fixes

- **Live TV could give up reconnecting too early during a real outage**
- **Audio-plays-but-no-video no longer leaves you stuck on a black screen**
- **Favorites could disappear after a source re-sync failed partway through**
- **Live TV no longer freezes silently mid-stream**
- **EPG match no longer removes a channel from the Guide**
- **Show/Hide password toggle on all password fields**
- **Per-source User-Agent for playback**
- **No more false "Playback error" over a movie that's actually playing**
- **Startup focus rests on the nav**
- **Clear watch history now empties Movies/Series from Home too**
- **Live preview shows full stream spec**
- **Startup → Live · Favorites lands inside the list**
- **Long‑press channel menu keeps focus on the channel**
- **Clearer Surround sound warning**
- **Imports survive a provider that errors on the full Movies/Series list**
- **EPG no longer fails on a single malformed tag**
- **Playback survives the screensaver**
- **Live TV no longer freezes with no recovery**
- **No sound when opening a channel very fast**
- **One corrupted file no longer breaks all playback**
- **Audio/video drift on some movies**
- **Long-press to favourite in Movies and Series**
- **Sync no longer wipes data on failure**
- **Sync times out fast instead of spinning forever**
- **M3U VOD entries now route to Movies**
- **Offline banner now works on all devices**
- **Profile dialog focus no longer escapes**
- **Two-stage video watchdog**
- **Guide shows programmes on first open**

## v3.2.0 — 2026-06-22

### ✨ New features

- **Live rewind (timeshift)**
- **Switch profile without leaving the app**
- **Wider category folders**
- **Catch-up defaults to your device timezone**
- **Longer Guide catch-up**
- **Clearer audio-track icon**

### 🐛 Fixes

- **Audio & subtitle selection now works on Live TV**
- **No more silent playback for AC3/DTS files played as live**
- **Live audio no longer keeps playing after you exit/log out**
- **Clearer error for an unplayable movie**
- **Playback errors now show the real reason**

## v3.1.2 — 2026-06-21

### 🐛 Fixes

- **Surround sound is now off by default (opt-in), with a safety net**
- **Live TV recovers from connection drops**
- **Screen no longer sleeps during Live TV**

## v3.1.1 — 2026-06-21

### ✨ New features

- **Near-instant Live TV (two playback engines)**
- **Import a playlist from a local file**
- **EPG is now opt-in**

### 🐛 Fixes

- **Surround sound no longer stutters video**
- **M3U live channels that wouldn't play now work**
- **4K channel zapping no longer hangs**
- **Episodes now appear for every Xtream series**
- **Global search opens the right series**

## v3.1.0 — 2026-06-20

### ✨ New features

- **Catch-up straight from Live TV**
- **Hide/show a whole range of categories at once**
- **Auto-play next episode**
- **Series open on your last-watched episode**
- **Surround sound passthrough**

### 🐛 Fixes

- **Faster channel zapping**
- **Live channels that dropped out every few seconds now play continuously**
- **Smoother video on TVs**
- **Installs on non-TV devices now**
- **EPG sources that failed with a "protocol error" now load**
- **Image-based subtitles now play smoothly**
- **Big-library import no longer gets stuck**

## v3.0.0 — 2026-06-17

### ✨ New features

- **Browse the TV Guide timeline**
- **Catch-up TV (archive)**
- **Auto-match your channels to the guide**
- **Match a channel's EPG from the Guide**
- **See what's coming up in Live TV**
- **Change channels with the D-pad**
- **Sort the TV Guide**
- **See a channel's real resolution before you watch**

### 🐛 Fixes

- **New playlists show up immediately**
- **Huge playlists import fully again**
- **Faster channel switching in Live TV**
- **Left from the channel list returns to your category**
- **"Now watching" card shows the right channel**

## v2.2.4 — 2026-06-14

### ✨ New features

- **Back from a series returns to the right poster**
- **No more sidebar flicker in Settings**
- **…and no category-rail flicker**

## v2.2.3 — 2026-06-14

### ✨ New features

- **Channels that wouldn't load now play**
- **Back hides the player controls first**
- **Smarter playback retries**
- **Channel zapping from the Guide**

## v2.2.2 — 2026-06-14

### ✨ New features

- **Category rail highlight follows your focus**

## v2.2.1 — 2026-06-14

### ✨ New features

- **Search your categories**

## v2.2.0 — 2026-06-14

### ✨ New features

- **Multiple EPG sources**
- **Match a channel to a guide manually**
- **"What's New" before updating**
- **Back up your settings too**
- **Aspect-ratio button in the player**
- **D-pad is now strictly for navigation while watching live**
- **Picture-in-Picture for live TV**
- **Playlists show what's in them**

### 🐛 Fixes

- **Favorites & history survive a re-sync**
- **Hiding a group now hides its channels everywhere**
- **Plays more streams on weak boxes**
- **Movie backdrop no longer looks clipped**
- **Simpler, crash-proof video**

## v2.1.0 — 2026-06-13

### ✨ New features

- **Channel up/down with the remote**
- **TV-friendly text entry**
- **Easier Fire TV install**

## v2.0.1 — 2026-06-14

### ✨ New features

- **Keep the screen awake while watching**
- **Renderer modes**
- **Recovers from a busy decoder**
- **Smoother subtitles, quieter logs**

## v2.0.0 — 2026-06-13

### ✨ New features

- **Playlist-order sorting**
- **Full category names**
- **Content customization (per profile, survives re-syncs)**
- **Custom EPG URL per source**
- **Tune from the Guide**
- **Guide search**
- **Guide lists every channel**
- **Resume, your way**
- **In-app updates**
- **Custom accent colors**
- **Simpler Settings**
- **Selective backup & restore**
- **Restore on first launch**
- **TV-style search bars**
- **About screen**
- **EPG status**
- **Complete backup**

### 🐛 Fixes

- **Runs properly on real TVs**
- **No more freezes (ANRs)**
- **Blank player fixed**
- **Live-drop recovery**
- **Guide fixes**
- **Episode resume actually works now**
- **Crash fixed**
- **Profile PIN locks can now be removed**
- **Restoring a backup keeps you in Backup & Restore**
- **Category rail performance**
- **Layout fixes**
- **Focus fixes**
- **D-pad navigation fixed everywhere**

## v1.0.0 — First public release

### ✨ New features

- Live TV, Movies, Series with folder rail, favorites, history, and per-folder + global search
- Full **EPG guide** (time × channel grid) + now/next in the Live preview
- **libmpv (FFmpeg)**
- Multiple **profiles** with PIN lock & kids flag; sources shareable between profiles
- Offline **downloads** for movies & episodes
- **Backup & Restore**
- Material 3 design (AMOLED dark / light), accent colors, UI zoom, avatars
- Scales to huge playlists (tested ~64k channels / ~169k movies)
