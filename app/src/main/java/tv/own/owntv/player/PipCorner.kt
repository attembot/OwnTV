@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package tv.own.owntv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.multiview.CornerPlayback
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * The corner's engine is one of upstream's [LivePreviewEngine]s — the same class behind the Live
 * preview pane and every Multiview tile — capped like a background tile. [tuner] is wired by the shell
 * to `LiveViewModel.tuneTile`, so a Stalker channel, a channel with its own headers or DRM, and a
 * playlist with a custom User-Agent all play in the corner exactly as they do full screen.
 */
class LiveCornerPlayback(val engine: LivePreviewEngine) : CornerPlayback {
    var tuner: (LivePreviewEngine, ChannelEntity, Boolean) -> Unit = { _, _, _ -> }

    init {
        engine.setMuted(true)
        engine.setMaxVideoHeight(LiveEnginePool.BACKGROUND_TILE_HEIGHT)
    }

    override val isErrored: Boolean get() = engine.state.value == LivePreviewEngine.State.ERROR
    override fun tune(channel: ChannelEntity, muted: Boolean) = tuner(engine, channel, muted)
    override fun setMuted(muted: Boolean) = engine.setMuted(muted)
    override fun stop() = engine.stop()
}

/**
 * The picture-in-picture corner window: the second stream's video in a rounded, bordered box with a title
 * and a loading spinner, plus — when [showControls] is set — a focusable D-pad control row (audio · swap ·
 * close). During the browse UI the corner carries its own controls (navigate to it with the remote, like
 * the docked mini-player); while the main player is full-screen the player HUD drives these instead, so the
 * corner is rendered video-only ([showControls] = false).
 *
 * The video is drawn through a TextureView (see [ExoPreviewSurface]): a second SurfaceView would ask the
 * television for a second hardware video plane, which many boxes do not have — sound with no picture is
 * exactly the Multiview symptom upstream fixed the same way.
 */
@Composable
fun PipCornerWindow(
    engine: LivePreviewEngine,
    /** The corner channel's name; shown briefly whenever it changes. */
    title: String?,
    /** The connection-budget sentence when the playlist had no stream to spare; null while playing. */
    refusal: String?,
    showControls: Boolean,
    audioOnCorner: Boolean,
    onToggleAudio: () -> Unit,
    onBrowse: () -> Unit,
    onMove: () -> Unit,
    onGrow: () -> Unit,
    onShrink: () -> Unit,
    onSwap: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val loading = refusal == null && state == LivePreviewEngine.State.LOADING

    // The channel name shows briefly when the corner opens or changes channel, then fades to just video.
    var titleVisible by remember { mutableStateOf(true) }
    LaunchedEffect(title) { titleVisible = true; delay(4000); titleVisible = false }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(
                width = if (audioOnCorner) 2.dp else 1.dp,
                color = if (audioOnCorner) tv.own.owntv.ui.theme.OwnTVTheme.colors.primary else Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        when {
            refusal != null -> Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                Text(refusal, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center)
            }
            state == LivePreviewEngine.State.ERROR -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.fork_corner_playback_failed), style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            else -> ExoPreviewSurface(engine = engine, modifier = Modifier.fillMaxSize(), keepAwake = true, useTextureView = true)
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 22)
            }
        }

        // Title strip (top) — channel name on a scrim, with the PiP marker and an audio badge (icons, not
        // emoji, so they render consistently in the TV system font and match the rest of the app's chrome).
        // Auto-hides a few seconds after opening / changing channel so it doesn't sit over the video forever.
        AnimatedVisibility(
            visible = titleVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OwnTVIcon(OwnTVIcon.PIP, tint = Color.White.copy(alpha = 0.85f), filled = true, modifier = Modifier.size(14.dp))
                if (audioOnCorner) {
                    OwnTVIcon(OwnTVIcon.VOLUME_HIGH, tint = tv.own.owntv.ui.theme.OwnTVTheme.colors.primary, filled = true, modifier = Modifier.size(14.dp))
                }
                Text(
                    title.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }

        if (showControls) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(8.dp).focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PipBtn(OwnTVIcon.SWAP, onClick = onToggleAudio)   // move the sound between the two windows
                Spacer(Modifier.weight(1f))
                PipBtn(OwnTVIcon.PLAYLIST, onClick = onBrowse) // retune the corner stream from the playlist
                PipBtn(OwnTVIcon.MOVE, onClick = onMove)       // cycle the corner through the four screen corners
                PipBtn(OwnTVIcon.ADD, onClick = onGrow)        // window +10% (capped)
                PipBtn(OwnTVIcon.MINUS, onClick = onShrink)    // window −10% (never below the base size)
                PipBtn(OwnTVIcon.FULLSCREEN, onClick = onSwap)  // swap the corner stream into the main window
                PipBtn(OwnTVIcon.CLOSE, onClick = onClose)
            }
        }
    }
}

@Composable
private fun PipBtn(icon: OwnTVIcon, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        focusedScale = 1.15f,
        focusedContainerColor = tv.own.owntv.ui.theme.OwnTVTheme.colors.primary,
        unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
        selectedContainerColor = Color.White.copy(alpha = 0.14f),
        contentAlignment = Alignment.Center,
    ) { focused ->
        OwnTVIcon(
            icon,
            tint = if (focused) tv.own.owntv.ui.theme.OwnTVTheme.colors.onPrimary else Color.White,
            filled = true,
            modifier = Modifier.size(19.dp),
        )
    }
}
