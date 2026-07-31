package tv.own.owntv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.features.profiles.ProfileGate
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.features.setup.Onboarding
import tv.own.owntv.features.shell.OwnTVShell
import tv.own.owntv.features.shell.ShellViewModel
import tv.own.owntv.ui.theme.BlurredBackdrop
import tv.own.owntv.ui.theme.GlassConfig
import tv.own.owntv.ui.theme.LocalBlurredBackdrop
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.UiZoom
import tv.own.owntv.ui.theme.stackBlur
import tv.own.owntv.ui.theme.supportsBackdropBlur
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Logcat tag for the background-image loader (top-level helper outside MainActivity). */
private const val BG_TAG = "BgImage"

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OwnTVHome"

        /** Bounded wait for the startup database probe (see [probeDatabase]). */
        private const val DB_PROBE_TIMEOUT_MS = 5_000L

        /**
         * Hard ceiling on the ST3 splash. A stuck DataStore/profile read must never leave the user
         * staring at a static logo — past this the splash dismisses whatever the state is.
         *
         * Kept short deliberately: the old build drew its (blank) first frame at ~1.1s, so anything
         * beyond ~2s of splash is a perceived-responsiveness regression even though the app is doing
         * strictly less work than before.
         */
        private const val SPLASH_TIMEOUT_MS = 2_000L
    }

    /**
     * ST3: false while the app has no destination to draw yet (active profile id unknown, or the
     * profile query has not returned on a cold start) — exactly the two `when` branches that used to
     * render a blank window. Latched true and never cleared, so the mid-session empty-profile window
     * a backup restore creates (see `everHadProfiles` below) can't bring the splash back.
     */
    @Volatile
    private var contentReady = false

    private val player: tv.own.owntv.player.OwnTVPlayer by inject()
    private val previewEngine: tv.own.owntv.player.LivePreviewEngine by inject()
    private val heroPreviewEngine: tv.own.owntv.player.HeroPreviewEngine by inject()
    // Activity-scoped: the same instance Compose retrieves via koinViewModel() inside setContent.
    private val shellViewModel: ShellViewModel by viewModel()
    private var pendingDeepLink by mutableStateOf<LauncherDeepLink?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = LauncherDeepLink.parse(intent.data)
        Log.d(TAG, "onNewIntent deepLinkHost=${intent.data?.host} deepLinkType=${pendingDeepLink?.javaClass?.simpleName ?: "none"}")
    }

    override fun onStop() {
        super.onStop()
        // Backgrounded (Home / another app), exited, or logged out: stop playback and free the demuxer
        // cache + decoder buffers — holding them while invisible got the process LMK-killed on real TVs.
        if (!isChangingConfigurations) {
            player.onAppBackgrounded()
            // Live runs on ExoPlayer — remember the channel and free the stream (its audio must stop too).
            previewEngine.onAppBackgrounded()
            heroPreviewEngine.stop()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        // Paired with onStop: bring back what was freed while backgrounded (notably the TV screensaver, which
        // kicks in during a long pause) — a VOD restored paused at its position, and a live channel re-tuned
        // to the live edge — so Play resumes instead of sitting on a dead/empty stream. No-op on fresh launch.
        player.onAppForegrounded()
        previewEngine.onAppForegrounded()
        // Staleness-based auto refresh on resume (interval modes only — STARTUP is cold-start only). The
        // ViewModel throttles this internally so a quick toggle doesn't re-run the check.
        shellViewModel.checkAutoRefresh(includeStartup = false)
    }

    /**
     * Probe the database before anything touches it. Room opens lazily, so a failed migration would
     * otherwise surface as a random crash inside whichever coroutine queried first — and with the
     * destructive fallback gone (D1) that crash would repeat on every launch. Opening it here, on a
     * worker thread with a bounded wait, turns that into a deterministic recovery screen.
     *
     * On a healthy install this is a version check on an already-migrated file: milliseconds, and it
     * is work the first query would have done anyway. A timeout is treated as "healthy" so a slow
     * device never sits on a blank window.
     */
    private fun probeDatabase(): String? {
        var error: String? = null
        val worker = Thread {
            runCatching { get<tv.own.owntv.core.database.OwnTVDatabase>().openHelper.readableDatabase }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
        }
        worker.start()
        worker.join(DB_PROBE_TIMEOUT_MS)
        if (error != null) Log.e(TAG, "database probe failed: $error")
        return error
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ST3 — must be installed BEFORE super.onCreate() per the library's contract. It also applies
        // postSplashScreenTheme (Theme.OwnTV), so the activity ends up in the same theme as before.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val splashDeadline = SystemClock.uptimeMillis() + SPLASH_TIMEOUT_MS
        splash.setKeepOnScreenCondition { !contentReady && SystemClock.uptimeMillis() < splashDeadline }
        pendingDeepLink = LauncherDeepLink.parse(intent.data)
        Log.d(TAG, "onCreate deepLinkHost=${intent.data?.host} deepLinkType=${pendingDeepLink?.javaClass?.simpleName ?: "none"}")
        val dbError = probeDatabase()
        if (dbError != null) {
            contentReady = true // the recovery screen IS the destination — don't hold the splash over it
            setContent {
                tv.own.owntv.features.recovery.DatabaseRecoveryScreen(
                    message = dbError,
                    onRetry = { recreate() },
                    onResetData = {
                        // Explicit, twice-confirmed user choice — the only thing that clears a
                        // database SQLite refuses to open. Never automatic (that was D1's bug).
                        runCatching { deleteDatabase(tv.own.owntv.core.database.OwnTVDatabase.NAME) }
                        finishAffinity()
                    },
                )
            }
            return
        }
        Perf.stamp("db-probed") // main thread was blocked here: everything above is pre-composition
        setContent {
            // First composition pass — splits "process start → Compose is running" from
            // "Compose is running → the destination's data arrived".
            // Deliberately a Unit-returning remember: the point is the side effect happening DURING
            // the first composition pass. LaunchedEffect/SideEffect would run after it and measure
            // something else.
            @Suppress("RememberReturnType")
            remember { Perf.stamp("first-composition"); Unit }

            // Hold the screen on while video is actually playing, so the TV screensaver doesn't
            // start mid-channel/episode; released when paused/stopped (then the screensaver is fine).
            val playing by player.isPlaying.collectAsStateWithLifecycle()
            LaunchedEffect(playing) {
                if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            val viewModel: ShellViewModel = koinViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
            val uiZoomPercent by viewModel.uiZoomPercent.collectAsStateWithLifecycle()
            val animationLevel by viewModel.animationLevel.collectAsStateWithLifecycle()
            val bgImagePath by viewModel.bgImagePath.collectAsStateWithLifecycle()
            val glassConfig by viewModel.glassConfig.collectAsStateWithLifecycle()
            val avatarId by viewModel.avatarId.collectAsStateWithLifecycle()
            val profileName by viewModel.profileName.collectAsStateWithLifecycle()
            val sourceSummary by viewModel.sourceSummary.collectAsStateWithLifecycle()
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            val activePlaylistId by viewModel.activePlaylistId.collectAsStateWithLifecycle()
            val weather by viewModel.weather.collectAsStateWithLifecycle()
            val weatherFahrenheit by viewModel.weatherFahrenheit.collectAsStateWithLifecycle()
            val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
            val visibleSections by viewModel.visibleSections.collectAsStateWithLifecycle()
            val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
            val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

            val profilesVm: ProfilesViewModel = koinViewModel()
            val profiles by profilesVm.profiles.collectAsStateWithLifecycle()
            var gatePassed by remember { mutableStateOf(false) }
            var addingProfile by remember { mutableStateOf(false) }
            // A backup restore deletes-then-reinserts profiles, so the list is briefly EMPTY while
            // the shell is showing — without this, the shell unmounts and remounts, dumping the user
            // out of Settings → Backup & Restore mid-restore. Only the cold start waits for the load.
            var everHadProfiles by remember { mutableStateOf(false) }
            LaunchedEffect(profiles) { if (profiles.isNotEmpty()) everHadProfiles = true }
            val shouldShowProfileGate = profiles.size > 1 || profiles.singleOrNull()?.pinHash != null
            // Set by the sidebar's profile-avatar single-click so the "Who's watching?" gate opens even for
            // a single unpinned profile — otherwise switch-profile is a silent no-op with just one profile.
            var switchProfileRequested by remember { mutableStateOf(false) }
            // Back from a user-requested switch returns to the shell (the cold-start gate exits the app).
            BackHandler(enabled = switchProfileRequested && !gatePassed && !addingProfile) {
                switchProfileRequested = false
                gatePassed = true
            }

            // ST3 — dismiss the splash the moment a real destination exists: onboarding, the profile
            // gate or the shell. Mirrors the two blank `when` branches below exactly; `everHadProfiles`
            // keeps a mid-session restore (which briefly empties `profiles`) out of the condition.
            val loadedProfileId = activeProfileId
            LaunchedEffect(loadedProfileId != null) {
                if (loadedProfileId != null) Perf.stamp("profile-id-loaded")
            }
            LaunchedEffect(profiles.isNotEmpty()) {
                if (profiles.isNotEmpty()) Perf.stamp("profiles-loaded")
            }
            val destinationReady = loadedProfileId != null &&
                (loadedProfileId < 0L || profiles.isNotEmpty() || everHadProfiles)
            LaunchedEffect(destinationReady) {
                if (destinationReady && !contentReady) {
                    contentReady = true
                    Perf.stamp("destination-ready") // splash hand-off — the number ST1/ST2 move
                }
            }

            // "Refresh on startup" — re-sync sources once the active profile is known.
            LaunchedEffect(activeProfileId) {
                if ((activeProfileId ?: -1L) >= 0L) viewModel.checkAutoRefresh(includeStartup = true)
            }

            OwnTVTheme(themeMode = themeMode, accent = accent, systemInDarkTheme = isSystemInDarkTheme(), customAccent = customAccent, animationLevel = animationLevel) {
                val base = LocalDensity.current
                // Glass is "always on" (Option B): panels go translucent whenever at least one surface is
                // scoped, independent of a background image. With a photo the glass frosts it; without one,
                // panels are translucent over the solid app background (a subtler layered look). The bg
                // image is a separate, optional layer rendered below the shell only when a path is set.
                val glassActive = glassConfig.enabled
                val effectiveGlass = if (glassActive) glassConfig else GlassConfig(scope = emptySet(), alpha = glassConfig.alpha, blurStrength = glassConfig.blurStrength)
                // Root viewport size in px — the area the background image fills and that the blurred
                // backdrop stands in for. Captured here (top of the tree) so glass panels can map their
                // own on-screen rect into the blurred bitmap's coordinate space.
                var rootSizePx by remember { mutableStateOf(Size.Zero) }
                // Phase 4 — real backdrop blur. Load+blur the background once (cached) when glass is on,
                // a background image is set, blur strength > 0, and the device supports it (API 31+).
                // Otherwise this stays null and panels fall back to Tier-1 translucency.
                val canBlur = glassActive && effectiveGlass.blurStrength > 0f &&
                    bgImagePath.isNotBlank() && supportsBackdropBlur()
                val blurred by produceState<BlurredBackdrop?>(initialValue = null, bgImagePath, canBlur, rootSizePx) {
                    if (!canBlur || rootSizePx.width <= 0f || rootSizePx.height <= 0f) {
                        value = null
                        return@produceState
                    }
                    // Decode + blur on a background dispatcher; never block the main thread.
                    val path = bgImagePath
                    value = produceBlurredBackdrop(path, rootSizePx)
                }
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density * UiZoom.factor(uiZoomPercent), base.fontScale),
                    LocalGlass provides effectiveGlass,
                    LocalBlurredBackdrop provides blurred,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            // Solid base color first (fallback if no image / image fails to load).
                            .background(OwnTVTheme.colors.background)
                            .onGloballyPositioned { rootSizePx = Size(it.size.width.toFloat(), it.size.height.toFloat()) },
                    ) {
                        // Background image sits behind everything when a path is set; otherwise the solid
                        // base color shows through and glass renders over that instead.
                        if (bgImagePath.isNotBlank()) BackgroundLayer(bgImagePath)

                        Box(modifier = Modifier.fillMaxSize()) {
                        val profile = activeProfileId
                        when {
                            profile == null -> Unit // loading
                            // Adding a profile from the gate → onboard the new profile.
                            addingProfile -> Onboarding(
                                firstRun = false,
                                onDone = { addingProfile = false; gatePassed = true },
                                onCancel = { addingProfile = false },
                                modifier = Modifier.fillMaxSize(),
                            )
                            // First run (no profile yet) → full onboarding.
                            profile < 0L -> Onboarding(
                                firstRun = true,
                                onDone = { gatePassed = true },
                                onCancel = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Profiles still loading (≥0 means at least one exists) — avoid a gate/shell flicker.
                            profiles.isEmpty() && !everHadProfiles -> Unit
                            // Run 2+ (or a single locked profile): "Who's watching?" — choose a profile or add one.
                            // Also opens when the sidebar avatar is single-clicked (switchProfileRequested),
                            // which is the only way to switch when there's a single unpinned profile.
                            (shouldShowProfileGate || switchProfileRequested) && !gatePassed -> ProfileGate(
                                onEnter = { gatePassed = true; switchProfileRequested = false },
                                onAddProfile = { addingProfile = true; switchProfileRequested = false },
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> OwnTVShell(
                                selectedSection = selectedSection,
                                visibleSections = visibleSections,
                                onSelectSection = viewModel::selectSection,
                                themeMode = themeMode,
                                uiZoomPercent = uiZoomPercent,
                                onSetZoom = viewModel::setUiZoom,
                                avatarId = avatarId,
                                onSetAvatar = viewModel::setAvatar,
                                profileName = profileName,
                                sourceSummary = sourceSummary,
                                playlists = playlists,
                                activePlaylistId = activePlaylistId,
                                onSelectPlaylist = viewModel::setActivePlaylist,
                                weatherInfo = weather,
                                weatherFahrenheit = weatherFahrenheit,
                                activeProfileId = activeProfileId,
                                pendingDeepLink = pendingDeepLink,
                                onDeepLinkConsumed = { pendingDeepLink = null },
                                isOffline = !isOnline,
                                onExitApp = { finish() },
                                onSwitchProfile = {
                                    // Stop playback and return to the "Who's watching?" gate — no app restart.
                                    player.onAppBackgrounded(); player.discardBackgroundRestore(); previewEngine.stop(); previewEngine.discardBackgroundRestore(); heroPreviewEngine.stop(); gatePassed = false
                                    // Force the gate open even with a single unpinned profile (cold-start gate would
                                    // skip it). Clearing gatePassed above alone is a silent no-op in that case.
                                    switchProfileRequested = true
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        } // end foreground Box (above the background layer)
                    }
                }
            }
        }
    }
}

/**
 * The full-bleed background layer, drawn behind the entire shell. Renders the user's chosen image
 * (an app-private file path set by the Background image picker) via Coil [AsyncImage], plus a soft
 * dark legibility scrim so foreground text stays readable over bright photos. Callers must check
 * [path] is non-blank before composing this — it draws unconditionally otherwise.
 */
@androidx.compose.runtime.Composable
private fun BackgroundLayer(path: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val file = java.io.File(path)
    // Build an explicit ImageRequest with a Uri from the File path. Passing a bare File relies on
    // whatever fetcher happens to be registered; an explicit file:// Uri guarantees the built-in
    // FileFetcher decodes it, and lets us cap the decoded size (a 7.8MB JPEG upscaled to 4K would
    // blow the deliberately tiny memory cache) and log failures for diagnosis.
    val request = remember(path) {
        coil3.request.ImageRequest.Builder(context)
            .data(android.net.Uri.fromFile(file))
            .build()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            // Surface decode errors so a silent load failure is obvious in logcat (BgImage tag).
            onError = { Log.w(BG_TAG, "background image load failed: ${it.result.throwable.message}") },
            onSuccess = { Log.d(BG_TAG, "background image loaded: ${file.absolutePath} (${file.length()} bytes)") },
            modifier = Modifier.fillMaxSize(),
        )
        // Legibility scrim: a soft dark wash over the photo.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f)),
        )
    }
}

/**
 * Phase 4 — build the single shared blurred backdrop for the Liquid Glass frost. Decodes the
 * background file into a downscaled [Bitmap] (≈480px wide — enough detail to frost, tiny in memory),
 * blurs it with [stackBlur], and wraps it in a [BlurredBackdrop] that also carries the root viewport
 * size in px so glass panels can map their on-screen rect into the bitmap's coordinate space.
 *
 * Runs on a background dispatcher (called from `produceState`); returns null on any decode failure so
 * callers fall back to Tier-1 translucency. The aspect ratio is locked to the screen's so the slice
 * drawn under each panel lines up with the sharp photo behind it (Coil renders that with Crop/Center).
 */
private suspend fun produceBlurredBackdrop(path: String, rootSizePx: Size): BlurredBackdrop? =
    withContext(Dispatchers.Default) {
        runCatching {
            val rootW = rootSizePx.width.takeIf { it > 0f } ?: return@runCatching null
            val rootH = rootSizePx.height.takeIf { it > 0f } ?: return@runCatching null
            val aspect = rootW / rootH
            // Blur at a DOWNSCALED size, not full root res. A blurred image upscaled bilinearly is
            // visually identical to a full-res blur, so the small bitmap is strictly better: ~0.7MB vs
            // ~8MB ARGB, ~9× fewer pixels through the CPU blur, and the same radius yields a much
            // deeper frost. (Full-res was a workaround for RGB blocks later proven to be a StackBlur
            // sign-wrap bug, since fixed — the upscale was never the culprit.)
            val targetW = (rootW / 3f).toInt().coerceIn(480, 640)
            val targetH = (targetW / aspect).toInt().coerceAtLeast(2)

            // Decode bounds first, then sample down to roughly the target width.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(BG_TAG, "blur: bounds decode failed for $path")
                return@runCatching null
            }
            val sample = max(1, bounds.outWidth / targetW)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(path, opts) ?: run {
                Log.w(BG_TAG, "blur: decodeFile returned null for $path")
                return@runCatching null
            }

            try {
                // Center-crop to the screen aspect ratio so the bitmap maps 1:1 to the root viewport.
                val curAspect = decoded.width.toFloat() / decoded.height.toFloat()
                var src = decoded
                if (abs(curAspect - aspect) > 0.02f) {
                    val cw: Int; val ch: Int
                    if (curAspect > aspect) { ch = decoded.height; cw = (ch * aspect).toInt() }
                    else { cw = decoded.width; ch = (cw / aspect).toInt() }
                    val cx = ((decoded.width - cw) / 2).coerceAtLeast(0)
                    val cy = ((decoded.height - ch) / 2).coerceAtLeast(0)
                    val cropped = Bitmap.createBitmap(decoded, cx, cy, cw.coerceAtLeast(2), ch.coerceAtLeast(2))
                    if (cropped !== decoded) { decoded.recycle(); src = cropped }
                }
                // Downscale + pre-process in one canvas pass: a mild saturation lift (real glass pops
                // color — the blur half of CSS `backdrop-filter: blur() saturate(…)` alone looks milky)
                // and the SAME 25% black scrim BackgroundLayer paints over the photo, so the frost
                // matches the brightness of the image around the panel instead of reading backlit.
                val scaled = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(scaled).apply {
                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(
                            android.graphics.ColorMatrix().apply { setSaturation(1.35f) },
                        )
                    }
                    drawBitmap(src, null, android.graphics.Rect(0, 0, targetW, targetH), paint)
                    drawColor(0x40000000) // 25% black — keep in lockstep with BackgroundLayer's scrim
                }
                src.recycle()
                stackBlur(scaled, radius = 8).asImageBitmap()
            } catch (t: Throwable) {
                Log.w(BG_TAG, "blur: crop/scale/blur threw ${t.javaClass.simpleName}: ${t.message}")
                decoded.recycle()
                null
            }
        }.onFailure { Log.w(BG_TAG, "blur: produceState failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()?.let { BlurredBackdrop(it, rootSizePx) }
    }
