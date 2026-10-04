package tv.own.owntv.ui.components

/**
 * The Stage mockup's line icons (`future-plan/ui-audit-227/index.html`, the `i-*` symbols), as SVG path
 * data on the same 24×24 grid [OwnTVIcon] draws on: 2-unit stroke, round caps and joins. Rects and
 * circles are written out as paths; nothing is redrawn by hand. FORWARD, CHEVRON and CHEVRON_UP are
 * the mirror images of the mockup's rewind, chev-l and chev-d, so each pair still matches.
 */
internal val StageGlyphPaths: Map<OwnTVIcon, String> = mapOf(
    OwnTVIcon.SEARCH to "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0zM20.5 20.5l-4.2-4.2", // search
    OwnTVIcon.HOME to "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z", // home
    OwnTVIcon.LIVE_TV to "M5.5 6H18.5A3 3 0 0 1 21.5 9V17A3 3 0 0 1 18.5 20H5.5A3 3 0 0 1 2.5 17V9A3 3 0 0 1 5.5 6zM8 2.5l4 3.5 4-3.5", // live
    OwnTVIcon.EPG to "M6 4H18A3 3 0 0 1 21 7V18A3 3 0 0 1 18 21H6A3 3 0 0 1 3 18V7A3 3 0 0 1 6 4zM3 9.5h18M8 2.5v3M16 2.5v3M7 13.5h4M7 17h7M14.5 13.5h2.5", // guide
    OwnTVIcon.MOVIES to "M6 3H18A3 3 0 0 1 21 6V18A3 3 0 0 1 18 21H6A3 3 0 0 1 3 18V6A3 3 0 0 1 6 3zM7.5 3v18M16.5 3v18M3 12h18M3 7.5h4.5M16.5 7.5H21M3 16.5h4.5M16.5 16.5H21", // film
    OwnTVIcon.SERIES to "M6 8.5H18A3 3 0 0 1 21 11.5V18A3 3 0 0 1 18 21H6A3 3 0 0 1 3 18V11.5A3 3 0 0 1 6 8.5zM5.5 5.2h13M8 2.2h8", // series
    OwnTVIcon.DOWNLOADS to "M12 3v12M7 10l5 5 5-5M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2", // download
    OwnTVIcon.SETTINGS to "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M14 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0zM8 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0zM16 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", // settings
    OwnTVIcon.FAVORITE to "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z", // heart
    OwnTVIcon.HISTORY to "M3 12a9 9 0 1 0 2.6-6.4L3 8M3 3v5h5M12 7.5V12l3 2", // history
    OwnTVIcon.REWIND to "M11 19 3 12l8-7zM21 19l-8-7 8-7z", // rewind
    OwnTVIcon.PLAY to "M7 4.5v15l12.5-7.5z", // play
    OwnTVIcon.INFO to "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM12 16.5v-5M12 8h.01", // info
    OwnTVIcon.STAR to "M12 2.8l2.8 5.8 6.3.9-4.6 4.4 1.1 6.3L12 17.2l-5.6 3 1.1-6.3L2.9 9.5l6.3-.9z", // star
    OwnTVIcon.CHEVRON_DOWN to "M6 9l6 6 6-6", // chev-d
    OwnTVIcon.SORT to "M4 6h16M7 12h10M10 18h4", // sort
    OwnTVIcon.FOLDER to "M3 7a2 2 0 0 1 2-2h4l2 2.5h8a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", // folder
    OwnTVIcon.SPARKLE to "M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z", // sparkle
    OwnTVIcon.REFRESH to "M20 11a8 8 0 0 0-14.3-4.9L4 8M4 4v4h4M4 13a8 8 0 0 0 14.3 4.9L20 16M20 20v-4h-4", // refresh
    OwnTVIcon.PAUSE to "M8 5v14M16 5v14", // pause
    OwnTVIcon.SUBTITLE to "M6 5H18A3 3 0 0 1 21 8V16A3 3 0 0 1 18 19H6A3 3 0 0 1 3 16V8A3 3 0 0 1 6 5zM7 14h4M13 14h4M7 10.5h10", // subs
    OwnTVIcon.CLOSE to "M6 6l12 12M18 6 6 18", // x
    OwnTVIcon.PERSON to "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0zM4 21a8 8 0 0 1 16 0", // user
    OwnTVIcon.PALETTE to "M12 3a9 9 0 1 0 0 18c1.2 0 1.8-.9 1.8-1.8 0-1.3-1-1.6-1-2.7 0-1 .8-1.7 1.8-1.7H17a4 4 0 0 0 4-4C21 6.6 17 3 12 3zM6.3 11a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0zM8.8 7a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0zM13.8 7.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z", // palette
    OwnTVIcon.LANGUAGE to "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z", // globe
    OwnTVIcon.WARNING to "M12 3 2 20h20zM12 10v4M12 17h.01", // warn
    OwnTVIcon.PHONE to "M9.5 2.5H14.5A2.5 2.5 0 0 1 17 5V19A2.5 2.5 0 0 1 14.5 21.5H9.5A2.5 2.5 0 0 1 7 19V5A2.5 2.5 0 0 1 9.5 2.5zM11 18.5h2", // phone
    OwnTVIcon.MORE to "M3.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0zM10.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0zM17.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", // dots
    OwnTVIcon.PLAY_CIRCLE to "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM10 8.5l5 3.5-5 3.5z", // play-c
    OwnTVIcon.BELL to "M18 8.5a6 6 0 0 0-12 0c0 7-3 8.5-3 8.5h18s-3-1.5-3-8.5M13.7 21a2 2 0 0 1-3.4 0", // bell
    OwnTVIcon.REC to "M6 12a6 6 0 1 0 12 0a6 6 0 1 0 -12 0z", // rec
    OwnTVIcon.CHEVRON_LEFT to "M15 18l-6-6 6-6", // chev-l
    OwnTVIcon.GRID to "M6 3H18A3 3 0 0 1 21 6V18A3 3 0 0 1 18 21H6A3 3 0 0 1 3 18V6A3 3 0 0 1 6 3zM3 9h18M3 15h18M9 3v18", // grid
    OwnTVIcon.LIST to "M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01", // list
    OwnTVIcon.CHECK to "M4.5 12.5l5 5 10-11", // check
    OwnTVIcon.CLOCK to "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM12 7v5l3.5 2", // clock
    OwnTVIcon.SUN to "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4", // sun
    OwnTVIcon.TREND to "M3 17l6-6 4 4 8-8M15 7h6v6", // trend
    OwnTVIcon.LAYERS to "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5", // layers
    OwnTVIcon.PENCIL to "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4", // pencil
    OwnTVIcon.EYE_OFF to "M3 3l18 18M10.6 5.1A9.8 9.8 0 0 1 12 5c6 0 9.5 7 9.5 7a17 17 0 0 1-2.7 3.6M6.6 6.6A16.7 16.7 0 0 0 2.5 12S6 19 12 19a9.4 9.4 0 0 0 4.4-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2", // eye-off
    OwnTVIcon.MOVE to "M12 3v18M8 7l4-4 4 4M8 17l4 4 4-4", // move
    OwnTVIcon.EXTERNAL to "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5", // external
    OwnTVIcon.TRASH to "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13", // trash
    OwnTVIcon.MULTIVIEW to "M4.5 4H9.5A1.5 1.5 0 0 1 11 5.5V9.5A1.5 1.5 0 0 1 9.5 11H4.5A1.5 1.5 0 0 1 3 9.5V5.5A1.5 1.5 0 0 1 4.5 4zM14.5 4H19.5A1.5 1.5 0 0 1 21 5.5V9.5A1.5 1.5 0 0 1 19.5 11H14.5A1.5 1.5 0 0 1 13 9.5V5.5A1.5 1.5 0 0 1 14.5 4zM4.5 13H9.5A1.5 1.5 0 0 1 11 14.5V18.5A1.5 1.5 0 0 1 9.5 20H4.5A1.5 1.5 0 0 1 3 18.5V14.5A1.5 1.5 0 0 1 4.5 13zM14.5 13H19.5A1.5 1.5 0 0 1 21 14.5V18.5A1.5 1.5 0 0 1 19.5 20H14.5A1.5 1.5 0 0 1 13 18.5V14.5A1.5 1.5 0 0 1 14.5 13z", // mv
    OwnTVIcon.NOW to "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM12 7v5l3.5 2M12 1.5v2", // now
    OwnTVIcon.CALENDAR to "M6 4.5H18A3 3 0 0 1 21 7.5V18A3 3 0 0 1 18 21H6A3 3 0 0 1 3 18V7.5A3 3 0 0 1 6 4.5zM3 10h18M8 2.5v4M16 2.5v4", // calendar
    OwnTVIcon.TILES to "M5.5 3.5H8.5A2 2 0 0 1 10.5 5.5V8.5A2 2 0 0 1 8.5 10.5H5.5A2 2 0 0 1 3.5 8.5V5.5A2 2 0 0 1 5.5 3.5zM15.5 3.5H18.5A2 2 0 0 1 20.5 5.5V8.5A2 2 0 0 1 18.5 10.5H15.5A2 2 0 0 1 13.5 8.5V5.5A2 2 0 0 1 15.5 3.5zM5.5 13.5H8.5A2 2 0 0 1 10.5 15.5V18.5A2 2 0 0 1 8.5 20.5H5.5A2 2 0 0 1 3.5 18.5V15.5A2 2 0 0 1 5.5 13.5zM15.5 13.5H18.5A2 2 0 0 1 20.5 15.5V18.5A2 2 0 0 1 18.5 20.5H15.5A2 2 0 0 1 13.5 18.5V15.5A2 2 0 0 1 15.5 13.5z", // more
    // Audio mode (added to the mockup 30 Sep).
    OwnTVIcon.EQ to "M4 20v-6M8.5 20V8M13 20v-9M17.5 20V5M21 20v-4", // eq
    OwnTVIcon.HEADPHONES to "M3.5 17v-4a8.5 8.5 0 0 1 17 0v4M5.3 14H6.2A1.8 1.8 0 0 1 8 15.8V19.2A1.8 1.8 0 0 1 6.2 21H5.3A1.8 1.8 0 0 1 3.5 19.2V15.8A1.8 1.8 0 0 1 5.3 14zM17.8 14H18.7A1.8 1.8 0 0 1 20.5 15.8V19.2A1.8 1.8 0 0 1 18.7 21H17.8A1.8 1.8 0 0 1 16 19.2V15.8A1.8 1.8 0 0 1 17.8 14z", // headphones
    OwnTVIcon.VOLUME_HIGH to "M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 9a4.2 4.2 0 0 1 0 6M18.3 6.3a8 8 0 0 1 0 11.4", // vol
    OwnTVIcon.EXPAND to "M14 4h6v6M20 4l-6.5 6.5M10 20H4v-6M4 20l6.5-6.5", // expand
    OwnTVIcon.SEEK_BACK to "M4.5 12a7.5 7.5 0 1 0 2.2-5.3L4.5 9M4.5 4.5V9H9", // seek-b
    OwnTVIcon.SEEK_FORWARD to "M19.5 12a7.5 7.5 0 1 1-2.2-5.3L19.5 9M19.5 4.5V9H15", // seek-f
    OwnTVIcon.SKIP_PREVIOUS to "M6 5v14M19 5.5v13L9 12z", // prev
    OwnTVIcon.SKIP_NEXT to "M18 5v14M5 5.5v13L15 12z", // next
    OwnTVIcon.FORWARD to "M13 19l8-7-8-7zM3 19l8-7-8-7z",
    OwnTVIcon.CHEVRON to "M9 18l6-6-6-6",
    OwnTVIcon.CHEVRON_UP to "M6 15l6-6 6 6",
)
