package tv.own.owntv.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.OwnTVPlayer

/** App-wide libmpv player. */
val playerModule = module {
    // Tails own-process logcat for MediaCodec/AudioTrack errors the engines can't expose.
    single { tv.own.owntv.player.PlayerDiagnostics() }
    // Per-item VOD engine pins made with the player's gear toggle (VOD counterpart of ForceMpvStore).
    single { tv.own.owntv.core.player.VodEngineStore(androidContext()) }
    // context, settings, connectivity, okHttpClient (ExoPlayer image-sub handoff), diagnostics, proxyHolder, vodEngineStore
    single { OwnTVPlayer(androidContext(), get(), get(), get(), get(), get(), get()) }
    // ExoPlayer engine for the fast Live preview pane (mpv stays the full/fullscreen player).
    // context, okHttpClient, diagnostics, settings, connectivity (auto-resume when the network returns)
    single { LivePreviewEngine(androidContext(), get(), get(), get(), get()) }
    // Muted ExoPlayer engine for the Home hero preview.
    single { HeroPreviewEngine(androidContext(), get()) }
    // Second, independent ExoPlayer for the picture-in-picture corner (true PiP — a different stream
    // alongside the main one). Separate decoder/surface/audio from the preview engine above so both play.
    single { tv.own.owntv.player.SecondaryLivePlayer(androidContext(), get(), get()) }
    single { tv.own.owntv.features.multiview.PipController(get<tv.own.owntv.player.SecondaryLivePlayer>()) }
    // MultiView (up to 4 live tiles). Each tile is its own constrained ExoPlayer, created on demand and
    // released when MultiView closes. Tile count is device-tiered (low-RAM boxes cap at 2) and each tile
    // caps lower than a PiP corner — so several decoders coexist on weak hardware.
    single {
        tv.own.owntv.features.multiview.MultiViewController(
            maxTiles = tv.own.owntv.features.multiview.MultiViewController.deviceMaxTiles(androidContext()),
            engineFactory = {
                tv.own.owntv.player.SecondaryLivePlayer(
                    androidContext(), get(), get(),
                    maxVideoHeight = tv.own.owntv.features.multiview.MultiViewController.TILE_MAX_HEIGHT,
                )
            },
        )
    }
}
