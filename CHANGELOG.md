# Changelog

## v4.1.6 — 2026-08-01

### ✏️ Bulk editing — rename channels, movies and series in bulk (#86)

- **The Customize screen got a proper item list.** A category's name is now a focusable button (one
  D-pad press per row, instead of walking past the arrows first): OK opens that category's items —
  every row visible, hidden items marked and recoverable, paged so a category with tens of thousands
  of rows still opens instantly.
- **Per-row Rename on Live TV** renames one channel from the item list; Movies and Series get bulk
  rename only (one film exists in ten languages, so per-row renames are meaningless there).
- **Bulk rename with rules.** Long-press Rename (or use the header pill on Movies/Series) to select a
  span with the same remote-friendly flow as Hide/Move. Build an ordered rule list that adds or
  removes prefixes and suffixes, supports several removable alternatives separated by semicolons,
  and optionally ignores case and trims leftover separators. The live review shows every proposed
  name before anything is written. Applied names are stored per profile and section, survive
  re-syncs, and appear everywhere — Live TV, Movies, Series, search and recently-watched.
- **Auto cleanup** creates an editable rule preset for country/provider tags, quality and codec tags,
  emoji, symbols and stray separators, then opens the same review before applying anything.
- **Restore original names** undoes a bulk rename for the whole span — the only undo for a bulk
  apply, so it is one tap away and always available.
- Renamed names travel in backups and are per profile, like every other customization.

### 🗂️ Custom combined categories (#87)

- **Create your own combined categories** from the Customize screen — a "＋ New category" pill on
  every section. A combined category can hold channels, movies or series from any source folder,
  side by side, in one rail at the top of Browse.
- **Move to category…** is available in the Live/Movies/Series context menu and on the Customize
  item list. Items leave their origin folder by default (they stay findable in All Channels and
  search); tick "Keep in <origin> as well" to keep a copy. Moving out of Favorites un-favourites
  the item; moving out of another combined category removes it there.
- **Combined categories behave like folders**: rename them, delete them (items stay in their
  original categories), reorder their items manually, hide them from Browse, and they remember
  their own last-focused item. They are per profile and survive re-syncs and backups.
- **"Hide new categories by default"** is now set from the add-source window too — a profile-wide
  default so categories that arrive on a future sync start hidden until you show them from
  Customize.

### 🌐 Custom DNS server — global, app-wide (#90)

- **Settings → Network → DNS** is a new screen, a sibling to the existing Proxy screen. Configure a
  custom DNS server and all OwnTV domain lookups — playlist sync, Xtream/Stalker API, EPG, images,
  ExoPlayer streams — resolve through it instead of the system resolver.
- **Two modes, auto-detected from what you enter.** A plain IP like `8.8.8.8` or `1.1.1.1:53` sends
  standard DNS-over-UDP queries. A URL starting with `https://` — e.g. `https://dns.google/dns-query`
  — uses DNS-over-HTTPS (RFC 8484), which encrypts your lookups so they can't be snooped or tampered
  with between your device and the server.
- **One-tap presets for Google, Cloudflare and Quad9** fill the DoH URL instantly and save it. They light
  up to show which one is active.
- **The DNS server is live-updated** — flip it on and off, or swap servers, and every future lookup uses
  the new setting immediately. No client rebuild, same pattern as the global proxy.
- **"Test DNS"** resolves `dns.google` through your configured server and reports the resolved IPs and
  round-trip time, so you can confirm it's working before saving.
- **The toggle alone is not enough.** Turning it on reveals the server field and preset buttons. Only
  when a server is entered and saved is DNS actually enabled — a red warning reminds you while the
  toggle is on but no server is configured. Toggling it off immediately disables custom DNS.
- Custom DNS is backed up and restored alongside the other settings. It does not contain secrets, so it
  travels in plaintext in the settings section.
- **mpv/FFmpeg is not affected** — it uses the system resolver internally and has no configurable DNS
  option. The setting covers everything that goes through OkHttp, which is most of the app's traffic.
- Built-in support for Android's `org.json` parser so the DoH JSON responses (per RFC 8484) are parsed
  with zero additional dependencies.

### 🐛 Fixes

- **Live TV playback switched to the channel's provider category after opening it from Favorites,
  History, All Channels or a custom category.** The preview pane still shows the channel's real
  provider category as metadata, but full-screen playback now keeps the browse context it was launched
  from. D-pad Up/Down, CH+/− and the Left channel-list overlay all stay within that same context. A
  category explicitly chosen from the in-player category browser becomes the new playback context.
- **Auto frame rate could still blank the TV when the setting was Off.** Media3/ExoPlayer and mpv each
  had their own surface-level frame-rate request in addition to OwnTV's window-level controller, so
  disabling the toggle did not stop every display-mode request. All three paths now obey the setting,
  and turning it off clears an already-applied surface hint. AFR defaults to Off in v4.1.6; existing
  installs are reset to Off exactly once, while any choice the user makes afterward is preserved.

## v4.1.5 — 2026-07-31

### 💬 Subtitle appearance — size, colour, position and background, each optional (#96)

- **Settings → Video Player → Subtitle appearance** is now a menu rather than a single row: a live
  preview of how subtitles will look, a **Customize subtitles** master switch, and — once it's on —
  **Size**, **Text color**, **Position** and **Background transparency**, each opening its own popup.
- **Every option starts at "Default", and Default means "don't touch it".** Turning the master switch
  on changes nothing by itself. Set only the background transparency and only the background changes;
  everything else keeps the look the stream or the renderer gives it, including the styling
  broadcasters embed in Live TV captions and the fonts and colours authored into ASS subtitles.
- **Background transparency** answers the original request: a ±10% stepper from **None** (fully
  transparent) to **Solid**, so subtitles over a bright scene can get a readable backdrop without
  blacking out a strip of the picture.
- **Text color** offers quick presets, a full colour picker and a hex code.
- **Position** is six fixed anchors — top/bottom × left/center/right — drawn as miniature screens so
  you can see where the text lands before choosing. Useful when a channel burns a ticker or a logo
  into the exact spot subtitles normally sit.
- **Size** moved here from its own settings row, and now sits under the master switch alongside the
  rest. If you had already changed subtitle size, the switch is turned on for you at upgrade so your
  size is preserved.
- The look is applied by **all three subtitle renderers** — mpv's own, the Compose overlay used for
  mpv direct rendering, and the Media3 view used for Live TV and image subtitles — so it doesn't
  change depending on which engine happens to be playing. Image subtitles (PGS/VOBSUB/DVB) are picture
  data and are drawn as authored.
- Settings are per profile and travel in backups.

### 🆕 Date added — sort Movies and Series by what arrived most recently

- **"Date added" is a new sort mode for Movies and Series**, alongside Playlist order, A–Z and Rating.
  Press the sort button on the Movies or Series list to cycle to it. Newest titles come first, so a
  provider that keeps adding films puts the new ones where you'll actually see them.
- The date comes from your provider. **Xtream playlists** carry a real added/last-modified date for
  every movie and show, and that is what's used. **Stalker portals** carry one too, and OwnTV now reads
  it — though on a freshly built portal it is often the date the reseller bulk-imported the catalog
  rather than a real release date.
- **M3U playlists have no date to give** — the `#EXTINF` format simply has no such field. Rather than
  inventing one, "Date added" falls back to **reverse playlist order** for them: the titles at the
  bottom of your playlist, which is where providers append new ones, come first.
- Titles with no date always sort last, so a mixed catalog puts everything OwnTV knows a date for
  ahead of everything it doesn't, instead of scattering them.
- The sort is index-backed, so it stays instant on very large catalogs.
- Your choice is remembered per section and travels in backups, exactly like the other sort modes.

### ↕️ Series sorting — season and episode order, set per show

- The series episode view has a **"Sorting" button** that opens a small popup with two independent
  choices: **Seasons** and **Episodes**, each **Oldest first** or **Newest first**.
- **It's set per show, not globally.** A long-running series you're catching up on can stay oldest-first
  while a weekly show you follow shows the newest episode at the top — and neither affects the other.
- The two are genuinely independent: newest seasons first with oldest episodes first is a valid
  combination, and OwnTV keeps it.
- Changes apply the moment you pick them; **Back** closes the popup. Both default to **Oldest first**.
- **Playback order is never affected.** Autoplay still runs episodes in their natural order whatever
  you choose here — this only changes what you see.
- Opening a part-watched show still lands you on the last episode you watched, in either order.
- Your per-show choices are kept per profile and are included in backups with the manual-ordering
  section, so they come back on the right shows after a restore.

### 📺 Channel numbers — type a number on the remote to tune

- **Type a channel number while watching Live TV full screen** and OwnTV tunes straight to it, the way a
  set-top box does — no list, no guide, no holding CH+ through fifty channels. Works with the **number
  row and the numpad** on your remote.
- The number appears top-left as you type, with a **bar that drains over the two seconds** before it
  submits, so you always know how long you have to add another digit. Press **OK** to tune immediately,
  or **Back** to cancel. Five digits submit on their own.
- Once it resolves, the same card becomes the **channel OSD** — logo, name and number — and stays up
  until the new channel is actually on screen. If nothing matches you get **"Channel not found"** on that
  card with the number you entered.
- The number searched is your provider's own channel number, **from the playlist you're currently
  watching**. Only if that playlist has no channel with the number do your other active Live playlists
  get searched, so a number in your current playlist is never hijacked by another one.
- **Hidden channels and hidden categories are skipped**, and renamed channels show your name. If a
  playlist genuinely uses one number for several visible channels, the one from the list you opened wins;
  if that's still not decisive you get **"Multiple channels"** rather than a guess.
- **CH+/− keeps working right after a numeric jump**, even when you land far outside the list you opened.
- Channel numbers are now shown wherever you'd look for one, so you can learn the ones you use: the
  **Live TV channel list** and the **channel-list overlay** show the number in a fixed-width column ahead
  of the name — so every name still lines up whether the number is 7 or 101, and channels without a
  number keep their place in the column — the **full-screen top bar** shows it before the channel name,
  and the player's channel card shows **#number** under the name.
- Numeric tuning is only active on a live channel in full screen — during **catch-up or timeshift** the
  number keys are left alone.
- All of it is governed by one setting, **"Channel numbers"** (on by default) — in **Settings → Video
  Player → Live TV**, as a **quick-toggle chip** at the top of Settings, and in settings search. Off hides
  every number and ignores the number keys during playback; your playlist's numbers are untouched, so
  turning it back on restores them immediately.

### 📶 Prefer HLS for Live TV — per source, with format auto-detection (community PR #97 by @codeVerine)

- **Xtream sources have a new "Prefer HLS for Live TV" option.** Xtream panels can serve a live channel
  two ways: as a raw MPEG-TS stream, or as an HLS playlist. OwnTV asked for MPEG-TS; some panels are
  markedly more stable over HLS, and on those the choice is now yours.
- The option is on the **Add source** screen and in **Edit source**, so you can flip it on an existing
  playlist without re-adding it, and it is stored **per source** — a setup with two providers can prefer
  HLS on the one that needs it and leave the other alone.
- **Nothing changes unless you turn it on.** MPEG-TS stays the default, which is what the great majority
  of panels serve best.
- **The ⓘ stream info overlay now shows a `Format` row** — *HLS* or *MPEG-TS* — so you can confirm what
  you're actually receiving rather than guessing from behaviour.
- **Catch-up and timeshift follow the source's setting** too, rather than being pinned to one format.
- The setting is part of the source record, so it survives backup and restore and is applied in remote
  mode as well.

### 💾 A proper backup file — `.own`, with your wallpaper inside and real encryption

- Backups are now written as **`owntv-backup.own`** instead of a plain `owntv-backup.json`. It is one
  container holding the backup itself plus any files that belong with it.
- **Your background image travels with the backup.** The Liquid Glass wallpaper lives in OwnTV's own
  storage, and a backup only ever carried its file *path* — which means nothing on another TV, so the
  background silently came back blank after a restore. The picture's actual data now rides inside the
  `.own` file and is put back in place on restore. It's included whenever the **App settings** section
  is ticked.
- **A backup password now encrypts the whole file, not just the passwords in it.** Before, only the
  saved secrets (source & proxy passwords, TMDB key, OpenSubtitles login) were encrypted — your
  playlist URLs, usernames, profile names and watch history sat next to them in readable text that
  anyone opening the file could see. With a password, nothing in the file is readable without it, not
  even the list of what's inside.
  - Keep that password safe: **a `.own` backup encrypted with a password you've lost cannot be opened
    at all.** Without a password the file isn't encrypted and saved secrets are left out, exactly as
    before.
  - Because a protected backup can't be read until it's unlocked, restoring one asks for the password
    **first** and then shows what it contains. There is no "Skip" for these — there is nothing to
    restore without the password. Older encrypted `.json` backups are unchanged: sections first,
    password after, and Skip still restores everything except the saved passwords.
- **Old backups still restore, and always will.** Restore accepts both `.own` and any `owntv-backup.json`
  from an earlier version. The file is identified by its contents rather than its name, so a renamed
  file works too. Restoring an old `.json` no longer leaves a dead background-image path behind.
- Sending a backup between TVs over Wi‑Fi works exactly as before and transfers `.own` files, in both
  directions.
- Note for anyone downgrading: an older OwnTV build cannot read a `.own` file. Keep a `.json` backup if
  you plan to go back to an older version.

### 🗂️ Browsing & lists — decide what Live TV, Movies and Series come back to

- A new **Settings → Browsing & lists** popup with **six toggles**, two for each of Live TV, Movies and
  Series:
  - **Remember last category** *(on)* — reopening the section lands on the category you left instead of
    jumping back to *All*. Live TV has always worked this way; **Movies and Series now do too**.
  - **Remember last item** *(off)* — each category keeps **its own** scroll position instead of starting
    at the top. The Live TV toggle also restores the last focused channel when you re-enter Live TV.
- **Fixed: switching category kept the previous category's scroll position.** Picking a new category in
  Live TV, Movies or Series left the list wherever the last one had been scrolled to, so a fresh
  category could open halfway down. Every category now starts at the top by default, and only keeps its
  place if you turn "Remember last item" on for that section.
- The separate **App startup → Last channel** setting is untouched and independent of all six toggles.
  All six are included in backups.

### 🌍 Metadata language — descriptions and posters in your language

- **Settings → Metadata (TMDB) → Language** picks the language TMDB descriptions, titles and artwork
  come back in: **Default (English)**, **Device language**, or one of 40 languages (Greek, Arabic,
  Spanish, French, German, Hindi, Portuguese (BR/PT), Spanish (MX), Turkish, Vietnamese and more). The
  list is searchable.
