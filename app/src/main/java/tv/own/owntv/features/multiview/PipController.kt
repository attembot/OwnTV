package tv.own.owntv.features.multiview

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.live.OpenStreamRegistry
import tv.own.owntv.core.live.StreamGrant
import tv.own.owntv.core.live.StreamPurpose
import tv.own.owntv.core.live.connectionBudget

/**
 * What the picture-in-picture corner needs from its engine, and nothing more, so [PipController] can be
 * unit-tested with a fake (no ExoPlayer, no Android, no second decoder). The production implementation
 * is [tv.own.owntv.player.LiveCornerPlayback], a thin adapter over upstream's `LivePreviewEngine`.
 */
interface CornerPlayback {
    /** The engine has given up on the current stream (so re-picking the same channel must retry). */
    val isErrored: Boolean

    /**
     * Tune [channel]. The implementation is expected to route through `LiveViewModel.tuneTile`, which
     * resolves a Stalker `cmd` to a link, applies the playlist's User-Agent, the channel's own HTTP
     * headers and DRM, and the pre-buffer / latency overrides. That is the whole point of the adapter:
     * the corner plays exactly what the main window would.
     */
    fun tune(channel: ChannelEntity, muted: Boolean)
    fun setMuted(muted: Boolean)

    /** Stop playback and free the decoder/connection, keeping the engine for the next corner channel. */
    fun stop()
}

/**
 * App-wide state for the **picture-in-picture corner window** — the second simultaneous stream. Tracks
 * which channel the corner is on, so the shell can mount a persistent corner overlay that survives moving
 * between the browse UI and full-screen, and holds the corner's claim in the provider connection budget.
 *
 * This is intentionally thin: it starts/stops the corner stream and remembers the corner channel. Audio
 * arbitration (only one window audible at a time) and the swap-with-main gesture are orchestrated by the
 * shell, which is the only place that knows what the *main* player is currently doing (mpv vs ExoPlayer).
 */
class PipController(
    private val playback: CornerPlayback,
    private val registry: OpenStreamRegistry,
) {
    /** The playlist a channel came from, for the connection budget. The shell wires this to the live VM. */
    var sourceOf: (ChannelEntity) -> SourceEntity? = { null }

    private val _active = MutableStateFlow(false)
    /** True while a corner window is on screen (a second stream is running, or a refusal is being shown). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _channel = MutableStateFlow<ChannelEntity?>(null)
    /** The channel in the corner (for its title, and to swap it into the main window). */
    val channel: StateFlow<ChannelEntity?> = _channel.asStateFlow()

    private val _refusal = MutableStateFlow<StreamGrant.Refused?>(null)
    /**
     * Set when the playlist had no connection to spare for the corner (its tiles, recordings and this
     * corner share one budget per playlist). The window shows the sentence instead of a spinner that
     * would have ended in a provider error.
     */
    val refusal: StateFlow<StreamGrant.Refused?> = _refusal.asStateFlow()

    private var claim: OpenStreamRegistry.Claim? = null

    /** Open [channel] in the corner (or switch the corner to it). Starts muted so the main stream keeps the
     *  sound; the user hands audio to the corner explicitly. No-op-safe to call repeatedly with the same one
     *  — except after an error or a refusal, where the same channel retries. */
    fun openCorner(channel: ChannelEntity, muted: Boolean = true) {
        val same = _channel.value?.id == channel.id && _refusal.value == null && !playback.isErrored
        _channel.value = channel
        _active.value = true
        if (same) {
            playback.setMuted(muted)
            return
        }
        releaseClaim()
        when (val grant = connectionBudget(sourceOf(channel), registry.openOn(channel.sourceId), StreamPurpose.WATCHING)) {
            is StreamGrant.Refused -> {
                _refusal.value = grant
                playback.stop()
                return
            }
            StreamGrant.Allowed -> Unit
        }
        _refusal.value = null
        claim = registry.claim(channel.sourceId, StreamPurpose.WATCHING)
        playback.tune(channel, muted)
    }

    /** Close the corner window and free its decoder/connection. */
    fun closeCorner() {
        _active.value = false
        _channel.value = null
        _refusal.value = null
        releaseClaim()
        playback.stop()
    }

    private fun releaseClaim() {
        claim?.let { registry.release(it) }
        claim = null
    }
}
