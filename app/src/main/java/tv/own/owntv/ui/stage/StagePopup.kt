package tv.own.owntv.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.LocalTvImeMetrics
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * The eyebrow a popup shows when it gives none of its own: a Settings page provides
 * "SETTINGS · PLAYER", so every popup opened from it is labelled as the mockup draws them.
 */
val LocalPopupEyebrow = staticCompositionLocalOf<String?> { null }

/**
 * The Stage popup (P1-06, P4-05, P9-05): the wash over the screen, one glass panel (radius 30, padding
 * 30) with the eyebrow 15/800, the title 38/800 and a muted 18 px text, then [content], then [buttons]
 * at the bottom right. Drawn in mockup pixels; the host applies Popup size and popup font size. While the TV keyboard is up the panel moves to the top and is capped to the room above the
 * keys, so the field being typed in stays visible. Focus never leaves it; Back closes it.
 *
 * [scroll] = false when [content] holds its own LazyColumn (give it `weight(1f, fill = false)`).
 */
@Composable
fun StagePopup(
    onDismiss: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    eyebrow: String? = LocalPopupEyebrow.current,
    /** The eyebrow in accent ("✦ Auto-match EPG · 41 channels matched"), with an optional glyph. */
    eyebrowAccent: Boolean = false,
    eyebrowIcon: OwnTVIcon? = null,
    body: String? = null,
    width: Dp = 880.mpx,
    scroll: Boolean = true,
    dismissOnBackPress: Boolean = true,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    OwnTVPopup(onDismissRequest = onDismiss, dismissOnBackPress = dismissOnBackPress, stageLayout = true) {
        val ime = LocalTvImeMetrics.current
        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color(2, 5, 6).copy(alpha = 0.55f)).trapAllFocusExit().focusGroup(),
            contentAlignment = if (ime.visible) Alignment.TopCenter else Alignment.Center,
        ) {
            val edge = 40.mpx
            // Room above the keyboard, less a gap; the whole height when it is down.
            val room = if (ime.visible) with(density) { ime.keyboardTopPx.toDp() } - edge - 24.mpx else maxHeight - edge * 2
            Column(
                modifier
                    .padding(top = if (ime.visible) edge else 0.mpx)
                    .width(width.coerceAtMost(maxWidth - edge * 2))
                    .heightIn(max = room.coerceAtLeast(120.mpx))
                    .stageGlass(30.mpx, overContent = true)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(30.mpx),
            ) {
                StagePopupHeading(title, eyebrow, eyebrowAccent, eyebrowIcon, body)
                // Text inside written with the old type styles reads as Stage type here.
                androidx.tv.material3.MaterialTheme(typography = stagePopupTypography()) { content() }
                if (buttons != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 24.mpx),
                        horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = buttons,
                    )
                }
            }
        }
    }
}

/**
 * A Stage question with two answers: [title], [body], then Cancel and [confirm] (tinted) at the bottom
 * right. Focus starts on Cancel when [focusCancel] — for anything a mis-press must not trigger.
 */
@Composable
fun StageConfirm(
    title: String,
    body: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    cancel: String = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.common_cancel),
    focusCancel: Boolean = false,
    eyebrow: String? = LocalPopupEyebrow.current,
    width: Dp = 760.mpx,
    /** What Back does, when it is not the same as [cancel] (the resume prompt: Back closes, Start over restarts). */
    onBack: () -> Unit = onCancel,
) {
    val first = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    StagePopup(
        onDismiss = onBack, title = title, body = body, eyebrow = eyebrow, width = width,
        buttons = {
            StageButton(cancel, onClick = onCancel, height = 56.mpx, textSize = 19, modifier = if (focusCancel) Modifier.focusRequester(first) else Modifier)
            StageButton(confirm, onClick = onConfirm, height = 56.mpx, textSize = 19, tinted = true, modifier = if (focusCancel) Modifier else Modifier.focusRequester(first))
        },
    )
}

/** A Stage message with one answer: [title], [body] and a tinted [ok] (OK by default) at the bottom right. */
@Composable
fun StageNotice(
    title: String,
    body: String?,
    onDismiss: () -> Unit,
    ok: String = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.common_ok),
    eyebrow: String? = LocalPopupEyebrow.current,
) {
    val first = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    StagePopup(
        onDismiss = onDismiss, title = title, body = body, eyebrow = eyebrow, width = 760.mpx,
        buttons = { StageButton(ok, onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true, modifier = Modifier.focusRequester(first)) },
    )
}

/** The Material type scale as Stage popups set text (titles 800, body 18 / 15), in the app's font. */
@Composable
private fun stagePopupTypography(): androidx.tv.material3.Typography {
    val t = androidx.tv.material3.MaterialTheme.typography
    return t.copy(
        headlineLarge = stageText(40, 800), headlineMedium = stageText(36, 800), headlineSmall = stageText(32, 800),
        titleLarge = stageText(30, 800), titleMedium = stageText(22, 700), titleSmall = stageText(19, 700),
        bodyLarge = stageText(19, 500), bodyMedium = stageText(18, 400), bodySmall = stageText(15, 500),
        labelLarge = stageText(19, 700), labelMedium = stageText(16, 700), labelSmall = stageText(14, 700),
    )
}

