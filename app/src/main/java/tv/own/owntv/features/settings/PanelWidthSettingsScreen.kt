package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.features.settings.data.PanelSection
import tv.own.owntv.features.settings.data.PanelShares
import tv.own.owntv.features.settings.data.PanelWidthLimits
import tv.own.owntv.features.settings.data.defaultPanelShares
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Panel Width Adjustment — lets the user re-balance the three browse panels (category rail · item
 * list/grid · preview/poster) independently for Live TV, Movies and Series.
 *
 * Each panel holds its share of the screen in percent, and the three must add up to exactly 100%.
 * The dialog shows a running total and refuses to save while it doesn't read 100, so the numbers
 * always mean what they look like they mean.
 *
 * This screen measures itself before its own padding, so `maxWidth` here is exactly the width the
 * browse row gets — that's what the stock (seed) percentages are derived from.
 */
@Composable
fun PanelWidthSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val colors = OwnTVTheme.colors

    var open by remember { mutableStateOf<PanelSection?>(null) }
    val rowFocus = remember { PanelSection.entries.associateWith { FocusRequester() } }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(Unit) { runCatching { rowFocus.getValue(PanelSection.LIVE).requestFocus() } }
    LaunchedEffect(open) {
        if (open != null) return@LaunchedEffect
        dialogReturn?.let { opener ->
            kotlinx.coroutines.delay(60)
            runCatching { opener.requestFocus() }
        }
        dialogReturn = null
    }
    BackHandler { onBack() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowWidth = maxWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .roundedPanel()
                .focusProperties { onEnter = { runCatching { rowFocus.getValue(PanelSection.LIVE).requestFocus() } } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Header(title = "Panel Width Adjustment", onBack = onBack)
            Spacer(Modifier.height(4.dp))
            Text(
                "Set how much of the screen each panel takes — categories, the item list, and the preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            PanelSection.entries.forEach { section ->
                val enabled by vm.panelWidthEnabled.getValue(section).collectAsStateWithLifecycle()
                val shares by vm.panelShares.getValue(section).collectAsStateWithLifecycle()
                val current = shares ?: defaultPanelShares(section, rowWidth)
                Row2(
                    icon = when (section) {
                        PanelSection.LIVE -> OwnTVIcon.LIVE_TV
                        PanelSection.MOVIES -> OwnTVIcon.MOVIES
                        PanelSection.SERIES -> OwnTVIcon.SERIES
                    },
                    title = sectionTitle(section),
                    desc = "Category ${current.category}%  ·  List ${current.list}%  ·  " +
                        "${previewLabel(section)} ${current.preview}%",
                    chip = if (enabled) "Custom" else "Default",
                    primaryChip = enabled,
                    chevron = true,
                    onClick = { dialogReturn = rowFocus.getValue(section); open = section },
                    modifier = Modifier.focusRequester(rowFocus.getValue(section)),
                )
            }

            Spacer(Modifier.height(12.dp))
            GroupLabel("How it works")
            Text(
                "• The three panels share one row, so their percentages must add up to 100%.\n" +
                    "• Widen one panel and you have to take the same amount off another before saving.\n" +
                    "• Reset puts a section back to the standard widths this screen started with.\n" +
                    "• Turning a section off restores its standard layout without losing your values.\n" +
                    "• Movie and series posters re-flow automatically, so a narrower list just shows fewer per row.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        open?.let { section ->
            PanelWidthDialog(section = section, rowWidth = rowWidth, vm = vm, onDismiss = { open = null })
        }
    }
}

private fun sectionTitle(section: PanelSection): String = when (section) {
    PanelSection.LIVE -> "Live TV"
    PanelSection.MOVIES -> "Movies"
    PanelSection.SERIES -> "Series"
}

private fun previewLabel(section: PanelSection): String =
    if (section == PanelSection.LIVE) "Preview" else "Poster"

/**
 * The per-section popup: master toggle, then one −/+ stepper per panel, a running total, and
 * Reset / Okay.
 *
 * Edits are held as a draft and only written on Okay — and Okay refuses while the total isn't 100%,
 * showing the reason in red. That way nothing half-adjusted can ever reach the browse screens, and
 * backing out discards cleanly.
 */
