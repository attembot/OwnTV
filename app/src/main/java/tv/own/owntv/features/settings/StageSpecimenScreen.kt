package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.PlaylistMark
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageGroupLabel
import tv.own.owntv.ui.stage.StageMenu
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.stage.StagePlaylistMark
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageSegmented
import tv.own.owntv.ui.stage.StageSheet
import tv.own.owntv.ui.stage.StageSheetItem
import tv.own.owntv.ui.stage.StageStepper
import tv.own.owntv.ui.stage.StageSwitch
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.stage.stageBackground
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.stage.stageSelectedBar
import tv.own.owntv.ui.theme.ALL_GLASS_SURFACES
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * Maintainer-only (behind BuildConfig.DEV_TOOLS, so English by rule): every Stage component on one
 * scrolling page, for the Phase 0 check against crops of the frozen mockup references. Each block
 * names the reference it is compared with. D-pad focus shows each focused state.
 */
@Composable
fun StageSpecimenScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onBack() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    val accent = stageAccent.accent
    val marks = listOf(PlaylistMark.of("IPTV_GOLD", 0), PlaylistMark.of("Junior", 1), PlaylistMark.of("X", 2))
    var seg by remember { mutableIntStateOf(0) }

    Column(
        modifier
            .fillMaxSize()
            .stageBackground(accent)
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(48.mpx),
        verticalArrangement = Arrangement.spacedBy(40.mpx),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(40.mpx), verticalAlignment = Alignment.CenterVertically) {
            Text("Stage specimen", style = stageText(46, 800, (-1).mpxSp), color = StageColors.Text)
            Box(Modifier.size(100.mpx).background(accent))
            Caption("Unit check · this square must capture as 100 × 100 px at UI Zoom 90%")
        }

        Caption("P2-01 · Play (primary when focused) · Trailer · All versions · ⓘ · ♥")
        Row(horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
            StageButton("Play", {}, Modifier.focusRequester(first), icon = OwnTVIcon.PLAY, iconFilled = true)
            StageButton("Trailer", {}, icon = OwnTVIcon.PLAY_CIRCLE)
            StageButton("All versions", {}, icon = OwnTVIcon.LAYERS, trailing = "3")
            StageButton(null, {}, icon = OwnTVIcon.INFO, round = true)
            StageButton(null, {}, icon = OwnTVIcon.FAVORITE, round = true)
        }

        Caption("P1-07 · pills")
        Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
            StagePill("Toy Story 5", {}, icon = OwnTVIcon.PLAY, iconFilled = true, small = "Resume")
            StagePill("All playlists", {}, icon = OwnTVIcon.LAYERS, trailingIcon = OwnTVIcon.CHEVRON_DOWN)
        }

        Caption("P3-01 · tool row: Number | A–Z · Guide view · ⋯")
        Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
            StageSegmented(listOf("Number", "A–Z"), seg, { seg = it }, icons = listOf(OwnTVIcon.SORT, null))
            StageTool("Guide view", {}, icon = OwnTVIcon.GRID)
            StageTool(null, {}, icon = OwnTVIcon.MORE)
            StageTool("Sort:", {}, icon = OwnTVIcon.SORT, value = "Date added", trailingIcon = OwnTVIcon.CHEVRON_DOWN)
            StageTool("Categories", {}, icon = OwnTVIcon.LIST, boxed = true)
        }

        Caption("P3-01 · tags and playlist marks")
        Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
            StageTag("FHD")
            StageTag("DE")
            StageTag("RAW")
            marks.forEach { StagePlaylistMark(it) }
        }

        Caption("P3-01 · channel row (FX when focused)")
        StageRow({}, 84.mpx, Modifier.width(846.mpx)) {
            Text("256", style = stageText(20, 600), color = StageColors.Dim, modifier = Modifier.width(50.mpx))
            Box(Modifier.size(76.mpx, 54.mpx).background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(StageRadii.Plate)))
            Column(Modifier.weight(1f)) {
                Text("Sky Cinema Premieren", style = stageText(22, 700), color = StageColors.Text)
                Text("Wish · 38 min left", style = stageText(17, 500), color = StageColors.Muted)
            }
            StageTag("FHD")
            StagePlaylistMark(marks[0])
        }

        Row(horizontalArrangement = Arrangement.spacedBy(40.mpx)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                Caption("P3-04 · menu, WATCH group, filled item")
                StageMenu(Modifier.width(540.mpx)) {
                    StageMenuHeader("256 · Sky Cinema Premieren", "Sky Cinema · IPTV_GOLD")
                    StageGroupLabel("WATCH")
                    StageMenuItem("Catch-up", {}, icon = OwnTVIcon.REWIND, value = "7 days")
                    StageMenuItem("Record", {}, icon = OwnTVIcon.REC, iconFilled = true)
                    StageMenuItem("Add to Multiview", {}, icon = OwnTVIcon.MULTIVIEW)
                    StageGroupLabel("CHANNEL")
                    StageMenuItem("All playlists", {}, icon = OwnTVIcon.LAYERS, checked = true)
                    StageMenuItem("Record from catch-up", {}, icon = OwnTVIcon.REC, enabled = false)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                Caption("P3-02 · sheet: selected dot, focused item")
                StageSheet("Categories", Modifier.width(450.mpx), trailing = "Live TV") {
                    StageSheetItem("Favourites", {}, icon = OwnTVIcon.FAVORITE, count = "12")
                    StageSheetItem("All channels", {}, icon = OwnTVIcon.LIVE_TV, count = "2,006")
                    StageGroupLabel("GROUPS · DE", sheet = true)
                    StageSheetItem("Sky Cinema", {}, count = "9", selected = true) { _ ->
                        StagePlaylistMark(marks[0])
                    }
                    StageSheetItem(
                        "Kinder", {}, count = "31",
                        tags = { focused -> StageTag("HD", onAccent = if (focused) stageAccent.onAccent else null) },
                    ) { _ -> StagePlaylistMark(marks[1]) }
                }
            }
        }

        Caption("P5-01 · posters idle / focused, rating chip")
        Row(horizontalArrangement = Arrangement.spacedBy(26.mpx)) {
            repeat(4) { i ->
                StagePoster("Toy Story ${i + 2}", {}, 196.mpx, 294.mpx, rating = "7.${i + 5}") {
                    Box(Modifier.fillMaxSize().background(Color(0xFF2A3B44 + i * 0x060606)))
                }
            }
        }

        Caption("P9-01 · settings rows: stepper, switch · selected bar")
        Column(Modifier.width(1000.mpx), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
            SettingsRowSample("UI zoom", "Makes everything larger or smaller.", icon = OwnTVIcon.EXPAND) { StageStepper("90%") }
            SettingsRowSample("Animations", "Sheets and the rail slide in.", icon = OwnTVIcon.MOTION) { StageSwitch(true) }
            SettingsRowSample("Glass Effect", "Frosted chrome.", icon = OwnTVIcon.GLOW) { StageSwitch(false) }
            Box(
                Modifier
                    .width(350.mpx)
                    .padding(vertical = 4.mpx)
                    .stageSelectedBar(accent, 14.mpx)
                    .padding(horizontal = 14.mpx, vertical = 14.mpx),
            ) { Text("Selected category", style = stageText(18.5f, 600), color = accent) }
        }

        Caption("P3-01 vs P3-05 · background and glass, Glass on / off")
        Row(horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
            listOf(true, false).forEach { on ->
                val base = LocalGlass.current
                val config = if (on) GlassConfig(scope = ALL_GLASS_SURFACES) else base.copy(scope = emptySet())
                CompositionLocalProvider(LocalGlass provides config) {
                    Box(Modifier.size(768.mpx, 432.mpx).stageBackground(accent), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(300.mpx, 160.mpx).stageGlass(StageRadii.Sheet))
                    }
                }
            }
        }

    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = stageText(15, 700), color = StageColors.Dim)
}

@Composable
private fun SettingsRowSample(title: String, desc: String, icon: OwnTVIcon, control: @Composable () -> Unit) {
    StageRow({}, 84.mpx, Modifier.fillMaxWidth(), horizontalPadding = 22.mpx) { focused ->
        OwnTVIcon(icon, StageColors.Muted, Modifier.size(24.mpx))
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(21, 700), color = StageColors.Text)
            Text(desc, style = stageText(15.5f, 500), color = if (focused) Color(0xFFD9E6E1) else StageColors.Muted)
        }
        control()
    }
}
