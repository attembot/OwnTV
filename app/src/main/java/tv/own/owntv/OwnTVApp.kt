package tv.own.owntv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import tv.own.owntv.di.appModule
import tv.own.owntv.di.databaseModule
import tv.own.owntv.di.dataModule
import tv.own.owntv.di.playerModule

class OwnTVApp : Application(), SingletonImageLoader.Factory, androidx.work.Configuration.Provider {

    companion object {
        /** Upper bound for the image disk cache — the value it was fixed at before ST6. */
        private const val MAX_IMAGE_CACHE_BYTES = 250L * 1024 * 1024

        /** Lower bound: below this the cache thrashes and stops saving any downloads. */
        private const val MIN_IMAGE_CACHE_BYTES = 32L * 1024 * 1024
    }

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(tv.own.owntv.core.sync.work.KoinWorkerFactory())
            .build()

    override fun onCreate() {
        Perf.begin() // zero-point for the OwnTVPerf startup timeline (adb logcat -s OwnTVPerf)
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@OwnTVApp)
            modules(appModule, databaseModule, dataModule, playerModule)
        }
        Perf.stamp("koin-started")
        // NOTE: cold start does ZERO heavy DB work. Index + ANALYZE maintenance is piggy-backed onto the
        // operation that actually changes the data — ImportFinalizer.finalize() for normal re-syncs, the
        // deferred content-index worker after a fresh import, the EpgRepository refresh after every EPG
        // sync, and the v5 migration on upgrade — never onto app launch.
        // A previous build ran `ANALYZE movies`/`ANALYZE series` (a full scan of 170k+50k rows) here at EVERY
        // cold start. On a low-end TV box's eMMC + CPU that saturated storage and the single DB connection at
        // the precise moment the Movies/Series grids need their first read — which is exactly why the grids
        // took seconds to appear. It was also redundant: content only ever changes via a sync, and every sync
        // already re-runs ANALYZE in finalize(), so the launch-time re-analyze was pure recurring tax. With it
        // off the launch path, the grids' first indexed query returns in well under a second.
    }

    /**
     * App-wide Coil loader that fetches images through the app's OkHttpClient — so posters/logos get
     * the same player-style User-Agent and connection pooling our IPTV requests use (some panels reject
     * default UAs). Crossfade smooths the grid as images stream in. The memory cache is capped well
     * below Coil's default ~25% — on a 4K TV every megabyte belongs to the video pipeline, not to
     * channel-logo bitmaps.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { GlobalContext.get().get<OkHttpClient>() })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.10).build() }
            // Explicit bounded disk cache: posters/logos survive across sessions instead of
            // re-downloading, capped so a 220k-item catalog can't eat the box's storage.
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(imageDiskCacheBytes())
                    .build()
            }
            // Opaque poster art doesn't need an alpha channel; RGB_565 halves bitmap memory
            // under the deliberately small memory cap.
            .allowRgb565(true)
            .crossfade(true)
            .build()

    /**
     * ST6 — size the poster/logo disk cache against the storage the box actually has: 5% of free
     * space, capped at the previous fixed 250 MB and floored at 32 MB (below that the cache stops
     * being worth having). A 220k-item catalog *will* reach whatever cap it is given, so on a nearly
     * full 8 GB box the fixed number was competing with downloads and app updates for the last
     * gigabyte. One `statfs` call at Application construction, never re-evaluated at runtime — the
     * launch path stays free of directory walks.
     */
    private fun imageDiskCacheBytes(): Long {
        val free = runCatching { cacheDir.usableSpace }.getOrDefault(0L).coerceAtLeast(0L)
        return minOf(MAX_IMAGE_CACHE_BYTES, (free * 0.05).toLong()).coerceAtLeast(MIN_IMAGE_CACHE_BYTES)
    }

    /**
     * Memory-pressure airbag: when the OS warns we're a kill candidate, drop the image cache and
     * shrink the player's stream cache live instead of waiting for the low-memory killer.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // TRIM_MEMORY_RUNNING_LOW is deprecated on API 34+ but still the only signal on TV devices
        // running older Android, so keep honouring it.
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
            runCatching { GlobalContext.getOrNull()?.getOrNull<tv.own.owntv.player.OwnTVPlayer>()?.onTrimMemory() }
        }
    }
}