@Composable
private fun StagePopupHeading(title: String?, eyebrow: String?, accent: Boolean, icon: OwnTVIcon?, body: String?) {
    val a = stageAccent
    if (eyebrow != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) OwnTVIcon(icon, if (accent) a.accent else StageColors.Dim, Modifier.size(20.mpx))
            Text(
                eyebrow,
                style = if (accent) stageText(19, 700) else stageText(15, 800, 0.12.em),
                color = if (accent) a.accent else StageColors.Dim,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (title != null) {
        Text(
            title, style = stageText(38, 800, (-0.5).mpxSp), color = StageColors.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (eyebrow != null) 6.mpx else 0.mpx),
        )
    }
    if (body != null) {
        Text(
            body, style = stageText(18, 400).copy(lineHeight = (18 * 1.45f).mpxSp), color = StageColors.Muted,
            modifier = Modifier.padding(top = 10.mpx),
        )
    }
    if (title != null || body != null || eyebrow != null) Box(Modifier.height(22.mpx))
}

/**
 * `.opt`: a popup row — [leading] (a radio, a check or an icon), a 21/600 title over an optional 15 px
 * muted line, then an optional accent [value] with ›; 76 high with a line, 62 without; focused = FX.
 */
@Composable
fun StagePopupOption(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    /** The title in the danger colour ("Delete"). */
    danger: Boolean = false,
    /** The title in accent while idle: the chosen row of a list without radios. */
    chosen: Boolean = false,
    leading: (@Composable RowScope.(focused: Boolean) -> Unit)? = null,
    trailing: (@Composable RowScope.(focused: Boolean) -> Unit)? = null,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        radius = 18.mpx,
        modifier = modifier.fillMaxWidth().heightIn(min = if (subtitle != null) 76.mpx else 62.mpx).alpha(if (enabled) 1f else 0.45f),
        focusStyle = StageFocus.FX,
        enabled = enabled,
        onLongClick = onLongClick,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.mpx, vertical = 10.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke(this, focused)
            Column(Modifier.weight(1f)) {
                Text(
                    title, style = stageText(21, 600),
                    color = when {
                        danger -> StageColors.Danger
                        chosen && !focused -> a.accent
                        else -> StageColors.Text
                    },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, style = stageText(15, 500), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null) {
                Text("$value ›", style = stageText(18, 700), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke(this, focused)
        }
    }
}

/** `.radio`: 24 px, a 2 px dim ring; chosen = a 7 px accent ring (white on the focused row). */
@Composable
fun StagePopupRadio(on: Boolean, focused: Boolean) {
    val a = stageAccent
    Box(
        Modifier.size(24.mpx).drawBehind {
            val r = size.minDimension / 2f
            if (on) drawInnerRing(if (focused) Color.White else a.accent, 7.mpx.toPx(), r)
            else drawInnerRing(StageColors.Dim, 2.mpx.toPx(), r)
        },
    )
}

/** A tick box for multi-select rows: 24 px, a 2 px dim ring; ticked = accent fill with a tick (white fill on the focused row). */
@Composable
fun StagePopupCheck(on: Boolean, focused: Boolean) {
    val a = stageAccent
    val fill = if (focused) Color.White else a.accent
    Box(
        Modifier.size(24.mpx).drawBehind {
            val r = 7.mpx.toPx()
            if (on) drawRoundRect(fill, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            else drawRoundRect(StageColors.Dim, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = androidx.compose.ui.graphics.drawscope.Stroke(2.mpx.toPx()))
        },
        contentAlignment = Alignment.Center,
    ) {
        if (on) OwnTVIcon(OwnTVIcon.CHECK, if (focused) a.accent else a.onAccent, Modifier.size(17.mpx))
    }
}

/** A popup row's icon, 21 px in text colour, as the Navigation popup draws them. */
@Composable
fun StagePopupIcon(icon: OwnTVIcon, tint: Color = StageColors.Text) {
    OwnTVIcon(icon, tint, Modifier.size(21.mpx))
}

/** The 1 px line between a popup's parts. */
@Composable
fun StagePopupDivider() {
    Box(Modifier.padding(vertical = 18.mpx).fillMaxWidth().height(1.mpx).background(Color.White.copy(alpha = 0.10f)))
}

/** A dim group label inside a popup ("PRESETS", "HEX CODE"). Passed in capitals; see [StageGroupLabel]. */
@Composable
fun StagePopupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = stageText(14, 800, 0.12.em), color = StageColors.Dim,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = 6.mpx, bottom = 10.mpx),
    )
}