@Composable
private fun PanelWidthDialog(
    section: PanelSection,
    rowWidth: Dp,
    vm: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val savedEnabled by vm.panelWidthEnabled.getValue(section).collectAsStateWithLifecycle()
    val savedShares by vm.panelShares.getValue(section).collectAsStateWithLifecycle()
    val stock = remember(section, rowWidth) { defaultPanelShares(section, rowWidth) }

    var enabled by remember { mutableStateOf(savedEnabled) }
    var draft by remember { mutableStateOf(savedShares ?: stock) }
    // The red note only appears once the user has actually tried to save an unbalanced total.
    var showError by remember { mutableStateOf(false) }
    val valid = draft.isValid

    val toggleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        runCatching { toggleFocus.requestFocus() }
    }
    LaunchedEffect(valid) { if (valid) showError = false }
    BackHandler { onDismiss() }

    tv.own.owntv.ui.theme.PopupFontTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 440.dp, corner = 16.dp, padding = 16.dp)) {
                Text(
                    "${sectionTitle(section)} panel widths",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(12.dp))

                FocusableSurface(
                    onClick = { enabled = !enabled },
                    modifier = Modifier.fillMaxWidth().focusRequester(toggleFocus),
                    shape = RoundedCornerShape(12.dp),
                    surface = GlassSurface.DIALOGS,
                    contentAlignment = Alignment.CenterStart,
                ) { _ ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Customize panel",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (enabled) "On" else "Off",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (enabled) colors.onPrimaryContainer else colors.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (enabled) colors.primaryContainer else colors.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                if (enabled) {
                    Spacer(Modifier.height(10.dp))
                    StepRow("Category panel", draft.category) { draft = draft.copy(category = it) }
                    Spacer(Modifier.height(6.dp))
                    StepRow("List panel", draft.list) { draft = draft.copy(list = it) }
                    Spacer(Modifier.height(6.dp))
                    StepRow("${previewLabel(section)} panel", draft.preview) { draft = draft.copy(preview = it) }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Total size",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${draft.total}%",
                            style = MaterialTheme.typography.titleMedium,
                            // `favorite` is the theme's red — the same one MaterialTheme maps to `error`.
                            color = if (valid) colors.primary else colors.favorite,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            // Same width as a stepper's value + one button, so it lines up under them.
                            modifier = Modifier.padding(end = 48.dp).width(64.dp),
                        )
                    }

                    if (showError) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.favorite.copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Total size is ${draft.total}%. It has to be 100% — please adjust.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.favorite,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton(
                        "Reset",
                        onClick = { draft = stock; showError = false },
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(
                        "Okay",
                        onClick = {
                            // An unbalanced total is only a problem for a section that's actually on.
                            if (enabled && !valid) {
                                showError = true
                            } else {
                                vm.setPanelWidths(section, enabled, draft)
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** One panel's row: label, then − value + in [PanelWidthLimits.STEP] increments. */
@Composable
private fun StepRow(label: String, value: Int, onSet: (Int) -> Unit) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
        // Both buttons stay focusable at the ends of the range: disabling the one holding focus would
        // drop it, and focus is trapped in this dialog — the D-pad would go dead (the bug fixed in
        // StepperDialog). They just stop moving the value and dim instead.
        StepBtn("–", atLimit = value <= PanelWidthLimits.MIN) {
            onSet((value - PanelWidthLimits.STEP).coerceAtLeast(PanelWidthLimits.MIN))
        }
        Text(
            "$value%",
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        StepBtn("+", atLimit = value >= PanelWidthLimits.MAX) {
            onSet((value + PanelWidthLimits.STEP).coerceAtMost(PanelWidthLimits.MAX))
        }
    }
}

/** Square − / + button (matches the one in NumberInputDialog / StepperDialog). */
@Composable
private fun StepBtn(label: String, atLimit: Boolean, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (atLimit) colors.outline else colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
