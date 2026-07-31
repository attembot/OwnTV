package tv.own.owntv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.features.settings.data.SubtitleStyle

/**
 * App-drawn subtitles for the direct render path: the decoder owns the video surface there, so mpv
 * can't draw its OSD — instead the player polls the active subtitle line ([OwnTVPlayer.subText])
 * and this overlay renders it Netflix-style. Inactive (empty) in GL mode, where mpv draws its own.
 */
@Composable
fun SubtitleOverlay(player: OwnTVPlayer, modifier: Modifier = Modifier) {
    val text by player.subText.collectAsStateWithLifecycle()
    val settings = koinInject<SettingsRepository>()
    // Subtitle appearance (#96) — every option left on its own "Default" (and everything, while the
    // master toggle is off) resolves to the exact look this overlay has always had: white text on a
    // 45%-black box, centred 56dp above the bottom edge.
    val styleOn by settings.subtitleStyleEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scale by settings.subtitleScale.collectAsStateWithLifecycle(initialValue = SubtitleStyle.SCALE_DEFAULT)
    val colorHex by settings.subtitleColor.collectAsStateWithLifecycle(initialValue = SubtitleStyle.COLOR_DEFAULT)
    val position by settings.subtitlePosition.collectAsStateWithLifecycle(initialValue = SubtitleStyle.Position.DEFAULT)
    val bgOpacity by settings.subtitleBgOpacity.collectAsStateWithLifecycle(initialValue = SubtitleStyle.OPACITY_DEFAULT)
    val line = text ?: return

    val textScale = if (styleOn) scale else SubtitleStyle.SCALE_DEFAULT
    val textColor = if (styleOn && SubtitleStyle.hasColor(colorHex)) Color(SubtitleStyle.colorArgb(colorHex)) else Color.White
    val boxColor = if (styleOn && SubtitleStyle.hasOpacity(bgOpacity)) {
        Color(SubtitleStyle.backgroundArgb(bgOpacity))
    } else {
        Color.Black.copy(alpha = 0.45f)
    }
    val anchor = if (styleOn) position else SubtitleStyle.Position.DEFAULT

    // Same six anchors the SubtitleView path uses, so a channel sits in the same place whether it
    // plays on mpv or ExoPlayer. Default keeps the historical bottom-centre placement.
    val alignment = when {
        anchor.isTop && anchor.isLeft -> Alignment.TopStart
        anchor.isTop && anchor.isRight -> Alignment.TopEnd
        anchor.isTop -> Alignment.TopCenter
        anchor.isLeft -> Alignment.BottomStart
        anchor.isRight -> Alignment.BottomEnd
        else -> Alignment.BottomCenter
    }
    val textAlign = when {
        anchor.isLeft -> TextAlign.Start
        anchor.isRight -> TextAlign.End
        else -> TextAlign.Center
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = if (anchor.isTop) 40.dp else 56.dp),
        contentAlignment = alignment,
    ) {
        Text(
            text = line,
            textAlign = textAlign,
            style = TextStyle(
                color = textColor,
                fontSize = (24 * textScale).sp,
                lineHeight = (30 * textScale).sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 6f),
            ),
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(boxColor)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