- Changing the language **clears the cached metadata** so existing movies and series are re-fetched in
  the new language on next view. Title→TMDB matches are kept (they don't depend on language), so nothing
  has to be re-matched.
- Logo/artwork selection prefers your language, then English, then language-neutral art.
- Default is unchanged (English), so upgrading changes nothing until you pick a language.

### 🎞️ Auto frame rate — match the TV's refresh rate to the video

- **Settings → Video player → Auto frame rate** *(on)*: in full screen, OwnTV now asks the TV to switch
  to a refresh rate matching the video (24 / 25 / 30 / 50 / 60 fps) and hands the display back when you
  exit — so 24fps films and 25/50fps broadcasts stop juddering on a fixed 60Hz panel.
- **Fixed: auto frame rate did nothing on Android 10 and older devices** — including Fire TV Stick 4K /
  4K Max on Fire OS 7. OwnTV only used `Surface.setFrameRate()`, which doesn't exist before Android 11,
  so on those boxes there was no frame-rate matching at all, in **Live TV or VOD**. It now also requests
  the display mode at the window level, which works from Android 6 up.
- Applies to **both playback engines** (ExoPlayer and mpv) and to Live TV as well as movies and series.
  Only the full-screen player switches the display — the mini-player and the Live preview pane never do.
- Resolution is never changed: only the refresh rate varies, so a 4K output stays 4K. If the TV has no
  matching mode, or ignores the request, playback is unaffected.
- Turn it off if your TV or AV receiver re-handshakes HDMI noisily on every channel change.

### 🖼️ Guide channel logos — take logos from your XMLTV feed

- **Settings → EPG Sources → Add / Edit an EPG source → "Use this guide's channel logos"** *(off)*: that
  feed's own `<icon>` logos replace the ones your playlist supplies, in Live TV, the channel lists, the
  Guide, Search, Home and the player.
- It is set **per EPG source**, not app-wide, so one feed can supply logos while another only supplies
  programmes.
- Channels the feed has no logo for keep their playlist logo, so a partial guide never leaves blank tiles.
- Your provider's logos are never overwritten in the database — this is a display override. Turn it off
  and the playlist logos come straight back, and a catalog re-sync can't undo your choice.
- Logos are stored when the feed is parsed, so **re-sync the EPG source once** after switching it on.
- The setting is included in backups, alongside that source's Auto refresh choice.

### ↕️ Span move — reorder a whole block of categories at once

- **Settings → Customize Categories & Items** already let you long-press **Hide** to select a *span* of
  categories and hide them all together. The same span selection now works on the four **move** buttons
  (**⤒ ↑ ↓ ⤓**), so a block of categories can be reordered in one go instead of one row at a time.
- **How it works**: long-press any arrow on the first category to anchor the span, then press an arrow on
  the last category — every category in between moves as one block, keeping its internal order: **↑ / ↓**
  step it one place, **⤒ / ⤓** send it straight to the top or bottom of the list.
- The block **stays selected after the move**, so the arrows can be pressed repeatedly to walk it further
  up or down. **Back**, the banner's **Cancel**, or switching section clears the selection.
- Every category in the selected block is **tinted**, not just the anchor, so the span is visible at a
  glance; the banner shows how many categories are selected and what the arrows will do.
- A move that would run off either end is ignored, and while a *move* span is active the **Hide** button
  goes back to a plain single toggle — the two span modes never fight over the same press.
- Single-row **⤒ ↑ ↓ ⤓** behaviour is unchanged; internally a single move is now just a block of one.

### 📺 Live TV full screen — redesigned top bar, and a History channel list

- **The top bar is now one strip**: back · channel logo · quality/audio chips · channel name ·
  **Now / Next guide**. The floating channel card that repeated the channel name and the tall guide
  card pinned to the right edge are both gone, so the picture is far less covered.
- The **Now** line shows a thin accent progress bar and how many minutes are left, and refreshes on its
  own while you watch; **Next** sits beside it, dimmed.
- **Fixed: the ◀ channel list showed the wrong channels.** Pressing Left in full screen listed whatever
  rail you happened to launch from (History, or *All channels*), not the channel's own category. It now
  always lists the playing channel's category — with its name as the heading — no matter how you got
  there. Uncategorised channels fall back to *All Channels*.
- **New: press ▶ in full screen for a History channel list** — the last 30 channels you watched, with
  what's on now, so you can hop back to a recent channel without leaving full screen. Press ▶ again, or
  Back, to close it. Both lists respect hidden channels, hidden categories, renames and manual order.
- **Player controls tidied**: the redundant *exit full screen* button is gone (Back already does it),
  **stream info** moved to the far right with a clearer ⓘ icon, and the speed button shows just `1.0x`
  without the extra `»` glyph.
- The top and bottom control bars now sit on a **soft dark gradient**, so white icons and text stay
  readable over a bright scene.
- **Favorites now use a heart everywhere** — Live TV, Movies, Series, Search, posters and the player —
  instead of a star, which on a poster reads as a rating. The star is still used for ratings and for
  selection ticks.

### 🗂️ Category browser in the player — switch Live TV category without leaving full screen

- **Press ◀ a second time** inside the full-screen channel list and a **category browser** slides in over
  the picture, listing every Live TV category. Pick one with **OK** and the channel list reloads with that
  category's channels — the stream you're watching keeps playing throughout.
- The category you're currently in is **highlighted and focused first**, so a second Left followed by OK
  puts you back where you were. **Back** or **◀** returns to the channel list without changing anything.
- The list respects your customizations: **hidden categories are left out, renames are shown, and your
  manual order is kept** — the same categories you see in the Live TV rail.
- After you switch, **CH+/− follows the new category**, so channel surfing continues in whatever you just
  browsed to.
- Community contribution — PR #95 by @cotol1985.

### 📼 Catch-up from Live TV, and catch-up in the player of your choice

- **The Live TV catch-up picker now opens the same programme popup the Guide does.** Long-pressing a
  catch-up channel and picking a past programme used to start it immediately. It now opens the
  programme details — description, times, and the choice of **Watch from start**, **Watch channel**,
  favourite the channel, or close — exactly as the Guide has always done. The popup is drawn compact
  here, since it sits on top of the channel picker.
- **New: play a catch-up recording in an external player.** Archive recordings are the hardest streams
  for any in-app engine (providers serve them mid-GOP), so VLC or MX Player is now an option for them.
- **Settings → Playback → Catch-up** (renamed from *Catch-up time*) gained **Play catch-up in**:
  - **OwnTV player** *(default — unchanged behaviour)*
  - **External player** — every recording goes straight to VLC/MX Player
  - **Always ask** — pressing *Watch from start* asks which player to use, each time
- Sent to an external player, a recording loses the OwnTV HUD, resume position and the engine toggle.
- The timezone/offset controls are unchanged and still live in the same popup. The new setting is
  included in backups.

### ▶️ External player — Live TV support, and a default per section

- **Live TV can now be played in an external player.** Long-press any channel → **Play in external
  player**. It is always offered, whatever your default is, as the escape hatch for a channel neither
  in-app engine can open.
- **The single external-player switch is now three.** *Settings → Video Player Settings → External
  player* opens a popup with an independent **On/Off for Live TV, Movies and Series**, so you can send
  live channels to VLC while keeping movies in OwnTV (or the other way around). Downloads follow the
  Movies or Series setting depending on what was downloaded.
- Your existing setting is carried over to **Movies and Series**; **Live TV starts off**, so upgrading
  never silently starts throwing channels at another app. All three are included in backups.
- **Fixed: "no external player found" for live channels.** Live streams ending in `.ts` or `.m3u8` were
  offered to other apps under a MIME type VLC and MX Player don't advertise, so nothing matched even
  with both installed. OwnTV now widens the type until a player accepts it.

### 🛡️ Your library can no longer be wiped by a database problem

- **A schema problem no longer deletes everything.** OwnTV used to be built to drop every table and
  start empty if the database didn't look the way it expected — the failure mode behind the 4.1.0
  upgrade reports. That is gone. The database is now opened on a background thread before the UI, and
  if it can't be opened you get a **recovery screen** with **Try again** and a **Reset app data**
  button behind a second confirmation. Nothing is erased unless you ask for it.
- **A half-finished sync can no longer delete your catalog.** If a provider returns a short or broken
  response, OwnTV now refuses to remove titles when that would delete more than half of a source, and
  Stalker cross-checks the channel dump against the portal's own item count before removing anything.
- **Xtream panels that are too big to answer in one request** ("response too large") fall back to
  per-category loading, and now clean up correctly — only inside the categories that actually
  answered, so uncategorised titles are never dropped.
- **Provider reorders no longer rewrite your whole catalog.** A changed sort order is now detected on
  its own, so a 170,000-item library updates the rows that moved instead of all of them.
- **Backups are written safely.** A backup is written to a temporary file, flushed, and only then
  swapped in — with the previous copy kept as a fallback that is used automatically if the newest file
  is unreadable. An interrupted restore is no longer silent: OwnTV notices on the next start and tells
  you.
- **Manual ordering survives a re-sync.** Items you moved by hand in folders and Favorites are now
  included in the pre-sync snapshot, like favorites, history and resume positions already were.
- **A backup containing a source type this build doesn't know is skipped and reported**, instead of
  being silently imported as an M3U playlist.

### 📡 Live TV that recovers instead of giving up

- **A single hiccup no longer kills a live channel.** The "stop immediately" shortcut meant for VOD was
  firing on live streams too; live now goes through the full retry ladder.
- **Reconnect keeps trying.** The retry ladder is 1.5 / 3 / 6 / 10 / 15 s and then holds at 15 s
  instead of giving up after roughly half a minute, and it only resets once playback has actually held
  for a minute.
- **Outages recover by themselves.** If the network drops long enough for the channel to stop, OwnTV
  now resumes it as soon as the connection is back — no matter how long it was gone.
- **The error log stopped clearing itself** after an internal player reset, so playback problems can
  actually be diagnosed.
- **Raw MPEG-TS channels no longer drop the connection every 10–15 seconds.** A live MPEG-TS channel is
  one long-lived HTTP response, so it is the *gap* between reads that matters, not how much video is
  buffered — and OwnTV's buffer settings left that socket idle long enough for providers and middleboxes
  to cut it, producing a glitch every few seconds on channels that stream fine elsewhere. The live buffer
  now keeps the connection being read often enough to stay open, without holding more memory. HLS
  channels were never affected, because each segment is its own request.

### 🎬 Playback fixes

- **Audio-only content no longer shows a playback error.** Radio stations filed under Movies, music
  videos and audio-only catch-up used to fail after 8 seconds with a "video could not be rendered"
  message. Content that really does declare video and fails to show it still reports the error.
- **"Compatibility mode" and the player-engine choice now stick on Stalker portals.** Those pins were
  stored against the stream link, and Stalker issues a brand-new link every time you press play, so
  the pin never matched again. They are now stored against the item itself. Existing pins on
  Xtream/M3U are carried over automatically.
- **Short clips no longer count as "watched" at position 0**, and content with an unknown length is
  never marked finished.
- **4K playback surface handling** and the mpv→ExoPlayer handoff now follow the same timing rules as
  every other engine switch, which removes a class of black-screen-after-switch cases on Realtek boxes.
- **4K channels no longer fall back to "compatibility mode" when you tune from one to the next.** Moving
  between 4K live channels dropped the second one to mpv with a decoder error, even though the very same
  channel played perfectly on ExoPlayer if you pressed the engine toggle. Some TV chipsets — Realtek
  boxes in particular — accept only **one** 4K decoder per video surface: releasing the first channel's
  decoder leaves the surface unusable, so the next channel's decoder starts and then dies about a second
  later. Toggling to mpv and back happened to rebuild the surface, which is why that always "fixed" it.
  Leaving a 4K channel now rebuilds the surface along with the decoder, so the next 4K channel gets a
  clean one and plays on ExoPlayer directly. Waiting longer between channels never helped and this is not
  a delay — a failing tune and a working one were within 30 ms of each other. Channels below 4K are
  untouched and zap exactly as before.
- **Subtitle timing offset no longer freezes the UI** — the shifted subtitle file is generated in the
  background and cached.
- **Switching engine during a catch-up recording no longer jumps to the live programme.** The player
  now knows an archive recording is playing and reloads the same recording at the same position,
  instead of re-tuning the channel and dropping you onto whatever is on air now.
- **Fewer "failed on both engines" errors.** When mpv had to be torn down and playback handed to
  ExoPlayer, the handoff could grab a video surface that was already being replaced and die instantly
  on an item that played fine on the next try. The handoff now waits for the new surface.
- **The resolution badge no longer under-reports wide-format streams.** The stream-info overlay worked out
  the quality label from the picture **height** alone, so a channel broadcasting a wide 1920×800 picture was
  labelled from its short edge and read **720p** even though it's a 1080p-class stream. A cinema-format
  picture is only cropped top and bottom, so the **width** survives it: the label is now taken from whichever
  of the width and the height implies the higher class, and cinema-format and letterboxed channels report the
  quality they actually deliver. Both playback engines use the same rule, so a channel can no longer show one
  quality full screen and another in the preview pane, and anything below 480p still reports its **true**
  height rather than being rounded up.

### 📺 Live TV

- **Choosing ExoPlayer for a channel that had fallen back to mpv now sticks.** When a channel dropped to
  mpv automatically, pressing the player's engine toggle to go back to ExoPlayer re-started it on
  ExoPlayer — and the fallback watchdog, armed again by that restart, immediately sent it back to mpv, so
  the button looked like it did nothing. Picking ExoPlayer is now treated as a deliberate override: the
  automatic fallback stays out of the way for that channel until you tune elsewhere. If ExoPlayer really
  can't play it you stay there and can press the toggle again for mpv. Automatic tunes are unchanged —
  ExoPlayer first, mpv if it fails.
- **Live preview no longer plays sound on surround channels when preview audio is off.** Channels with
  5.1 audio (Dolby Digital / DTS) kept playing sound while browsing even with **Settings → Live TV →
  Preview audio** turned off. The preview was muted by volume alone, which has no effect on a surround
  bitstream passed straight through to the TV over HDMI — the TV received it at full level. The preview
  now switches the audio track off entirely while muted, so every channel stays quiet. Radio and other
  audio-only channels are unaffected and still play.
- **The Preview audio setting now applies to a preview that's already playing.** Turning it on or off
  took effect only on the next channel; it now changes the current preview immediately.
- **A live channel opened from the Guide now appears in History.** Tuning a channel from the guide grid
  (or *Watch channel* in a programme popup) is recorded straight away, rather than going through the
  delay that exists to keep rapid channel-surfing out of your history.
- **CH+ and D-pad Up now go to the next channel in full screen, not the previous one (#84).** Channel
  surfing ran backwards: CH+ moved *down* the list and CH− moved up, so on the first channel CH+ jumped
  to the very last one instead of to channel 2. Every surfing key now points the same way — **CH+,
  D-pad Up and Next all go to the next channel; CH−, D-pad Down and Previous go to the previous one** —
  matching how a set-top box and every other TV app behave. Wrapping around is unchanged and still
  intended: going down from the first channel lands on the last, and up from the last returns to the
  first.

### 📅 Guide & series

- **New episodes now appear in a series you already opened.** Episodes were cached once and never
  refreshed, so a show you had opened before would never gain a new episode. They now refresh every
  6 hours and after a sync — and merging keeps your watch progress and resume positions on the right
  episodes. A failed or empty fetch never empties a show that already has episodes.
- **A truncated guide download is no longer trusted for 24 hours.** The downloaded guide is only
  promoted to the cache once it has been fully parsed.
- **Channels added while a guide sync was running now get their programmes**, instead of staying empty
  until the next full guide download.
- **Guides emptied by the 4.1.x upgrade refill themselves** once, automatically, on the next start.
- **"Resync now" vs "Resync and remove missing titles"** — refreshing a source now asks which you want,
  so removing titles a provider has dropped is a deliberate choice. Neither option deletes your data.
- **The sync status pill** shows one line per running sync with real per-type counts, instead of
  collapsing everything into a single line.

### ⬇️ Downloads that survive the background

- **Downloads keep running when you leave the app.** Transfers moved to a proper foreground background
  worker with a notification, so Android no longer kills them the moment OwnTV goes to the background.
- **Pause and resume no longer re-download what you already had** — the real file length is saved when
  you pause, and a download interrupted by the app being killed resumes rather than restarting.
- **Pulling out the USB/SD card mid-download fails the download** instead of quietly continuing into
  internal storage.

### 🚀 Startup & speed

- **A branded splash instead of a blank window** on cold start, held until OwnTV actually knows which
  screen to show (with a 4 s safety limit).
- **Roughly 3× faster to a usable screen** on the developer's TV with a full catalog: shell 1879 →
  557 ms, Home data 3171 → 1096 ms, guide preload 3072 → 1782 ms.
- **A fresh install is no longer slow for the first few days.** Android normally leaves most of an
  app's code in a slow form and only speeds it up gradually as it learns what you use — which hits
  sideloaded apps like OwnTV hardest, since every new release starts that over. The APK now ships a
  recorded startup profile so the code that runs at launch is compiled ahead of time, from the very
  first launch after installing.
- **The database is no longer repaired on every single open** — it is checked first and only repaired
  when something is actually missing.
- **Settings reads no longer wake every screen** on each preference write, and around 95 background
  data streams now stop when nothing is watching them.
- **The image cache is sized to the free space available** (up to 250 MB, 5% of free space, never below
  32 MB) instead of a fixed guess.
- **Browsing storage folders and importing a background image no longer block the UI.**

### 🛠️ Setup & appearance

- **Fixed: Stalker portals with a "virtual" MAC were rejected at setup.** Some panels hand out MACs
  containing letters past F (for example `…:PQ`), which the app refused with *"Enter 12 hex digits"* —
  locking those users out entirely. The portal only ever echoes the MAC back to itself, so there was no
  protocol reason to insist on hexadecimal. Any 12 letters/digits are now accepted, in any of the usual
  separator styles, which still catches typos and truncated pastes.
- **Fixed: picking a new background image did nothing until the app was restarted.** In Liquid Glass
  mode, choosing a second image of the same file type reused the same filename, so nothing detected a
  change and the previous picture stayed on screen. Each pick now lands under its own name and appears
  immediately; the old file is still cleaned up, so only one background is ever kept.
- **Fixed: the + / − buttons became unreachable once a setting hit its maximum (#88).** In **Mini-player →
  Size**, setting the size to its 50% maximum left the picker with nothing selectable on the next visit —
  the D-pad did nothing and only Back got you out, so the size could never be turned back down with the
  remote. The dialog always tried to select the **+** button, which is disabled at the top of the range
  and so cannot be selected, and focus is deliberately kept inside the dialog. It now selects whichever
  button is usable, and hands over to the other one if the one you are on runs out of range mid-adjust.
  The same dialog is used by **A/V sync** and the **custom live buffer**, which were stuck the same way at
  their maximums.

### Internal

- **The bundled baseline profile was rebuilt from obfuscated names and did nothing.** Every release
  build reshuffles those names, so ~98% of the 7,238 recorded entries matched nothing in the shipped
  app — and `assembleStandardRelease` printed ~7,100 *"Startup class not found"* warnings because of it.
  The dead recording is removed. The baseline profiles that come from Compose, coroutines, lifecycle and
  Room are unaffected and still ship, so startup speed is unchanged. Recording our own again is blocked
  on an upstream fix; see `future-plan/baseline-profile-agp9-plan.md`.
- **Tests and lint now gate CI.** Pull requests run unit tests and Android Lint before anything is
  built, lint fails the build on an error (0 errors, from 122), and reports are uploaded on failure.
  APKs are still only built for `main` and tags.
- **New tests** for the database migration chain (every exported schema version migrates to the current
  one; the repair path restores every guaranteed index and search table), for backup merge/restore id
  remapping, and for the re-linking that keeps favorites, history and resume positions attached across
  a sync.
- **Release notes now come from `CHANGELOG_APP.md`, not `CHANGELOG.md`.** The tag build in
  `android.yml` was publishing the newest **full** `CHANGELOG.md` section as the GitHub release body —
  and GitHub's auto-generated commit list on top of it — which is what the in-app update dialog shows.
  It now extracts that tag's short, bullet-only section from `CHANGELOG_APP.md` and no longer appends
  the generated notes. `CHANGELOG.md` stays what it was meant to be: the detailed changelog developers
  and contributors read on GitHub by hand.
- `release-notes.yml` did already do this, but never ran for tag builds: a release published with
  `GITHUB_TOKEN` doesn't trigger other workflows. It remains as the fallback for releases published by
  hand from the GitHub UI. Both jobs now match the version header as a whole word, so a header without
  a trailing date (`## v4.1.5`) is found too.

## v4.1.4 — 2026-07-24

### 🧊 Liquid Glass — frosted translucent interface over your own background photo

- An opt-in **glass look**: content panels, sidebar, preview panes, dialogs, top bar, cards and the
  mini-player turn **translucent with a real frosted-blur backdrop** over an optional **background
  photo** — glassmorphism on TV. Everything lives in one **Settings → Glass Effect**
  dialog: Liquid glass On/Off, the background image, a **Transparency** stepper (20–95%), a
  **Blur / Frost** stepper (0–100%), a **Surfaces** sub-menu, and **Reset**.
- **Background image — Local or Remote.** **Local** picks a photo from USB/device storage (copied into
  app-private storage so unplugging the stick can't blank it). **Remote** sends one from your phone:
  the TV shows a **PIN + QR** (the same companion pairing as Remote Backup & Restore), the phone opens
  the page on the same Wi-Fi and uploads a JPG/PNG/WebP/BMP, and it applies instantly.
- **Per-surface control.** The **Surfaces** menu toggles glass individually for content panels,
  sidebar, preview panes, dialogs & popups, top bar, cards and the mini-player — or all at once with
  one master row. Turning every surface off turns glass off.
- The frost is a real blurred slice of the photo aligned behind each panel, pre-processed with a
  matching brightness scrim and a slight saturation lift so the glass blends with the scene instead of
  reading backlit. It is computed once per image at a downscaled size, so it stays cheap on low-end
  boxes. Backdrop blur needs Android 12+; older devices fall back to translucency without frost.
  All glass settings persist per install and are included in backups.
- **Every focused/selected control now frosts, not just the big panels.** The glass highlight was
  extended to the focus rim itself: cards, list rows and chips on every content screen, every row and
  list item inside popups/dialogs (pickers, storage browser, avatar/subtitle/backup/profile lists,
  EPG match review), the sidebar's profile/avatar buttons, the action pill buttons (Save / Cancel /
  Add / Edit / Delete / Done), and every search bar (category rail, Live/Movies/Series/Search/EPG
  panels, and the in‑popup search fields) — so a pill or search field inside a dialog frosts with the
  dialog and one on a panel frosts with the panel. The fullscreen player stays solid by design. Four
  dialogs that previously kept a flat fill (Video Player pickers, EPG "Fill from playlist", Live
  catch‑up, the Guide programme popup) now frost with the rest.
- **The player's Subtitles & Audio pickers are now glass too.** Their track rows and the A/V-sync
  buttons frost like the rest of the interface (they previously stayed flat inside the glass panel),
  and the popups were tightened to the compact style used by the storage picker — a narrower box with
  a smaller font.

### ⭐ Favorite from the player — add to Favorites without leaving the stream

- A **star button** in the fullscreen player's control bar favorites (or un-favorites) what you're
  watching **without backing out** to the list — a live channel, a movie, or a series (an episode
  favorites its parent show). The star fills when the item is already a favorite and updates
  instantly, and it survives channel zapping. (community suggestion)

### 🗂️ Per-section sync scope — choose Now / Later / Off for each section (#74)

- Every source now controls **Live**, **Movies** and **Series** independently with a **Now / Later /
  Off** scope instead of the old on/off sync toggles. Set a section to **Off** and it is never
  synced or shown (the long-requested "don't load VOD" — turn Movies off and the huge movie catalog
  is skipped entirely), **Later** keeps it available to sync on demand without running now, and
  **Now** syncs it with the rest. Editable per source in **Setup** and **Settings → Manage sources**,
  and from the LAN **companion** page (Now/Later/Off dropdowns replace the old checkboxes).
- A source with **every** section Off does no sync work at all. Changing a section's scope resyncs
  just that source. Backups carry the per-section scope forward, and upgrading preserves your existing
  behaviour (all sections default to **Now**). (community PR #78)

### 🎨 Accent color — full HSV picker with a live preview

- The accent dialog is rebuilt around a proper **color picker**: a **hue bar** and a large
  **saturation / brightness square**, each a D-pad "enter-to-edit" control — focus it, press **OK** to
  step inside (it glows amber), move with the D-pad, **OK/Back** to step out — plus a **live preview**
  circle and a trimmed set of **6 quick presets**. Type an exact **hex code** at the top and **Apply**,
  or dial one in and **Use this color**.
- **Custom hex accents now render exactly.** Entering a hex code used to pin its lightness and show a
  nearby shade; the seed color is now used verbatim as the accent (only the contrast roles are
  derived). The dialog uses the shared Lora popup styling.

### 🎧 Audio Mode — listen with the screen free

- A new third player mode, alongside fullscreen and the docked mini-player: **switch the current
  stream to audio-only** and keep browsing. Video decoding is stopped entirely (true audio-only, not a
  hidden video), and a compact **now-playing bar** appears in the top bar — an animated equaliser,
  the title, and transport controls (play/pause, previous, next, volume, fullscreen, close). The
  equaliser dances while sound plays and freezes flat when paused. Live shows a pulsing **LIVE**
  badge; movies/episodes show a slim progress line with remaining time.
- **Enter it** from the **headphones button** on the fullscreen player controls or on the docked
  mini-player. **Two-stage D-pad focus:** move onto the bar and it highlights as one target; press
  **OK** to step inside, where Left/Right move between the buttons and OK runs the focused one; focus
  stays locked in the bar and **Back** is the way out. **Fullscreen** returns to full video, **close**
  stops playback. Works for Live TV (both engines), movies and series.

### 📤 Remote Backup & Restore — move a backup between TVs over Wi-Fi

- **Remote restore.** **Settings → Backup & Restore → Restore from another device**, and the same
  option in the first-run / add-profile **setup wizard**, open the LAN companion server in
  backup-upload mode and show a **PIN, a QR code, and the URL**. A phone or laptop on the same Wi-Fi
  opens the page and uploads an OwnTV backup JSON straight to the TV; when it arrives it flows into
  the normal restore path (section picker, backup-password prompt). No cloud, no USB stick, no file
  browser on the TV.
- **Remote export.** **Settings → Backup & Restore → Send to another device** serves the exported
  backup from the TV so a remote device on the same Wi-Fi can **download** it — the mirror of remote
  restore for getting a backup *off* the TV.
- Both reuse the existing companion **PIN + QR pairing**, the profile / section pickers and the same
  encryption as local backups; the listener stops automatically when you leave the screen. Local
  backup/restore (USB, on-device file) is unchanged.

### 📡 Live TV latency control (#72)

- **Settings → Video Player → Live latency** trades how close to the live edge you play against
  stability: **Low latency**, **Balanced** (default), **Stable**, or a **Custom** buffer in seconds.
  It applies on the next channel open, to live streams only (VOD is never affected).
- Works on **both engines** — ExoPlayer live uses it as the HLS live-edge target offset, mpv live as
  the demuxer read-ahead. **Balanced applies no override at all**, so it can never regress a stream
  that already plays well. Picking **Low latency** (or a below-Balanced custom value) shows a quick
  heads-up that a smaller buffer can stutter on weaker connections.

### 🪟 Configurable mini-player

- The docked mini-player (live PiP) now has an adjustable **size** (percentage of screen width) and
  **screen position** (six docking spots — the four corners plus top/bottom centre), set in
  **Settings → Playback → Mini-player** and also changeable **on the fly** from the mini-player's own
  resize / move controls. The window is laid out proportionally (`fillMaxWidth% × 16:9`), so it scales
  consistently across TV sizes and the UI zoom instead of the old fixed box.

### 🖼️ Live TV preview pane — info-only, genre dots, EPG coverage

- **The preview pane is now informational only — the action buttons are gone.** Favorite / Rename /
  Hide / Match EPG / Catch-up all moved to the long-press channel menu (where Move and Remove-from-History
  already lived), so nothing in the pane is selectable or focusable any more. **Right-arrow no longer
  enters the pane** — D-pad focus stays in the channel list — which fixes the common complaint that a
  stray right press dropped you onto the buttons by accident. The pane instead shows a short note
  ("Press OK to watch fullscreen · Long-press for options").
- **Channel metadata row.** Under the channel name, a compact row of chips shows the channel's **real
  category** (resolved from its `categoryId`, so it's correct even when you're browsing via Favorites /
  History / All — never the browse context), its inferred **genre** with a colour dot, **catch-up**
  availability (with days, e.g. "Catch-up · 7d"), and **EPG coverage** ("EPG · Nd" from the stored guide
  span, or plain "EPG" / "No EPG"). Every channel gets a genre marker — unmatched categories fall back to
  a neutral grey **Other** dot rather than none.
- **Shared genre colour system.** The Guide's category→colour inference and the preview's genre dot now
  use one shared `ChannelGenre` helper (sport→green, news→red, movies→violet, kids→amber, music→blue,
  documentary→teal, other→grey), so the two surfaces agree. The chips use the Lora serif font and a
  uniform fixed height so long category names never make one chip taller than the others.

### 🔄 Sync reliability — completion notices, restore visibility, concurrent sync

- **Sync completion pill.** When a catalog sync finishes — success, failure, or cancel — the global
  status pill now shows the result for a few seconds ("Sync complete · Playlist · 3 categories added")
  instead of silently disappearing. Multiple back-to-back completions queue and display one after
  another. (community PR #73 by @pt5pnzghm6-sys)
- **Restoring a backup no longer hides all your channels.** A restored source starts with empty
  catalog tables, but its saved `lastSyncAt` timestamp made the first post-restore sync behave like a
  *re-sync* — and with "hide new categories on resync" on, every category looked "new" and got hidden,
  leaving the screen empty. Restored sources now take the fresh-install sync path, so your restored
  show/hide preferences are honored exactly as they were. (community PR #73)
- **Concurrent playlist syncs no longer corrupt each other — and still run in parallel.** Syncing
  two or more playlists at once (manual resync, startup auto-refresh) used to race on the shared
  SQLite tables: one source's index/FTS-trigger drop-and-restore cycled against another's concurrent
  writes, throwing `SQLiteDatabaseLockedException`s that truncated the second source's movies and
  skipped its series entirely — silently reported as success. PR #73 added a per-table index lock
  that fixed the "trigger already exists" crash; this release closes the remaining race at its
  source: a second sync arriving on a table in bulk-insert mode now *joins* that mode (writer-counted)
  instead of bypassing the lock, and the index restore waits for the last writer. Sources download,
  parse and insert fully in parallel — no app-wide queueing — with every playlist syncing to
  completion regardless of how many run at once. (community PR #73 by @pt5pnzghm6-sys)
- **Incremental M3U resync — no more clear-and-reimport.** M3U playlists used to be wiped and fully
  reinserted on every resync (playlists carry no provider item ids), which was slow on big playlists,
  briefly emptied the grids mid-sync, and re-created every row so favorites/history/manual order
  pointed at dead entries. Each M3U item now gets a stable synthesized key (name + group), and
  resyncs run the same hash-diffed upsert as Xtream/Stalker: unchanged items are skipped, changed
  items (including reordered playlists and series that gained/lost episodes) update in place keeping
  their identity, and removed items are pruned. **Favorites, watch history, playback progress and
  manual ordering on M3U content now survive resyncs.** The first resync after this update migrates
  old rows to stable keys once (that one resync still relinks like before; per-item hide/rename
  customizations on M3U sources reset once); every resync after that is incremental. A failed
  download or a playlist missing a content type still never wipes existing rows.

### 🐛 Fixes

- **Settings → About shows the updated Telegram group QR code.**
- **Editing a source no longer shows the other source types.** The Edit-source screen listed all the
  type chips (Xtream / M3U / Stalker) even though the type can't change while editing. It now shows
  only the chip matching the source you're editing.
- **Accent hex code field is no longer hidden behind the keyboard.** The hex input sits above the
  color picker so the on-screen keyboard can't cover it while you type a code.
- **Latency warning popup: focus returns to the Live latency row.** After picking **Low latency** (or
  a below-Balanced custom value) and dismissing the heads-up with "I understand", focus used to jump
  to the first row of Video Player settings ("Hardware decoding") instead of the row you were on. The
  picker→popup transition was clearing the pending return-focus target; it is now preserved through
  the popup so focus lands back on the Live latency row.
- **Live preview off: audio no longer keeps playing after you leave a channel.** With the in-pane
  Live preview turned off in Settings, exiting a full-screen live channel left the ExoPlayer engine
  decoding the stream's audio in the background (nothing re-took the engine to silence it, unlike when
  preview is on). Leaving full-screen now stops that engine when the preview is disabled.
- **4K live channels no longer lag/judder on mpv when a provider sends broken timestamps.** Some IPTV
  4K feeds send non-increasing / duplicate presentation timestamps; mpv is strict about PTS and was
  dropping nearly every frame (render output collapsing to ~8–12 of 30 fps) while decode itself was
  fine — so the channel looked laggy on mpv even though ExoPlayer played it cleanly. Live playback on
  mpv now derives timing from the container FPS (`correct-pts=no`), stops chasing the audio clock
  (`video-sync=desync`), and no longer drops frames (`framedrop=no`) — all **live-only**, so VOD keeps
  accurate PTS/seeking and normal frame-dropping. Confirmed on Realtek 4K hardware across 24/30/50/60 fps
  channels with zero frame drops.
- **Playlists & EPG Sources menus: focus now stays inside the list.** Entering either sub-menu used to
  land focus on the "Add" button instead of the list; after editing, re-syncing, or deleting a source,
  focus escaped the menu to the "Add" button. Both screens now track the row you acted on (per-row
  `FocusRequester`) and restore focus to that same row on edit/re-sync, move it to the nearest surviving
  neighbour on delete, and fall inside the list on entry.
- **Settings dialogs no longer let D-pad escape behind the scrim.** Every scrim dialog in Settings
  (Zoom, Accent, Theme, About, Playback error log, Clear history, Catch-up time, plus the Backup,
  Video Player picker/stepper, Customize and shared Number/Picker dialogs) was missing the focus trap,
  so a D-pad press toward the edge could land on the settings rows behind the dialog. All now use
  `trapAllFocusExit` like the rest of the app.
- **No more "scroll animates from the top" when closing a Settings / Video Player dialog.** Opening a
  scrim dialog over a scrollable settings list reset the list's scroll to the top, so closing it made
  the list visibly scroll back down to the row you came from. The scroll position is now snapshotted
  when you tap a row and restored instantly on dialog close, so the list stays exactly where it was.
- **Settings dialog-close focus return hardened.** The `dialogReturn` target (which row to refocus when
  a dialog closes) was being cleared in the wrong place, so it leaked and could misroute the next
  directional entry; it is now cleared in the restore effect itself. The entry fallback is also
  search-aware (uses the always-bound search field while searching, instead of an unbound row).
- **OpenSubtitles, Network & Metadata settings: focus no longer escapes on entry / state changes.**
  These three screens had no focus-group safety net, so entry focus could fall to the sidebar. The
  OpenSubtitles screen also stole focus back to the first row on every server state change (e.g. after
  pressing Refresh) and never restored focus when returning from the Delete-subtitles screen with no
  state change — all fixed.
- **Profiles, Mini-player, Customize, CH+- paging, Weather: focus returns to the row that opened a
  dialog.** Closing a dialog in these sub-menus used to send focus to the screen's first row. Each now
  tracks its opener row and restores focus there; the CH+ / CH− skip rows also got their own
  `FocusRequester`s (they had none).
- **Long-press context menus in Movies / Series / Live / Guide no longer let D-pad escape behind them.**
  The long-press menus used the OK-key guard but not the focus trap; D-pad could now escape behind the
  scrim. All now trap focus inside.
- **Downloads: focus moves to the next download when you delete one.** Deleting a download used to let
  focus escape to the sidebar; it now moves to the nearest surviving download row (same slot, else the
  last row).
- **Home & Customize category lists trap vertical focus.** A held D-pad Up/Down that outran the lazy
  composition could escape the list to the sidebar; both now use `trapVerticalFocusExit` like every
  other browse list.
- **Category rail: abbreviation badges removed.** Next to each category name the rail showed a short
  2–3 letter code derived from the name (e.g. `UPR` beside "UK PRIME RAW") in a fixed-width column.
  This was left over from the old compact-pill rail and read as clutter on what was otherwise a
  full-label column. Category folders now show just the name; **Favorites** and **History** keep their
  star / clock icons inline before the name. The content-pane subtitle also shows the full category
  name instead of the abbreviation. (#75)
- **Subtitle search overlay: sign-in moved to Settings only, local-file button removed.** Opening
  **Search OpenSubtitles** while signed out (or after the session expired) used to offer three buttons
  — add account / select local file / skip — plus an in-place username+password sign-in popup. Sign-in
  now lives only in **Settings → Video Player → Subtitles → OpenSubtitles** (which already had it):
  the signed-out overlay shows a clear note pointing there with just a **Close** button, and the
  in-overlay sign-in and sign-in-failed dialogs are gone. The overlay's **Select local file** button
  was removed too — the dedicated **Select local subtitle file** row in the Subtitles menu (right
  below **Search OpenSubtitles**) already covers local subs.
- **Catch-up dialog: D-pad focus no longer escapes the popup.** Opening the catch-up programme picker
  (long-press a channel → Catch-up) left the dialog without a hard focus boundary, so a stray D-pad
  press — or the Live screen's own focus restoration — could drop focus onto the channel grid behind
  the scrim. The dialog now wraps in `Popup(focusable = true)` and traps focus exit, matching the other
  scrim dialogs. It also picks up the standard popup-menu styling: the **Lora** serif font at 75% scale
  and a denser box, so it reads like the EPG-match and other popups.
- **Category rail highlight: sharper corners.** The focused / selected box on the category rail (used
  by Live TV, Series and Movies — one shared component) had a soft `14dp` corner radius that read as
  nearly pill-like; it's now `8dp`, crisper and closer to the channel-list item style next to it.

## v4.1.3 — 2026-07-19

### 💬 External subtitles — OpenSubtitles search & local subtitle files

- **Search OpenSubtitles from the player.** For any movie or series episode, open **Subtitles →
  ADD SUBTITLES → Search OpenSubtitles**. The search is pre-filled from the item's identity (TMDB id
  when available, else title/year and season/episode), shows language, release name, Trusted/SDH/AI
  tags and download counts, and supports **Edit search** and **All languages**. Picking a result
  downloads the subtitle, attaches it live without interrupting playback, and remembers it for that
  profile and title. Never automatic: OwnTV only searches or downloads when you ask.
- **OpenSubtitles account, per profile.** Sign in from **Settings → Video Player → Subtitles →
  OpenSubtitles** (free account at opensubtitles.com), with an optional **Stay signed in**.
  Each OwnTV profile connects its own account; the allowance display shows the provider's own
  remaining-downloads and reset values. Credentials sit in Android-Keystore-sealed storage, are wiped
  on sign-out/profile deletion, and are never logged. They enter a backup only when you set a backup
  password — encrypted per profile, and omitted entirely from a password-less backup. If you pick Search OpenSubtitles
  while signed out, a friendly dialog lets you **add the account right there** (or jump to a local
  file instead).
- **Local subtitle files — no account, no internet.** **ADD SUBTITLES → Select local subtitle file**
  opens OwnTV's TV-safe file browser for `.srt` / `.ass` / `.ssa` / `.vtt` / `.webvtt` files (USB or
  internal storage). Non-UTF-8 files (Windows-1256 Arabic, Windows-1252, ISO-8859…) are detected and
  converted automatically so they render correctly, and OwnTV keeps a managed copy so the subtitle
  keeps working after the USB stick is gone.
- **Subtitle timing.** **Subtitles → ADJUST → Subtitle timing** nudges the active subtitle in
  ±0.1 s / ±0.5 s steps while the video keeps playing, with plain-language direction (earlier/later).
  The offset is remembered per profile, per title, **per exact subtitle release** — a WEB-DL sub and
  a Blu-ray sub keep separate offsets, and switching subs never inherits another's offset.
- **Smart caching, quota-friendly.** Downloads are cached on the device and deduped: re-picking a
  subtitle any profile already downloaded re-uses the file and **spends no download quota**. On
  replay, a title's previously downloaded subtitles are re-listed in the Subtitles menu ready to pick.
  Everything works across both playback engines, including the in-player MPV/EXO toggle, and for
  **OwnTV Downloads** — offline, with the OpenSubtitles moviehash silently sharpening online matches
  for downloaded files.
- **Manage & delete.** **Settings → OpenSubtitles → Delete subtitles** lists every downloaded
  subtitle by Movies/Series with per-item and bulk delete; long-press a movie or episode for
  **Delete OpenSub subtitles**. Deletion is per profile — a subtitle another profile also downloaded
  stays available for them.
- *Privacy:* the OpenSubtitles API key lives only in an OwnTV-run Cloudflare Worker (like the TMDB
  proxy) — never in the app; only subtitle-search data is ever sent (no stream URLs or IPTV
  credentials). This product uses the OpenSubtitles API but is not endorsed or certified by
  OpenSubtitles.

### 👥 Profile-based backups (merge restore, PIN-protected)

- **Backup export now starts with a profile picker.** Every backup is per-profile: choose which
  profiles ride in the file (none pre-ticked — you decide), then pick the data sections as before
  (the old "Profiles & sources" section is now just "Sources"). Only the selected profiles' data —
  favorites, history, resume positions, customizations, startup modes, Customize PINs — and only the
  sources they actually use are written.
- **Locked profiles need their PIN.** Ticking a PIN-locked profile that isn't the one you're signed
  into prompts for that profile's PIN; a wrong PIN shows "PIN incorrect" and the profile stays out of
  the backup. Your current profile never re-asks (you already passed its gate). Profile PINs
  themselves are stored in the file only as salted hashes, never as the actual PIN.
- **Restore now MERGES — it never deletes existing profiles or sources.** Profiles are matched by
  name: a profile already on the device is updated from the backup, and profiles only in the backup
  are added — your other profiles are left completely untouched. Sources match by address, so a
  shared playlist isn't duplicated. (Previously a restore replaced everything.)
- **Profile names are now unique.** Creating or renaming a profile to a name that already exists is
  blocked with "This name is already taken" — names are how restore recognises the same profile.
- **OpenSubtitles logins now ride in encrypted backups.** With a backup password, each ticked
  profile's OpenSubtitles sign-in (username + password/token) is included, sealed with your passphrase,
  and restored to the matching profile on the target device. Without a backup password it's omitted,
  exactly like source passwords, the Stalker MAC and the proxy/TMDB secrets.

### 📱 Add a playlist from your phone (Remote setup)

- **"Add source" now starts with a Remote / Manual choice.** Pick **Manual** to type Xtream / M3U /
  Stalker details with the remote as before, or **Remote** to fill everything on your phone. Both the
  first-run setup wizard and Settings → Manage sources offer the choice.
- **Remote setup shows a QR code, a URL, and a one-time PIN.** Open the server on the TV, then on a
  phone or laptop on the same Wi-Fi scan the QR (or type the URL). The page first asks for the 6-digit
  PIN shown on the TV, then shows an **OwnTV-styled form** with Xtream / M3U / Stalker tabs. Fill it,
  tap **Send to TV**, and the details appear in the Add Source screen on the TV — you press **Start
  Import** with the remote (the phone never starts the import itself).
- **Secure by design.** The QR carries only the URL, never the PIN; every submission must carry the
  PIN or it's rejected (401). A fresh PIN is generated each time the server opens, passwords/MAC are
  never logged, and the listener stops automatically when you leave the screen.
- *Core idea from **@zarga03** (PR #66)* — reimplemented and hardened for OwnTV: added M3U support and
  the phone-side type picker, the one-time PIN gate, the QR onboarding, an app-matching web form, and
  "fill the form, you press Start Import" semantics.

### ⏱️ EPG sync: Run in background (onboarding)

- **The "Sync the TV guide now?" step during first-run setup can now Run in background.** Once the
  guide starts downloading you no longer have to wait on the sync screen — press **Run in background**
  to enter the app while the guide keeps downloading (matching the playlist import's own background
  option).

### 🗂️ Categories grouped by provider + new-category control

- **Multi-provider category lists no longer interleave.** When you view **All playlists** (or a profile
  with two or more linked sources), the category lists across Live/Movies/Series browse, Customize, EPG,
  Search and Home now stay **grouped by provider** (in the order you added them) instead of mixing two
  providers' categories together. A single selected playlist looks exactly as before.
- **Provider name on Customize rows.** When more than one source is in scope, each Customize category row
  shows which provider it belongs to, so bulk-hiding across providers is easier to follow.
- **"New category behavior" (Show / Hide) — per profile.** A new setting at the top of **Settings →
  Customize** decides what happens to a category the provider adds on a later re-sync: **Show** (default,
  the old behavior) or **Hide** it automatically. Useful if you keep only a few categories visible and
  don't want new ones appearing. It rides in the Customize backup/restore like other per-profile settings.
- **Re-sync tells you the category churn.** The sync-complete message now shows "N categories added,
  M removed" when a re-sync changes them — so you still know new categories exist even when you hide them
  by default. (Never shown on a source's first sync, where everything is new.)
- *Community PR #70 by **@pt5pnzghm6-sys** (related to issue #60).*

### 🎨 Smaller tweaks

- **Default UI zoom is now 90%** (was 100%) so more of each screen fits on smaller TVs out of the box;
  adjustable any time in Settings.
- **The Player settings "OpenSubtitles account" row is now just "OpenSubtitles"** — it holds sign-in
  *and* the downloaded-subtitle manager, so the shorter name fits what's inside.

### 📺 Live TV — current programme in the channel list

- **Now-playing subtitle on every channel row.** The Live TV channel list now shows the programme
  currently airing under each channel name (a small second line), sourced from your guide data. The
  channel-list column is also **a little wider** so the longer rows breathe, and the preview pane a
  little narrower to match. Channels without guide data look exactly as before — single line.
- **Same in the in-player channel overlay.** Pressing **Left** (while the player controls are hidden)
  to open the side channel list now shows the same current-programme subtitle under each channel, so
  you can see what's on without leaving fullscreen.
- *Detail:* the list uses the stored bulk guide only (one batched query, refreshed every 60 s); the
  focused-channel preview pane keeps its full provider short-EPG fallback. No per-row network calls.

### 🔄 EPG / Guide sync status pill

- **Updating the guide now shows the status pill too.** The small semi-transparent pill that already
  reports background playlist syncs now also reflects **EPG/Guide downloads** — manual resyncs from
  Settings → EPG Sources and the automatic startup/staleness refreshes. It reads "Updating guide ·
  *source* · N programmes" and disappears when the sync finishes. Catalog syncs keep priority; if both
  run at once the pill notes "· EPG too".

### 🎯 Smarter EPG matching

- **Match EPG picker suggests related channels first.** Long-press a channel → **Match EPG** (Live TV
  or Guide) no longer opens on a plain A-Z list: guide channels **similar to the channel's name float
  to the top**, best match first (e.g. opening it on "MTV FR" shows the MTV entries immediately). The
  ranking also applies while you type a search. The picker now scans the *whole* guide-channel set
  instead of only the first 300 alphabetical entries.
- **The name matcher itself is more robust** (used by the picker ranking, the Guide's **Auto-match
  all**, and single-channel auto-match):
  - Spelled-out **country names** match their codes — "MTV France" ↔ "FR| MTV" is now an exact match
    (guarded so channels like **France 24 / France 2** keep their name).
  - **Number words** — "BBC One" ↔ "BBC 1" now match.
  - **Word-order tolerance** — "France MTV" ↔ "MTV France" score highly via token overlap.
  - **Channel-number guard** — "Sky Sports 2" can no longer match "Sky Sports 3" (never even offered),
    and "MTV" vs "MTV 2" is capped below auto-apply so it goes to review instead of silently applying.
- **Dialog ergonomics on TV remotes.** In the Match EPG picker and the Auto-match **review** popup,
  the action buttons (**Close / Clear match**, **Accept all / Skip all / Done**) moved to a **right-hand
  column** — press **Right** from any list row to reach them, no more scrolling to the bottom of a long
  list. Focus is also **contained inside the popup** now (a stray D-pad press can no longer drop focus
  onto the screen behind it).

### 🎬 Better TMDB title cleaning

- The movie/series **title normalizer** (what builds the TMDB search query) strips more provider noise
  while keeping real titles intact: audio/language tags (**VOSTFR, VF, SUBBED/DUBBED, DUBLADO/LEGENDADO,
  TRUEFRENCH, LAT**), release markers (**HDCAM, CAMRIP, HDTC, HDLight, 10bit, 60fps, AAC/AC3/DTS, 5.1/7.1**),
  trailing **season/episode tails** on series names ("Show S05", "Loki Season 2", "Dark Staffel 1",
  "Temporada 3", "S02E04"), and trailing uppercase language codes ("Movie FR"). Guarded so titles like
  *Ocean's 8*, *Se7en*, *Area 51* and *Sub Rosa* are never touched.

### 🗂️ Storage access that works on more TVs

- **One-click "Grant full storage access."** The file/folder picker (download folder, local M3U
  import, backup) now has a single grant action that opens **OwnTV's own app-settings page**, where
  you enable **Allow management of all files** yourself. This fixes OEM TVs (e.g. TCL Android 12)
  whose system "All files access" screen is hijacked or missing, which previously left no working
  way to grant storage from inside the app. On Android 10 and below the standard permission dialog
  appears instead (it grants full access there). A media-only grant is no longer treated as storage
  access — it hid `.m3u`/backup files behind scoped storage.
- **The picker is a real dialog window now.** D-pad focus physically can't escape onto the screen
  behind it anymore, and access is re-checked when you come back from system settings, so the grant
  row disappears immediately after granting.

### 🎨 Compact popup menus in a new serif font

- **Popup menus are ~40% smaller and render in Lora** (a free, open-licensed serif; only popups —
  the rest of the app keeps its sans-serif): the player's **subtitle/audio/track menus**, Settings
  **option pickers** and **+/− steppers**, the **playlist switcher**, and the **storage/file
  picker** (now 300 dp with restacked footer buttons).
- **Match EPG picker** (Live TV & Guide long-press) shrank 40%, the Guide's **Review EPG matches**
  popup 20%, and the **Customize screen's PIN dialogs** got a compact variant — all in the Lora
  serif. The profile "Who's watching?" PIN dialog is unchanged.

### 🔀 CH+- key paging for browse panels

- **Page the category & item lists with the remote's CH+ / CH− keys.** In Live TV, Movies and Series,
  the CH+ / CH− keys now page whichever panel currently has focus — the category rail or the item
  list/grid. Short press jumps a configurable number of items (clamped at the ends, so a short list
  reaches the end in one press for free); long-press CH+ jumps straight to the **first** item and
  long-press CH− to the **last**. A lifesaver for big libraries (e.g. 50k live channels, 500+
  categories) where scrolling top-to-bottom was impractical.
- **Per-direction skip counts, typed or stepped.** New **Settings → Content → CH+- Key Paging**: a
  master on/off, plus a separate skip count for CH+ and CH− that you can type directly or nudge with
  − / +. The dialog warns (advisory, never blocking) when a count exceeds 50, since large skips
  overshoot short lists and may feel jumpy on low-end TVs; a hard cap of 1000 guards against typos.
- **Apply to the focused panel only.** The keys never fire when focus is elsewhere (e.g. the top bar),
  and a master toggle lets users whose remotes map CH keys to something else opt out entirely. The
  category rail moves focus only — selection still happens on OK, so a stray CH press never reloads
  a category's channels. All jumps use instant `scrollToItem` (no animation) to avoid jank on slow
  TVs over big distances. Defaults: enabled, skip 10 each direction.
- **Long-press is disabled on the "All" list.** On the built-in All channels / All movies / All series
  list a long-press jump to the very last item (e.g. the 170,000th movie) is pointless and janks, so
  long-press does nothing there — short-press skipping still works normally. Real categories and
  folders keep long-press jump-to-first/last. (This checks the built-in All key, not the name, so a
  provider category literally called "All Hindi" is unaffected.)
- **Also pages the Customize category list.** The same CH+ / CH− paging now works in **Settings →
  Customize Categories & Items**, where the list is just the raw provider folders (no "All"). Handy
  with big provider category lists; long-press jumps to the first/last folder. The keys move focus
  within the list only — they can never push focus out of it — and the CH+- Key Paging settings screen
  notes this coverage.

### 🐛 Fixes

- **"Grant full storage access" no longer dead-ends on OEM TVs.** On TCL Android 12 the old grant
  button opened the OEM "Permission Shield" screen, which has no storage entry at all; the picker
  also showed a "grant" option that could only ever yield a useless media-only permission. Both
  replaced by the app-settings route above.
- **Storage picker focus could escape the popup.** Moving focus (especially after returning from
  the permission screen) could land on the screen behind the picker; it's now hosted in its own
  window so that can't happen.
- **Deleting an EPG source now shows a "Deleting…" status and can't leave orphaned guide data.**
  Removing an EPG source with a large guide (100k+ programmes) took a while to clear from the
  database, but the row vanished instantly with no indication, and leaving the screen mid-delete
  could orphan those programmes with no source left to clean them up. The row now stays with a
  **Deleting…** badge (its actions hidden) until the delete finishes, the guide rows are removed
  **before** the source leaves the list, and the delete completes even if you navigate away.
- **EPG match now falls back to a network re-sync when the cache has no data for it.** After
  matching a channel, OwnTV fills its programmes from the cached XMLTV without a network call — but
  that step reported success even when the cache held none of the matched channel's programmes, so
  the network fallback never ran. It now re-syncs (with the just-saved match included in the sync
  filter) whenever the cache yields nothing for the matched channel. And when a matched channel
  genuinely has no current/upcoming programmes in the feed, the Guide now says so ("Matched — but
  this guide channel has no current programmes in the EPG feed yet") instead of leaving a silently
  empty row.
- **Match EPG from Live TV now takes effect immediately.** Matching a channel's EPG from the Live TV
  list used to leave the details/preview pane without guide data until an app restart (the row's
  now-playing line updated, the pane didn't). The match now also tops up the matched guide channel's
  programmes from the cached EPG and refreshes the pane right away.
- **Focus returns to the channel after Match EPG.** Closing the Match EPG dialog (pick, clear or
  back) lands D-pad focus back on the channel row it was opened for, instead of falling to the nav panel.
- **Customize screen showed categories from every playlist.** When you'd picked one playlist (e.g.
  playlist A) via the top-bar switcher, **Settings → Customize Categories & Items** still listed
  categories from *all* playlists. It now respects the selected playlist — same as the Live TV / Movies
  / Series rails. ("All playlists" still shows the merged set.) Existing reorders/hides are preserved.
- **Customize screen renamed** to **"Customize Categories & Items"** (was "Customize & Hidden Items")
  for clarity — it's where you hide/unhide items, rename, and reorder categories.
- **Live TV "Now" no longer shows a future programme** (#68). For channels without a configured guide,
  OwnTV falls back to the provider's short-EPG. When that data had a gap around the current moment, the
  "Now" slot could pick the next upcoming programme and mislabel it as live. It now correctly leaves
  "Now" blank on a genuine gap; the upcoming programme still shows under "Next". EPG display only — no
  playback impact.
- **CH+- skip dialog alignment.** In the CH+- Key Paging skip-count popup, the − / + buttons no longer
  sit above the number field — they now line up with it (the field's label was pushing them up).

### 🔒 Security (community PR #65)

- **Customize PIN no longer stored in plaintext** (community PR #65 by @aravindtri). The screen lock
  PIN is now stored as a salted SHA-256 hash, matching how profile PINs are already handled. Existing
  installs and imported backups with old plaintext PINs still verify correctly and migrate on use.
- **Hero preview URLs are redacted in error logs.** A failed Home hero-preview playback no longer logs
  the raw stream URL (which can carry credentials); it's scrubbed via the existing `redactUrl` helper.

### 📊 Player diagnostics — measured fps/bitrate & top-bar bitrate chip (community PR #67)

- **ExoPlayer now shows real fps, bitrate and dropped-frame stats** (community PR #67 by
  @pt5pnzghm6-sys). Raw MPEG-TS streams (most Xtream live TV) don't declare `frameRate` or `bitrate`,
  so ExoPlayer's **Stream Info overlay** and the preview's top-left chips used to be blank where mpv
  showed live values. This measures them on the fly — **fps** from decoder-rendered frame timing
  (snapped to a standard rate so a brief stall doesn't give a stray reading), **bitrate** from actual
  network bytes, and **dropped frames since the start of playback** — all with negligible CPU impact,
  and only computed while the info overlay is open. It also fixes a couple of mpv↔ExoPlayer handoff
  bugs that were blocking correct resolution/fps display for VOD on Exo.
- **Bitrate now appears in the player top-bar chips** for all playback — Live TV (preview & full),
  movies and series, on both engines. The chip uses the stream's declared bitrate (free to read), so
  it adds no measurement overhead; raw live MPEG-TS streams that don't declare one stay blank in the
  chip (the overlay still shows the live measurement when opened).
- **New "Measured stream stats" toggle** (**Settings → Video Player → Diagnostics**, on by default) —
  a one-switch escape hatch. On, the Stream Info overlay measures fps/bitrate/dropped frames as above.
  Off, no live measuring runs at all (declared values only), for the rare low-end TV where the
  measuring is ever suspected of causing stutter. It only gates the diagnostic numbers — never the
  actual video pipeline or the mpv↔ExoPlayer handoff.

### 📦 Packaging

- **Smaller downloads — split ABI builds.** Releases now ship a single **arm APK** (`OwnTV.apk` /
  `OwnTV-vX.X.X.apk`, `arm64-v8a` + `armeabi-v7a` — for all real Fire TV / Android TV devices, and what
  the Downloader code fetches) plus a separate **`OwnTV-x86_64-vX.X.X.apk`** (for emulators / rare Intel
  boxes). The main download roughly **halves in size** (~104 MB → ~49 MB), which fixes the "parse error
  on install" reports caused by truncated large downloads on Fire TV's Downloader app. `x86` (32-bit
  Intel) is dropped — even emulators use x86_64.
- **In-app updater picks the APK matching your device.** With releases now carrying one APK per ABI,
  the updater selects the asset matching the device's ABI (arm on real TVs, x86_64 on emulators)
  instead of blindly taking the first APK — so an arm TV can never download the emulator build, and
  in-app updates now also work on an x86_64 emulator. Older single-APK releases still update fine.

## v4.1.2 — 2026-07-14

### ⚡ Background catalog sync

- **"Run in background" during the first import** — the setup wizard's sync screen (and any first
  playlist import) now has a **Run in background** button: enter the app immediately and start
  watching while the catalog keeps loading. Works for M3U, Xtream and Stalker alike. If a
  backgrounded import later fails, the playlist is **kept** (with its credentials) so you can
  re-sync it from Settings → Playlists — previously a failed add was silently removed.
- **Stalker portals sync live TV first, movies & series in the background — automatically.**
  Stalker has no bulk VOD endpoint (its catalog is paged ~14 items per request), so a big portal's
  movies/series crawl took minutes. Adding a portal now imports live channels in seconds and hands
  the movies/series crawl to a background worker that survives app restarts and retries transient
  failures. No toggles to understand — it's the default for every Stalker add.
- **Adaptive portal speed** — the Stalker VOD/series crawl now learns how many parallel requests a
  portal tolerates (ramping up on success, backing off instantly on rate-limit/overload errors)
  instead of using a fixed pool. Tolerant portals sync significantly faster; strict portals stop
  erroring pages.
- **Faster Stalker re-syncs (delta check)** — on a refresh, a category whose item count is
  unchanged on the portal is skipped entirely instead of re-walking all its pages. On a stable
  catalog this cuts a re-sync from thousands of requests to roughly one per category.
- **Background-sync status pill** — a small semi-transparent pill at the bottom of the screen shows
  "Syncing *playlist* · N items" whenever any catalog sync runs in the background (a backgrounded
  first import, the movies/series remainder, auto refresh). It never takes D-pad focus and hides
  during fullscreen playback.
- **Clearer "All set!" message for staged imports** — when movies/series are still loading in the
  background, the import-success screen now says so explicitly (and points at the status pill), so
  a fresh Stalker add no longer looks like it "only synced live TV".

### 📺 Live player guide card

- **Before / Now playing / Next on the player controls** — bringing up the controls on a live
  channel now shows a guide card on the right edge: the programme that just ended, what's on now
  (with times and a short description), and what's next. Uses your XMLTV guide first, falling back
  to the provider's short-EPG API (Xtream and Stalker); channels without guide data simply show no
  card. Informational only — it never takes D-pad focus.

### 🐛 Fixes

- **Guide programme popup: last button was cut off** — with catch-up channels the four actions
  (Watch from start / Watch channel / Favourite / Close) overflowed the dialog edge. The buttons
  now wrap to a second row when they don't fit.
- **Guide programme popup: long-press acted in one go** — opening a programme with a held OK could
  instantly trigger the focused button. The dialog now swallows OK until the key is released once,
  so a long-press only opens it and the next press selects.
- **Settings → Home screen: focus didn't enter the list** — opening Home screen settings left
  D-pad focus on the sidebar instead of the first row (the only sub-settings screen missing the
  initial focus request; all others were audited and are correct).

## v4.1.1 — 2026-07-14

### 📡 Stalker / Ministra portal support

- **New source type: Stalker (MAC portal)** — add a portal with just a Portal URL + MAC address
  (no username/password). Third source type alongside M3U and Xtream, with the same Default-playlist
  toggle, playlist switcher, per-source Auto refresh, Backup & Restore (the MAC is encrypted like a
  password), and TMDB enrichment. Available everywhere sources are added — including the
  **first-run setup wizard**, so a portal + MAC is enough to onboard.
- **Test connection before saving** (handshake + profile check), with clear errors for a bad MAC,
  an unreachable portal, or clock drift ("check the TV's date & time"). When the portal reports a
  subscription expiry, the result shows it.
- **MAG User-Agent presets** (MAG250/254/270/420) in the add-source form, plus the per-source
  User-Agent override, so a portal's UA whitelist change never needs an app update.
- **Live TV** from a Stalker portal plays on both engines (ExoPlayer preview/fullscreen + mpv
  compatibility mode), with embedded subtitles, zap, and the engine toggle. Stream URLs are minted
  at play time and **silently re-resolved if they expire mid-session** (Stalker links are
  short-lived) — a long live watch survives a ~2–4 h token reset.
- **Movies & Series** sync and play: per-category catalog import with a shared concurrency budget
  (movies + series import simultaneously, with a bulk single-dump fast path where the portal
  supports it), lazy per-season episode loading, and next/previous/autoplay across a season — each
  episode mints its own fresh stream link. External player playback works too.
- **Downloads** work for Stalker movies and episodes like any other source: the link is resolved
  when the download starts, and if it expires mid-download the app fetches a fresh one and resumes
  from where it stopped (HTTP Range).
- **EPG & catch-up**: now/next comes from the portal's short-EPG API; the full guide uses an XMLTV
  feed (advertised by the portal, or pasted in Settings → EPG). Channels with a provider archive
  get the existing catch-up features — Guide "Watch from start", the Live TV catch-up picker, and
  live rewind.
- **Fast, resilient sync**: bulk `get_all_channels` fast path (thousands of channels in seconds)
  with paged fallback, and transient portal errors (HTTP 429/5xx) retried with backoff so a hiccup
  never drops a category. Re-syncs and auto refreshes are non-destructive (favorites, history and
  progress survive).

### 🧭 Sidebar Menu Customization

- **Show only the icons that match your playlist.** A playlist that only has VOD no longer clutters
  the side menu with Live TV / Guide; a Live-only playlist hides Movies / Series / Downloads. Open
  **Settings → Sidebar Menu Customization** and switch **Behavior** between **Static** (manually
  hide any of the six icons — Home, Live TV, Movies, Series, Downloads, Guide) and **Dynamic** (the
  icons auto-adapt to what the active playlist actually contains).
- **Dynamic** reuses the existing per-source content counts (Home & Settings always show; Live/Guide
  show when there are channels; Movies/Series when their tables have rows; Downloads when Movies or
  Series exist — Live has no download). Counts re-evaluate on their own after every sync, so the
  rail updates the moment content arrives. With the top-bar picker on "All playlists", counts are
  unioned across the profile's sources.
- **Static** is the default (all icons visible) — existing users see no change until they opt in.
  Settings is always pinned at the bottom and can never be hidden; hiding every browse icon lands
  the app on Settings.
- **Focus & fallback:** if the section you're viewing becomes hidden, the app jumps to the first
  still-visible browse item (or Settings if all are hidden); opening the screen lands focus on its
  first row.
- The mode and hidden-icon set are part of **Backup & Restore**.

### ✨ Improvements

- **Favourites from the TV Guide.** Add or remove a channel from Favourites without leaving the
  Guide: **long-press a channel label** for the channel menu (favourite toggle + the existing EPG
  match options), or use the **Favourite** button in a programme's details dialog. Stars show up
  immediately in Live TV, Search, and the Home rail, and the Guide's "Favorites" sort refreshes in
  place.

- **Subscription expiry in Manage sources.** Each Xtream and Stalker playlist row now shows an
  "Expires …" note with the account's end date (read from the provider when the screen opens).
  M3U playlists have no account, so they don't show one.

- **Deleting a playlist now shows its progress.** Removing a source with a huge catalog
  (hundreds of thousands of channels/movies/episodes) can take a while — the source row in
  **Settings → Manage sources** now shows a "DELETING…" badge with a spinner until the removal
  finishes, and the row's Edit/Re-sync/Delete buttons are hidden meanwhile so it can't be
  touched mid-delete. The removal also now always runs to completion even if you leave the
  Settings screen while it's working.

### 🐛 Fixes

- **Updating from 4.0.x/4.1.0 could crash the app at launch (database self-heal).** If a large
  playlist or EPG import was ever interrupted mid-sync (TV standby, low memory, force-stop), the
  import speed-up that temporarily drops SQLite indexes could leave some of them missing. That was
  invisible in daily use, but the next app update re-validates the whole database schema — so the
  update crashed the app on every launch until the previous version was reinstalled. The database
  now **self-heals**: the final migration and every database open recreate any missing index or
  search (FTS) table (idempotent and effectively instant on healthy installs), every index-restore
  pass shares one canonical index list so a gap can never persist again, and the post-import index
  rebuild now covers the rating-sort indexes it previously missed. Verified against every public
  upgrade path (v1.0.0 → current) — updating preserves all playlists, favorites, history and
  progress; no reinstall needed.
- **Hidden categories are now respected in the TV Guide.** Categories hidden via Customize no
  longer appear in the Guide's "Category" dropdown, and their channels stay out of the guide grid
  (matching Live TV). The dropdown also now shows your category **renames** and keeps manually
  **reordered** categories pinned first, like the Live TV rail. If the category you were filtering
  by gets hidden, the Guide falls back to "All" instead of showing an empty grid.
- **Download retry & failure polish.** Retrying a download now stops the old attempt before
  starting fresh (previously the two could race and corrupt the restart); a failed download keeps
  its real partial byte count instead of showing 0; and a resume that finds the file already fully
  downloaded is marked completed instead of failing.

### 🔧 Under the hood

- **Sync engine split into per-source-type modules.** The single large `SyncManager` was split
  into a thin dispatcher plus `XtreamSyncer`, `M3uSyncer` and a shared `SyncSupport` toolbox
  (chunked inserts, stable upserts, category refresh, pruning) — groundwork for the upcoming
  Stalker portal source type. No behavior change; import/sync logic and logging are identical.
- **Migration tests modernized.** The database migration test suite now runs every chain to the
  current schema version (it had stopped at v9), and gains a regression test that deliberately
  drops indexes from a v12 database and asserts the new self-heal repairs it during the upgrade.

## v4.1.0 — 2026-07-11

### ✨ New features

- **Playback error log in Settings.** The last ~10 playback failures are now kept on the device —
  each with its plain-English reason, the stream's codec/resolution spec, the raw engine error, the
  engine (mpv/ExoPlayer), Live/VOD, and your device model/Android version. Open **Settings →
  Playback → Playback error log** to read (or clear) them, so you can report exactly what happened
  even after dismissing the error screen or restarting the app — no adb/logcat needed.
- **Custom TMDB names are now in Backup & Restore.** Titles/years you hand-corrected via long-press →
  **Custom TMDB name** (for providers with weird item names) now ride in the backup's Customizations
  section and are merged back on restore — any stale cached match for a restored key is dropped so the
  corrected name re-fetches. Two more backup upgrades ride along: your own **TMDB API key** is now
  included when (and only when) the backup is password-encrypted (same policy as source/proxy
  passwords), and **recent searches** are backed up with settings. Older backup files still restore
  fine; older app versions simply ignore the new blocks.
- **Wider interface zoom range.** **Settings → Interface zoom** now goes from **50% to 150%**
  (previously 65%–140%), for tighter grids on big screens or larger UI on small/far ones. The
  existing low-memory warning below 85% still applies.

### ⚡ Performance & reliability

- **Smaller app, faster cold start (R8).** Release builds are now shrunk and optimized by R8 —
  dead code is stripped and the remaining code is optimized, so there's less to load on
  low-end TV boxes. Baseline profiles bundled by the UI/player libraries are now actually
  installed on sideloaded installs (via ProfileInstaller), pre-compiling the hot startup and
  scrolling paths instead of leaving them to the JIT on first run.
- **Faster playlist import on huge playlists.** The M3U parser now extracts all `#EXTINF` attributes
  in a single scan of each line (previously ~10 separate searches per channel), and the detailed
  per-item timing instrumentation in both the M3U and Xtream parsers is now off unless explicitly
  enabled for debugging (`setprop log.tag.M3uParser DEBUG` / `log.tag.XtreamClient DEBUG`) — removing
  millions of clock syscalls from a 100k+ item sync. The single-scan parser also fixes a subtle
  mis-parse where a key could match inside a longer key (e.g. `type` inside `tvg-type="…"`).
- **Scheduled syncs now retry after network blips.** A playlist or EPG auto-refresh that failed on a
  transient error (offline, timeout, connection reset, server 5xx) previously gave up until the next
  scheduled window, leaving content stale. Both sync workers now ask WorkManager to retry with backoff
  (up to 3 attempts); permanent errors (bad credentials/URL, malformed data) still fail immediately.
  Xtream category-list fetches also get up to 3 HTTP attempts, and server 5xx/429 responses are retried
  safely (only when no data was consumed yet).
- **Player stability hardening.** The stream-info chips (fps / audio layout) no longer read libmpv
  properties on the UI thread — on a stalling stream those reads can block for seconds and caused
  potential freezes/ANRs. Queued freeze-frame callbacks are now cleared when the player is released, so
  they can never fire against a destroyed surface.
- **More accurate playback error diagnosis.** The plain-English error mapper no longer mis-labels
  errors whose stream URL merely *contains* digits like `509`/`403` as HTTP provider errors, and a
  spurious "out of memory" match on any `-12` substring is fixed. The background codec-error log tail
  now restarts itself if the system kills it, so error details keep working for the whole session.
- **Much faster global search on huge catalogs.** Search-as-you-type now uses the full-text index
  instead of scanning every movie/series/channel name per keystroke — on a 170k-movie catalog each
  keystroke was a full table scan. Matching is now by word prefix ("harry pot" finds
  "Harry Potter…"); folder-scoped search keeps the old substring behaviour.
- **Big folders page faster.** Folders where you never used **Move** (manual reorder) now use the
  plain indexed query instead of the reorder-aware join that re-sorted the whole folder on every
  page turn. Folders with manual positions behave exactly as before.
- **Smoother UI during large syncs.** The live item-count badges (Live/Movies/Series and the EPG
  programme count) now refresh at most once per second during a bulk import instead of re-counting
  the whole table after every committed batch.
- **Posters and channel logos are cached on disk.** Artwork now survives app restarts (capped at
  250 MB) instead of re-downloading every session, loads offline once seen, and opaque poster
  bitmaps use half the memory.
- **Faster, safer backup restore.** Restoring thousands of favorites/history/resume records used to
  run one database transaction per record; they're now batched (500 per transaction). The
  profiles-and-sources restore is atomic: a crash mid-restore can no longer leave a half-restored
  database.
- **Faster first launch when upgrading from v3.2.0 or older.** The one-time database migration no
  longer de-duplicates the (huge) cached TV guide row-by-row — it clears the rebuildable guide cache
  instead, so the first launch after a big version jump is instant. The guide re-downloads on your
  next EPG sync. (Upgrades from any 4.x version are unaffected.)
- **Less UI work while browsing.** The most-passed-around UI models (channels, movies, series,
  home-screen state, EPG now/next, search results, weather, details panes) are now marked immutable
  for Compose, so screens can skip re-rendering unchanged parts instead of redrawing whole subtrees
  on every state tick.
- **TMDB caches no longer grow forever.** Metadata cached for items you haven't opened in 90 days is
  cleaned up after each playlist sync and simply re-fetches if you come back to them.

### 🐛 Fixes

- **Dialogs no longer get cut off on small screens.** On low-resolution/overscanned TVs, tall popup
  dialogs (New profile, context menus, Settings dialogs, catch-up & EPG-match pickers, the setup
  wizard, and more) could extend past the screen with no way to reach the lower buttons — profile
  creation could not be completed at all. Every popup is now scrollable (D-pad focus scrolls
  off-screen controls into view) and list pickers cap their height to the screen.
- **Grids keep your place through background refreshes.** The Movies/Series/Live lists and grids now
  track items by identity instead of position, so a background re-sync or list update no longer
  scrambles D-pad focus or recomposes every visible poster.

- **A–Z sorting now applies to categories too.** The sort chip in Live TV, Movies and Series only
  reordered the items inside a folder — the category rail itself always stayed in provider order.
  Switching the chip to **A–Z** now also sorts the category folders alphabetically (by their displayed
  name, so renamed categories sort under their custom name), and the TV Guide's category picker follows
  the Live TV setting the same way. Categories you manually reordered in **Settings → Customize**
  stay pinned at the top in your custom order in every mode; the rest sort below them. **Provider**
  (and **Rating**) modes keep the playlist order exactly as before. The fixed rail entries (All,
  Favourites, Recent…) never move.

### 🔧 Under the hood

- **CI dev builds are now release builds.** Every push now produces a release-signed, R8-shrunk
  `OwnTV-dev-<sha>.apk` artifact (previously debug), versioned `99.99.99` so it installs straight
  over any published release for testing. Publishing a GitHub Release still only happens on `v*`
  tags. Fork PRs (no signing secrets) still build debug.
- **Player timing constants named.** The ~15 bare `delay()` literals in the playback engine
  (decoder-release waits on mpv↔ExoPlayer handoffs, live-reconnect pause, surround/decode
  verification windows, retry beats) are now named companion constants documented in one place —
  no behavior change.
- **Dependency updates.** Koin 4.1.1 → 4.2.2, Coil 3.3.0 → 3.5.0, WorkManager 2.10.0 → 2.11.2.
  (core-ktx/lifecycle/Compose BOM stay put — their latest versions require compileSdk 37; OkHttp 5
  is deferred as its own change.)
- **Sync engine de-duplicated.** The three near-identical Xtream phase implementations
  (Live/Movies/Series: fresh-vs-stable upsert, per-category 512 fallback, prune) are now one generic
  phase parameterized per content type, so future fixes to the sync logic land once instead of three
  times. Behavior-identical; the category refresh also drops a redundant second database lookup.
- **Media3 (ExoPlayer) bumped 1.10.0 → 1.10.1.** ExoPlayer drives the image-subtitle (PGS/VOBSUB/DVB)
  handoff and the VOD mpv→Exo fallback, so this patch release lands fixes directly on those paths:
  a crash when recovering from decoder errors with renderer prewarming (the fallback triggers this),
  an `ArrayIndexOutOfBoundsException` during HLS stream fallback when the active track set is a subset
  of the manifest (#3161), and HLS init segments not carrying over across playlist updates when
  `#EXT-X-MAP` isn't repeated (#3105). It also stops needless MediaCodec resets at frame-rate changes on
  API < 30. `libmpv` is unchanged at `1.0.0` (still the latest).

## v4.0.3 — 2026-07-09

### ✨ New features

- **Settings: search and quick toggles.** A **"Search settings…"** field at the top of Settings filters
  the whole screen down to matching rows — results carry their group as a breadcrumb (e.g.
  `Playback › HDR`) and act exactly like the real row, so you jump straight to a setting without hunting
  through groups. Above it, a pinned row of one-press **quick toggles** (Live preview · Preview sound ·
  HDR · Auto-play · Check for update) flips the most-used options without opening a sub-menu. **Back**
  clears an active search before it leaves Settings.
- **Search: a launcher home, a detail pane and smarter Back.** The empty Search screen is now a launcher
  — a **"Jump to"** row (**Continue watching**, **Unwatched**, **Channels**) plus your **recent
  searches** as chips (with **Clear**). Results moved to a **list + detail** layout: focusing a result
  shows its poster, plot and rating in a side pane with a **primary action** button (Play / Watch live /
  Open series), and OK still plays it directly. **Back** clears the query (returning to the launcher)
  before it leaves Search. "Unwatched" and "Channels" are bounded to your favourites (and recent
  history) so they stay fast on large playlists.
- **Downloads: queue groups, a storage bar and clearer failures.** The Downloads list is now grouped
  into **Active · Waiting · Completed · Failed** sections with counts, a **storage bar** at the top shows
  free space (e.g. `12.4 GB free of 118 GB`), and a failed download now reads
  **"Download failed — couldn't reach the source. Tap Retry."** next to its one-press Retry.
- **Download status on the poster.** Start a download of a movie, a whole series, or a single episode and
  a compact **status strip** (Downloading / Queued / Paused / Failed, with a progress bar) now appears at
  the top of that item's poster panel — so you can see it's actually running without opening the
  Downloads screen. The strip only shows while something is in flight and disappears once complete.
- **Shell: a shared "Continue" chip.** The top bar now carries a compact **Continue** chip that resumes
  your most-recent item in one press — **Resume** a movie, **Next up** an episode, or your **Last
  channel** — labelled with the title and shown on every screen. It only takes focus from the navigation
  panel (like the search pill), so it never gets in the way while browsing, and hides when there's
  nothing to resume.
- **Series episode view: watched state, "Next up" and a "Hide watched" filter.** Episodes now show a ✓
  (and a dimmed title) once watched to ≥95%, and a thin progress bar when part-watched, so you can see
  exactly where you are in a season at a glance. Season chips show a `watched/total` count
  (e.g. `Season 2 · 8/18`). A **"Next up" card** at the top of the episode detail pane surfaces the
  episode to continue with — the one you're mid-way through, or the next one after the last finished —
  with a one-press **Play** (and a `Resume <time>` line when in progress). A **"Hide watched"** toggle
  in the header filters the list down to what's left to watch. Opening a show still focuses your
  last-watched episode (#22); when that episode is hidden by the filter, focus falls to the first
  visible one instead of losing focus.
- **Mark an episode watched / unwatched manually.** Long-press an episode for a new **"Mark as
  watched"** option (or **"Mark as unwatched"** if it's already watched) — corrects the auto-detected
  ≥95% state without playing the episode. Marking watched restarts the episode from the beginning the
  next time you press Play (it won't jump to the credits).
- **TV Guide: a "now" line, Jump-to-Now, catch-up badges, genre dots and a preview strip.** The guide
  grid now draws a red vertical line at the current time; a **"Jump to Now"** button in the header
  scrolls the timeline back to now (handy after browsing the catch-up archive); programmes you can
  rewind from show a ↻ badge; channel labels get a small colour dot by genre
  (sport / news / movies / kids / music / docs); and a non-modal strip at the bottom previews the
  programme under the cursor (title, channel, time, runtime, catch-up, synopsis) without opening the
  dialog — OK still opens the full detail.
- **Movies: watched state on posters and a resume label.** Movie posters (and the compact list rows)
  now show a ✓ badge (with dimmed art) once watched to ≥95%, and a thin progress bar when part-watched,
  matching the Series episode view. The movie detail pane shows a `Resume <time>` label under the poster
  when there's an unfinished position, and long-press gains a **"Mark as watched / unwatched"** option
  (mirrors Series; marking watched still restarts from the beginning on Play).
- **Player: a next-episode countdown card.** When a series episode nears its end, a card appears with a
  countdown to the automatic next-episode advance plus **Play now** and **Cancel** — so you can jump
  early or stop the auto-advance. Works on both the mpv and ExoPlayer engines.

### 🐛 Fixes

- **All seasons now reachable on long-running series.** The season selector on the Series detail screen
  was a single non-scrolling row, so shows with more seasons than fit on one line (e.g. a 12-season
  series) had the seasons past the visible ones clipped off the right edge — invisible and unreachable
  with the D-pad. The selector is now a scrollable rail: Right/Left moves season-by-season and
  auto-scrolls the focused season into view, and opening a show scrolls straight to the active
  (last-watched) season.
- **Clearer 4K decode-guard message.** When a stream's format can't be hardware-decoded on the TV and
  falls back to software decoding (which can't sustain >1080p), the error now explains the stream's
  format is the issue rather than implying the TV can't play any 4K content — the TV may still play
  other 4K videos fine.
- **Player seek bubble now shows time remaining.** The scrub bubble above the seek thumb was not
  displaying (padding couldn't lift it out of the bar) and, once fixed, now reads the time left to the
  end (e.g. `-12:34`) — the elapsed and total times are already shown at the bar's two ends.
- **Favourite "On Now" now covers every favourite channel.** When the Favourite Channels row was set to
  **On Now**, the inline mini-guide only looked up programme data for the first ~10 favourites and left
  the rest without guide info. The builder now reads programme summaries for the whole candidate list in
  a single batched query, so every visible favourite shows its airing show (community PR #62 by
  [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV).
- **Home artwork and metadata from TMDB.** The Home hero card and Continue Watching series tiles now
  prefer **TMDB backdrops, title logos and plot text** when metadata is available, while preserving the
  provider artwork/text fallbacks. Continue Watching series tiles resolve episode/show artwork on focus
  and render as **landscape cards** instead of stretched portrait art. The hero's expanded view now uses
  a landscape backdrop with a title logo, plot and a Play action (community PR #62 by
  [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV). Requires metadata cache v13 (Room
  migration `12 → 13`, additive `logoPath` column on `metadata_cache`).
- **Home refreshes after a playlist switch.** Switching the active playlist from the top-bar quick
  switcher while sitting on Home now updates the hero, Continue Watching, Recent and Favourites rows in
  place — previously you had to leave and reopen Home to see the new source's content (community PR #62
  by [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV).
- **Manual reorder now survives Backup & Restore.** The Move up/down positions you set for channels,
  movies and series (the `content_order` table from v4.0.0) were never written to a backup or restored
  — the resolver supported it but the backup section picker never asked for it. Backup & Restore now
  has a dedicated **Manual reorder** section (export and restore) so your custom order comes back after
  a restore. Existing backup files still restore cleanly; older files simply have no reorder data to
  apply.

## v4.0.2 — 2026-07-07

### 🏠 Customizable Home screen — reorder/hide rows, dwell-to-expand hero, On Now mini-guide (community PR #58 by [@codeVerine](https://github.com/codeVerine) — Sagar Mukundan UV)

- **Reorder and show/hide every Home row** via the new **Settings → Home screen** page (per profile):
  Keep Watching hero, Recent Channels, Favourite Channels, Continue Watching Movies, Continue Watching
  Series can each be toggled and moved up/down/top/bottom. When every row is hidden, Home says so
  instead of showing a blank screen. Configs ride with **Backup & Restore** (backup format v8; older
  backups restore cleanly with defaults).
- **Filter the Keep Watching hero row** — independent toggles include/exclude live channels, movies and
  series from the hero strip (e.g. keep it VOD-only). Addresses **#43**.
- **Redesigned hero cards — dwell-to-expand** — a card stays compact until it holds focus for **3
  seconds**, then widens to a 16:9 preview with a **blurred-artwork backdrop** (no more stretched
  channel logos — **#49**). Quick D-pad sweeps never expand; the video preview starts only after the
  expansion settles, and the row stays anchored on the active item across data refreshes.
- **"On Now" mini-guide rows** — Recent Channels and Favourite Channels can each display as **Cards**
  or **On Now**: an inline programme guide with the currently-airing show, live progress bar, and the
  next ~6 hours, sharing the real EPG renderer. Up/Down picks a channel, Left/Right scrolls the
  timeline, OK tunes. Favourite Channels defaults to On Now.
- **New Recent Channels row** (hidden by default) — recently tuned live channels, respecting the active
  playlist filter.
- **Times follow the device's 12h/24h clock setting** across Home, Live TV preview, TV Guide and the
  catch-up dialog (previously always 24h).

### ⚙️ Settings menu reorganized

- **Profiles** moved to the **top** of Settings (own "Profile" group, first focused row).
- **Live preview** and **Preview audio** moved from Content into the **Playback** group.
- **App startup** (Home / Last channel / Live TV Favorites) now lives in the **App** group.
- **Home screen** (new page above) sits in Content; the **Android TV home** toggle + refresh moved into it.

### 🗂️ Multiple playlists — switch the whole app to one playlist (or all)

- **Selecting a playlist as "Default" now actually filters the app.** Previously the Default toggle only
  changed a label; the Browse screens always merged every playlist. Now choosing a default narrows
  **Live TV, Movies, Series, TV Guide, Search, and the Home rails (Continue Watching / Favourites)** to
  that one playlist. Choosing **All playlists** (no default) restores the merged view — exactly the old
  behaviour. It's a view filter only: nothing is deleted or re‑imported, and switching back to All brings
  everything straight back.
- **New top‑bar playlist switcher.** With 2+ playlists, the playlist chip in the top‑right becomes a
  button (with a ▾) that opens an **All playlists / A / B / C** picker. It applies everywhere instantly and
  **persists across restarts**, so you can switch without opening Settings.
- **Default is now chosen in the playlist's Add/Edit form** via a **"Default playlist"** toggle (instead of
  a per‑row button). The Sources list shows a **DEFAULT** badge as a status marker. Turning the toggle off
  on the current default clears it back to **All playlists**.
- **Favourites & History inside each section respect the selected playlist** — with a single playlist
  active you no longer see another playlist's favourites/history mixed in; the rail counts match too.
- The selected default is included in **Backup & Restore** (Sources section).

### ✨ VOD engine fallback (movies & series play on more devices)

- **Automatic second-engine retry for Movies & Series** — if a movie or episode terminally fails on
  the mpv engine (file rejected, decoder stall, all retries exhausted), the same item is now retried
  automatically on ExoPlayer at the same position before any error is shown. Some devices/providers
  play streams on ExoPlayer's decoder path that mpv can't open — previously those items just errored
  even though the hardware could play them (as Live TV, which starts on ExoPlayer, proved). Each item
  gets one fallback attempt; if **both** engines fail, the error says so explicitly ("Playback failed
  on both video engines") instead of a misleading single-engine message.
- **New setting: Settings → Video Player → "Movies & Series player"** — choose which engine plays VOD
  first: **mpv** (default; widest format support — DTS/TrueHD audio, unusual containers — plus the
  A/V sync nudge) or **ExoPlayer** (for TVs/providers where mpv can't start movies at all; no
  DTS/TrueHD decoding and no A/V sync fix). Whichever is picked, the other is still tried
  automatically on failure, in reverse order. Live TV and catch-up are unaffected. The setting is
  included in Backup & Restore like the other player preferences.
- **Player top bar shows the active engine** — the mini chips in the player's top-left (aspect ·
  resolution · fps · audio) now lead with **MPV** or **EXO** on every stream — Live TV, Movies and
  Series — so you can always tell at a glance which engine is playing.
- **Stream Info shows the active engine** — the player's info overlay now leads with an "Engine" row
  (mpv / ExoPlayer, including *why* ExoPlayer is active: preferred, fallback, or image-subtitle
  handoff), and shows real ExoPlayer codec/resolution/audio/buffer data while it owns playback.
- **In-player engine toggle for movies & episodes** — the player's **engine toggle (the ⇄ MPV/EXO
  pill, same spot as Live TV's compatibility mode)** switches the **current** item between mpv and
  ExoPlayer at the same position, without changing the global setting. Useful to check whether the
  other engine exposes a subtitle or audio track the current one doesn't — flip, check the tracks,
  and stay on whichever works. The pill shows the active engine (teal while on ExoPlayer) — and,
  like Live's compatibility mode, the choice is **remembered per movie/episode**: a toggled item
  opens on that engine every time, while everything else keeps following the setting.
- **Engine toggle restyle + confirmation toast** — the Live "compatibility mode" and the in-player
  mpv/ExoPlayer switch are no longer a gear icon: they're one labeled pill that shows the active
  engine (MPV or EXO) and turns teal on the non-default one. Flipping it briefly pops up a small
  "Switched to MPV" / "Switched to ExoPlayer" note at the bottom of the player, so the change is
  always confirmed. Applies everywhere the toggle appears: Live TV, Movies, Series, and channels
  opened from the Guide.
- While ExoPlayer owns VOD playback: subtitles (text **and** image) and audio tracks are selectable
  directly on it, autoplay-next keeps working across episodes and seasons, and progress/resume is
  tracked as usual.

### 🔄 Per-source Auto Refresh (playlists & EPG)

- **Each playlist and EPG source can now refresh itself automatically** — open Settings → Manage
  sources (playlists) or Settings → EPG sources and pick an **Auto refresh** mode per source: **Off**,
  **Refresh at startup** (once per cold app start), or a staleness interval (playlists: 6h / 12h / 24h
  / 48h; EPG: 1h / 3h / 6h / 12h / 24h / 48h). Interval modes are checked on cold start **and** when
  the app returns to the foreground; a source refreshes only once it's actually stale (now − last
  successful sync ≥ the chosen threshold), so resuming the app doesn't re-sync everything every time.
- **Off by default** — new playlist and EPG sources start with Auto refresh **Off**; nothing syncs in
  the background unless you turn it on. Existing users who had the old "Refresh on startup" toggle
  enabled are migrated to **Refresh at startup** so their behaviour is unchanged.
- **Failure-safe freshness** — a failed EPG sync no longer marks the source as freshly synced, so a
  source that errors stays "stale" and is retried on the next check instead of being skipped for the
  full interval. Never-synced sources are always treated as stale. Auto refreshes preserve existing
  data (they never clear-then-reimport); a manual sync still does the full replace.

### 💾 Backup & Restore now covers every persistent setting

- **Auto Refresh selections are backed up** — the per-source playlist and EPG Auto refresh modes ride
  with the **Profiles & sources** section. On restore, a saved mode is re-applied only if that source
  still exists; ids that no longer exist are skipped, and an unknown/corrupt mode falls back safely to
  **Off**. Sync timestamps are **not** backed up — after a restore the app re-derives freshness from
  the restored mode and the real sync state.
- **Per-item compatibility mode is backed up** — the Live TV "compatibility mode" pins and the
  Movies/Series per-item engine pins (mpv / ExoPlayer, set from the player's engine toggle) are now
  saved and restored with the **App settings** section. They're keyed by stream URL, so they survive a
  re-sync, and restore **merges** them into any pins you've already set rather than replacing them.
- **Audit gaps closed** — the **Default source** selection and the legacy **"resume last channel"**
  preference were being stored but not backed up; both are now included. Every user-facing preference
  in the settings store is now covered by Backup & Restore.
- **Download folder is backed up too** — the chosen **Download folder** (Settings → Storage) was the
  one persistent setting still missing; it now rides with App settings and restores on import. On a
  different device a path that no longer exists harmlessly falls back to app storage, so a stale
  restore never breaks downloads.
- **Backward compatible** — older backup files that lack any of these new fields still restore
  cleanly: missing Auto refresh defaults to the normal app behaviour (EPG stays Off), and missing
  compatibility-mode/default-source fields simply leave your current values untouched. Unknown or
  invalid entries are ignored — a restore never crashes on them.
- **Customize PIN lock is backed up** — each profile's Customize PIN rides with the **Profiles &
  sources** section and is restored per profile (PINs for profiles that no longer exist are dropped
  safely; older backups without the field restore as before).

### 🎬 TMDB metadata enrichment (Movies, Series & Episodes)

- **On-demand TMDB enrichment** — cached posters, plots, cast, genres, ratings and backdrops from TMDB,
  filling the gaps your playlist leaves. Fully opt-in and cached in Room; no bulk calls. Works out of the
  box via a shared caching server (no setup), or bring your own TMDB API key / self-hosted server.
- **Metadata source mode (Settings → Metadata)** — choose **Provider only**, **Provider + TMDB** (provider
  wins, TMDB fills gaps), or **TMDB only** (TMDB preferred). Advanced key/self-host fields appear only when
  TMDB is on.
- **TMDB Details window** — long-press a movie or series (or episode) → **TMDB Details** opens a scrollable
  window with the backdrop/still, full overview, cast, genres and rating (Back to close).
- **Series & episode enrichment** — series show pages and, inside a series, a new **episode detail pane**
  showing each episode's TMDB still, plot, air year and rating (resolved lazily per season).
- **Sort by rating** — the Movies & Series sort chip now cycles Provider → A–Z → **Rating** (highest first).
- **Cleaner detail pane / interaction** — the side detail pane is now display-only (single-press plays,
  long-press for Favorite / Download / TMDB Details), which also fixes D-pad navigation from the grid to the
  pane. Episode rows lost their play/download icons (single-press plays, long-press for Download / Details).
  Downloading an already-downloaded item shows a toast instead of re-queuing.
- **Better title matching** — provider prefixes like `4K-OSN+ - ` are now stripped before searching TMDB, so
  more messy playlist titles resolve correctly.
- **Refetch TMDB details (long-press)** — clear a wrong/stale TMDB match (or a 7-day "no match" cache) and
  re-search immediately, on Movies, Series, and Episodes — no need to wait for the cache to expire. Lets the
  improved title matcher reach titles that failed before the fix.
- **Set TMDB name (long-press)** — manual override for titles the matcher still gets wrong: type the exact
  TMDB title (and optional year) and OwnTV re-searches under that name, on Movies and Series. The override
  survives playlist re-syncs; Clear reverts to automatic matching. Episodes inherit their series' match.
- **In-app toasts** — transient notices (refetch, already-downloaded, re-search) now use a themed in-app
  toast instead of the system toast.
- **🎞️ In-app trailers (Movies & Series)** — long-press → **Play Trailer** (shown only when TMDB has one)
  plays the YouTube trailer in a floating window styled like the TMDB Details window, with Exit, a progress
  bar and D-pad ◀/▶ ±10s seek. Falls back to opening the YouTube app if the built-in player can't run.
- **Self-hostable metadata server** — the caching-proxy Worker source now ships in `worker/` with a README,
  so anyone can deploy their own and point OwnTV at it.
- **Attribution** — Settings → Metadata shows the TMDB logo and the required notice: this product uses the
  TMDB API but is not endorsed or certified by TMDB.

### 🙈 Hide individual movies & series — and a Customize PIN lock

- **Hide any single movie or series** (not just whole categories) — long-press an item → **Hide**
  removes it from everywhere at once: global **Search**, in-section search, its **category**, the
  **All** list and count, **Home** rails (Continue Watching / Favourites), the Android TV **Watch
  Next** launcher, and **Downloads**. The downloaded file stays on disk and the item returns the
  moment you unhide it — exactly like Live TV's per-channel hide.
- **Hidden categories now hide their items everywhere too** — previously hiding a Movies or Series
  category only dropped the folder from the rail, while its items still showed in **All** and
  **Search**. Hiding a category now behaves like Live TV: the items vanish from Search, All and the
  Home/launcher rails until you unhide the category.
- **Unhide everything from one place** — Settings → **Customize & Hidden Items** (renamed from
  "Customize Category", since it now manages hidden items too) lists every hidden channel, movie and
  series per section, each with an **Unhide** button.
- **Optional PIN lock on the Customize screen** — tap **🔒 Set PIN** at the top-right of Customize &
  Hidden Items to lock it; afterwards every entry asks for the PIN, so nobody else can unhide items
  or change your category setup. It is per-profile, asked each time you open the screen, and
  **deliberately not included in backups** — a lock code shouldn't travel in a readable file, and a
  restore must never lock you out.

### ✨ External player — play movies, series & downloads in VLC / MX Player

- **New setting: Settings → Video Player → "External player"** — when on, pressing Play on a **Movie**,
  **Series episode**, or **Download** opens the stream in an external video player (VLC, MX Player, …)
  instead of the built-in one. Useful for streams this app can't decode, or if you simply prefer another
  player. Turning it off restores normal in-app playback. The setting is included in **Backup & Restore**
  like the other player preferences.
- **Long-press "Play with external player"** — every movie and series episode's long-press menu has a
  new action that plays just that item externally, **regardless of the global setting**. Completed
  downloads get an **"External"** button next to Play.
- **Live TV is unaffected** — channels always play in the built-in player (external routing would lose
  rewind/catch-up). Movies, Series and Downloads are the only sections that route externally.
- **Smart hand-off** — if more than one player is installed you get a chooser; if exactly one is set up it
  opens directly; if none is installed you get a clear "install VLC or MX Player" message instead of a
  silent failure. Downloaded files are shared safely via a content URI (not a raw file path).
- **Trade-offs when playing externally** (the same ones every IPTV app has): resume position and
  prev/next aren't available, and streams that require a custom User-Agent or referer header may not
  play in the external player. Watch history is still recorded.

### 📺 Live TV closed captions now work (#57)

- **ExoPlayer engine: embedded CEA-608 captions on raw MPEG-TS channels are now detected.** IPTV
  panels almost never declare captions in the stream tables, so the player never exposed them; the app
  now surfaces the standard **CC1** track on every `.ts` live channel (HLS channels already worked).
  Because detection is unconditional, the CC entry also appears on `.ts` channels that carry no
  captions — selecting it there simply shows nothing.
- **mpv engine: selecting the CC track now actually renders captions.** CC text can only be extracted
  by the software video decoder, so while a CC track is selected the channel temporarily switches to
  software decoding (≤1080p only — the same GL path used by the decoder-rescue fallback) and switches
  straight back to hardware decoding when CC is turned off or you change channels. Expect a ~1s
  blip when toggling. On >1080p channels captions stay unavailable on mpv rather than risking
  stutter; use the ExoPlayer engine there.

### 🌦️ Weather settings submenu — Celsius / Fahrenheit

- The two weather rows on the Settings root are now a proper **Settings → Weather** submenu with three
  options: **Show weather** (top-bar chip on/off), **Custom location** (city or "lat,lon"; blank =
  auto-detect — useful on a VPN), and a new **Temperature unit** toggle (**°C / °F**) for the top-bar
  chip. All three are included in Backup & Restore.

### ⚠️ Low-zoom memory warning (#51)

- **Setting UI Zoom below 85% now asks you to accept the risk first.** Lower zoom draws far more
  items on screen at once, which can crash devices with limited memory (e.g. 2 GB Fire TV sticks)
  when combined with large playlists and EPG data. Stepping under 85% shows a one-button warning —
  **OK** ("I understand and accept the risk") continues, **Back** keeps zoom at 85%. If your zoom is
  already below 85%, the dialog doesn't nag.

### 🐛 Fixes

- **Fixed D-pad navigation from the Movies/Series grid to the detail pane** — the display-only pane no
  longer traps focus on the way right.
- **Fixed episode long-press menu losing focus** — after an action in the episode context menu (e.g.
  Refetch TMDB details), focus now returns to the episode row instead of jumping away.
- **Failed TMDB lookups are no longer remembered as "no match"** — a network error, rate limit or proxy
  outage during a lookup now simply retries on the next open, instead of being negative-cached for 7 days
  like a genuine "title not on TMDB" answer. The Settings test lookup also distinguishes "server
  unreachable" from "no match".

- **Live channel-list overlay now matches the channel you launched from Home (#55)** — pressing Left
  while a Live channel plays opens the quick channel-list overlay. When you started the channel from a
  Live TV **category**, it correctly listed that category — but when you started it from the **Home**
  screen (Keep Watching or a Favourites rail), the overlay still showed the *previous* category's list.
  The Home launch path updated the CH+/CH- zap list but not the list the overlay reads, so the two
  disagreed. The overlay now reflects the same list you're zapping through — the Keep Watching /
  Favourites channels you actually opened.
- **Active nav section stays visible when focus moves away (#47)** — in the left navigation and the
  category rail, the *selected* item lost all highlight as soon as you moved focus to another item, so
  at a glance you couldn't tell which section/category was actually active. Both now use a consistent
  four-state treatment: **selected + focused** (full accent fill) → **focused** cursor (surface fill +
  teal outline) → **selected but unfocused** (soft tonal fill, accent tint and a persistent left accent
  bar) → idle. The accent bar gives a colour-independent marker of the active tab for low-contrast
  panels. Selection/focus boxes are also slightly less rounded (box-style) and the nav bar sits a little
  closer to the first panel, so the whole left navigation reads as one consistent system.
- **4K Live channels no longer break playback on some TVs** — on certain low-end panels (e.g. some
  Hisense models), watching a 4K channel could wedge the TV's hardware video decoder: every channel
  afterwards took ~20 seconds to start, and it stayed broken until the TV was rebooted (Google TV /
  higher-end sets were unaffected). The Live engine (ExoPlayer) was *parking* and reusing its decoder
  between channels instead of releasing it, so the stuck 4K decoder was never handed back. Now, whenever
  you **leave a UHD (>1080p) channel** — Back, exit full-screen, background, or zap to another channel via
  CH+/-, the D-pad, or the channel-list overlay — the decoder is **fully released** so the next channel
  starts cleanly. It's scoped to 4K only, so normal SD/HD zapping keeps the same fast, instant switching.
- **Live engine pill now shows the engine that's actually playing** — when a Live channel auto-fell-back
  from ExoPlayer to mpv, the MPV/EXO pill still read **EXO** (it was showing the saved pin, not the live
  engine), and tapping it appeared to do nothing. The pill now reflects the **running** engine, and one
  tap always switches it — flipping to mpv (and remembering the channel) or back to ExoPlayer. (The
  Movies/Series pill already tracked the live engine and is unchanged.)
- **Live TV zoom / aspect modes now work** — choosing Fit, Fill / Crop, Stretch, Original, Force 16:9
  or Force 4:3 on a Live TV channel did nothing at all (the picture never changed). Live channels play
  full-screen on ExoPlayer (the live engine), and that path had no zoom implementation — the mode was
  stored but never applied to the surface. Zoom/aspect now works on Live TV just like on Movies and
  Series, whether the channel plays on ExoPlayer or on mpv (a compatibility-mode pin).
- **Fill / Crop now actually zooms in and crops** — on Movies, Series and Live, "Fill / Crop" could
  look identical to Fit (especially on 16:9 content), or read as a stretch rather than a crop. It now
  takes the fitted picture and scales it up ~20% so it always visibly zooms and fills edge-to-edge,
  regardless of the source's aspect ratio. (Stretch remains a true distort-to-fill.)
- **Weather chip: VPN-friendly location override + hide toggle (#45)** — the top-bar weather guesses
  your city from your public IP, so on a VPN it showed the VPN server's city instead of yours. You can
  now set a manual **Weather location** (Settings → Appearance) — a city name (e.g. *London*) or a
  raw `lat,lon` pair (e.g. `51.5,-0.12`) — which is geocoded via Open-Meteo and overrides IP lookup.
  Leave it blank for the previous auto-detect behaviour. There's also a **Show weather** switch to hide
  the chip entirely. Both settings are included in Backup & Restore. Default ON + blank location means
  existing users see no change.
- **Modal D-pad focus can no longer escape into the UI behind it (#48)** — in the Exit, Avatar picker,
  Rename/Text-input, Resume, App-update and EPG-sync-prompt dialogs, pressing Left/Right/Up/Down from
  a button could move focus into the browse UI behind the dialog, leaving Cancel/Exit unreachable
  (only Back could dismiss it). A new all-directions focus trap keeps D-pad focus inside every modal
  scrim; Back still closes each dialog as before.
- **Focus returns to the right item after a long-press context menu (#46)** — on Live TV, Movies and
  Series, long-pressing OK on an item and closing the menu (Cancel / Favourite / Hide / Remove from
  history / Download) used to jump focus to the left Category rail. Focus now lands back inside the
  list/grid: on the exact item if it's still there, or on the **nearest surviving neighbour** if it
  was removed (e.g. unfavouriting on Favorites, or Remove from History) — only leaving the pane when
  the category becomes empty. The restore is now deterministic (id + position based), fixing an
  intermittent race where the paged list still held a stale copy of the removed item.

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
