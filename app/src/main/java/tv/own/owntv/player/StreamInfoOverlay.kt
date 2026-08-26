package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Non-interactive technical readout for the current stream (codec, resolution, HDR, bitrate, decoder, audio,
 * buffer, source). Reads [PlaybackEngine.streamInfo] live — re-polled once a second so bitrate/buffer update
 * — and works on whichever engine is playing (mpv or ExoPlayer). Toggled from the player's info button.
 */
@Composable
fun StreamInfoOverlay(player: PlaybackEngine, modifier: Modifier = Modifier) {
    // Starts empty and is filled by the effect below: the mpv read now happens on the player's own
    // executor, so there is nothing to read synchronously during composition (A-F2).
    var rows by remember { mutableStateOf(emptyList<StreamInfoRow>()) }
    // Re-read on the player's shared cosmetic beat rather than a timer of its own, so having the overlay
    // up alongside the position readout costs one wake-up rather than two (see [PlayerHeartbeat]).
    LaunchedEffect(player) {
        PlayerHeartbeat.beat.collect { rows = player.streamInfo() }
    }
    if (rows.isEmpty()) return
    val colors = OwnTVTheme.colors

    Column(
        modifier = modifier
            .widthIn(min = 300.dp, max = 460.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.player_stream_info),
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(2.dp))
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text(
                    stringResource(row.label.resourceId),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(0.38f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.value.displayText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(0.62f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val StreamInfoLabel.resourceId: Int
    get() = when (this) {
        StreamInfoLabel.ENGINE -> R.string.player_stream_engine
        StreamInfoLabel.FORMAT -> R.string.player_stream_format
        StreamInfoLabel.SOURCE -> R.string.player_stream_source
        StreamInfoLabel.VIDEO -> R.string.player_stream_video
        StreamInfoLabel.HDR -> R.string.player_stream_hdr
        StreamInfoLabel.BITRATE -> R.string.player_stream_bitrate
        StreamInfoLabel.DECODER -> R.string.player_stream_decoder
        StreamInfoLabel.AUDIO -> R.string.player_stream_audio
        StreamInfoLabel.AUDIO_OUTPUT -> R.string.player_stream_audio_output
        StreamInfoLabel.BUFFER -> R.string.player_stream_buffer
        StreamInfoLabel.LIVE_BUFFER -> R.string.player_stream_live_buffer
    }

@Composable
private fun StreamInfoValue.displayText(): String {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: java.util.Locale.US
    fun number(value: Double): String = java.text.NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(value)
    return when (this) {
        is StreamInfoValue.Engine -> when (engine) {
            StreamEngine.MPV -> stringResource(R.string.settings_player_mpv)
            StreamEngine.EXOPLAYER -> when (mode) {
                StreamEngineMode.PREFERRED -> stringResource(R.string.player_stream_engine_exo_preferred)
                StreamEngineMode.FALLBACK -> stringResource(R.string.player_stream_engine_exo_fallback)
                StreamEngineMode.IMAGE_SUBTITLE_HANDOFF -> stringResource(R.string.player_stream_engine_exo_image_subtitle)
                StreamEngineMode.NORMAL -> stringResource(R.string.settings_player_exoplayer)
            }
        }
        is StreamInfoValue.Format -> name
        is StreamInfoValue.Source -> url
        is StreamInfoValue.Video -> listOfNotNull(
            codec,
            if (width != null && height != null) "${width}×${height}" else null,
            fps?.let { stringResource(R.string.player_stream_fps, it) },
            bitDepth?.let { stringResource(R.string.player_stream_bit_depth, it) },
        ).joinToString(stringResource(R.string.player_metadata_separator))
        is StreamInfoValue.Hdr -> when (mode) {
            StreamHdrMode.HDR10_PQ -> stringResource(R.string.player_stream_hdr10_pq)
            StreamHdrMode.HLG -> stringResource(R.string.player_stream_hlg)
            StreamHdrMode.SDR -> stringResource(R.string.player_stream_sdr)
        }
        is StreamInfoValue.Bitrate -> stringResource(R.string.player_stream_mbps, number(bitsPerSecond / 1_000_000.0))
        is StreamInfoValue.Decoder -> buildList {
            when (kind) {
                DecoderKind.HARDWARE -> {
                    name?.takeIf { it.isNotBlank() }?.let(::add)
                    add(stringResource(R.string.player_decoder_hardware))
                }
                DecoderKind.SOFTWARE -> {
                    name?.takeIf { it.isNotBlank() }?.let(::add)
                    add(stringResource(R.string.player_decoder_software))
                    if (gpu) add(stringResource(R.string.player_decoder_gpu))
                }
                DecoderKind.NAMED -> {
                    name?.takeIf { it.isNotBlank() }?.let(::add)
                    if (hardware) add(stringResource(R.string.player_decoder_hardware))
                    else if (software) add(stringResource(R.string.player_decoder_software))
                }
            }
            if (direct) add(stringResource(R.string.player_decoder_direct))
        }.joinToString(stringResource(R.string.player_metadata_separator))
        is StreamInfoValue.Audio -> listOfNotNull(
            codec,
            channelLabel(channelCount),
            sampleRateHz?.let { stringResource(R.string.player_stream_khz, number(it / 1000.0)) },
            bitsPerSecond?.takeIf { it > 0 }?.let { stringResource(R.string.player_stream_kbps, number(it / 1000.0)) },
        ).joinToString(stringResource(R.string.player_metadata_separator))
        is StreamInfoValue.AudioOutput -> buildList {
            add(
                when (kind) {
                    AudioOutputKind.PASSTHROUGH -> stringResource(R.string.player_stream_audio_passthrough)
                    AudioOutputKind.DECODED_IN_APP -> stringResource(R.string.player_stream_audio_decoded_in_app)
                    AudioOutputKind.PCM -> channelCount?.let { count ->
                        stringResource(R.string.player_stream_audio_pcm, channelLabel(count) ?: count.toString())
                    } ?: stringResource(R.string.player_stream_audio_decoded_in_app)
                },
            )
            add(
                stringResource(
                    if (multichannelAllowed) R.string.player_stream_multichannel_allowed
                    else R.string.settings_surround_stereo,
                ),
            )
            fallbackReason?.let { add(stringResource(R.string.player_stream_fell_back, it)) }
        }.joinToString(stringResource(R.string.player_metadata_separator))
        is StreamInfoValue.Buffer -> listOfNotNull(
            bufferedMs?.let { stringResource(R.string.player_stream_seconds, number(it / 1000.0)) },
            droppedFrames?.let { pluralStringResource(R.plurals.player_stream_dropped_frames, it.toInt(), it) },
        ).joinToString(stringResource(R.string.player_metadata_separator))
        is StreamInfoValue.LiveBuffer -> buildList {
            add(
                if (prerollEnabled) {
                    stringResource(R.string.player_stream_preroll_video, number(prerollSeconds ?: 0.0))
                } else {
                    stringResource(R.string.player_stream_preroll_off)
                },
            )
            depthSeconds?.let { add(stringResource(R.string.player_stream_depth, number(it))) }
            readaheadSeconds?.let { add(stringResource(R.string.player_stream_readahead, number(it))) }
            if (playlistOverride) add(stringResource(R.string.player_stream_playlist_override))
        }.joinToString(stringResource(R.string.player_metadata_separator))
        is StreamInfoValue.Raw -> text
    }
}

@Composable
private fun channelLabel(count: Int?): String? = when (count) {
    null -> null
    1 -> stringResource(R.string.player_audio_mono)
    2 -> stringResource(R.string.player_audio_stereo)
    6 -> "5.1"
    8 -> "7.1"
    else -> pluralStringResource(R.plurals.player_audio_channels, count, count)
}
