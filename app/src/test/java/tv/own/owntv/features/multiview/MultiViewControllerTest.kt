package tv.own.owntv.features.multiview

import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.CornerEngine
import tv.own.owntv.player.CornerState
import tv.own.owntv.player.MediaMeta

/**
 * Verifies MultiView orchestration with fake engines — no real decoders. Focuses on the things that matter
 * for correctness and for being light on resources: only the active tile is audible, the device tile cap is
 * honoured, and **every tile is released on exit** (nothing keeps decoding in the background).
 */
class MultiViewControllerTest {

    private class FakeEngine : CornerEngine {
        override val state: StateFlow<CornerState> = MutableStateFlow(CornerState.IDLE)
        override val meta: StateFlow<MediaMeta> = MutableStateFlow(MediaMeta())
        override var currentUrl: String? = null
        // @JvmField: the generated setter would clash with the CornerEngine.setMuted override (same
        // JVM signature) — exposing the raw field skips accessor generation entirely.
        @JvmField var muted = true
        var released = false
        var stops = 0
        override fun play(url: String, meta: MediaMeta, muted: Boolean, userAgent: String?) { currentUrl = url; this.muted = muted }
        override fun setMuted(muted: Boolean) { this.muted = muted }
        override fun stop() { stops++; currentUrl = null }
        override fun release() { released = true; currentUrl = null }
        override fun setSurface(surface: Surface?) {}
    }

    /** A controller whose engines are fakes we can inspect (one fake per slot, created on demand). */
    private fun controller(maxTiles: Int = 4): Pair<MultiViewController, List<FakeEngine>> {
        val created = mutableListOf<FakeEngine>()
        val c = MultiViewController(maxTiles = maxTiles, engineFactory = { FakeEngine().also { created += it } })
        return c to created
    }

    private fun channel(id: Long, url: String) =
        ChannelEntity(id = id, sourceId = 1, name = "Ch$id", streamUrl = url)

    @Test
    fun enter_startsTilesAndOnlyActiveIsAudible() {
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c")))

        assertTrue(c.active.value)
        assertEquals(3, c.tiles.value.size)
        assertEquals(0, c.activeIndex.value)
        // Exactly one engine (the active tile) is unmuted.
        assertEquals(1, engines.count { !it.muted })
        assertFalse("active tile must be audible", engines[0].muted)
    }

    @Test
    fun setActive_movesAudioToTheFocusedTile() {
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b")))

        c.setActive(1)

        assertEquals(1, c.activeIndex.value)
        assertTrue("previously-active tile is muted", engines[0].muted)
        assertFalse("newly-focused tile is audible", engines[1].muted)
    }

    @Test
    fun replaceTile_retunesInPlaceAndLeavesOthersUntouched() {
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b")))
        c.setActive(1) // tile 1 is the audible one

        c.replaceTile(0, channel(9, "z"))

        assertEquals("tile 0 now shows the new channel", listOf(9L, 2L), c.tiles.value.map { it.id })
        assertEquals("tile 0's engine retuned to the new url", "z", engines[0].currentUrl)
        assertEquals("tile 1 was left playing untouched", "b", engines[1].currentUrl)
        assertEquals("audio stayed put: only the active tile is audible", 1, engines.count { !it.muted })
        assertFalse("active tile (1) is still the audible one", engines[1].muted)
    }

    @Test
    fun replaceTile_ignoresOutOfRangeAndSameStream() {
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b")))
        val playsBefore = engines[0].currentUrl

        c.replaceTile(5, channel(9, "z"))      // out of range — no-op
        c.replaceTile(0, channel(1, "a"))      // same stream — no-op

        assertEquals(listOf(1L, 2L), c.tiles.value.map { it.id })
        assertEquals(playsBefore, engines[0].currentUrl)
    }

    @Test
    fun promoteToDominant_setsActiveAndDominantAndSwitchesLayout() {
        val (c, _) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b")))

        c.promoteToDominant(1)

        assertEquals(1, c.activeIndex.value)
        assertEquals(1, c.dominantIndex.value)
        assertEquals(MultiLayout.DOMINANT, c.layout.value)
    }

    @Test
    fun setActive_movesAudioButNotTheDominantTile() {
        // Audio follows D-pad focus; the LARGE tile only changes on an explicit promote — if it followed
        // focus, merely moving across the strip would restructure the layout and drop focus.
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c")))
        c.promoteToDominant(0)

        c.setActive(2)

        assertEquals(2, c.activeIndex.value)
        assertEquals("dominant tile unchanged by focus moves", 0, c.dominantIndex.value)
        assertFalse("focused tile is the audible one", engines[2].muted)
    }

    @Test
    fun removeTile_clampsDominantIndex() {
        val (c, _) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c")))
        c.promoteToDominant(2)

        c.removeTile(2)

        assertEquals(2, c.tiles.value.size)
        assertEquals("dominant clamped into the remaining range", 1, c.dominantIndex.value)
    }

    @Test
    fun toggleLayout_flipsGridAndDominant() {
        val (c, _) = controller()
        c.enter(listOf(channel(1, "a")))
        assertEquals(MultiLayout.GRID, c.layout.value)
        c.toggleLayout(); assertEquals(MultiLayout.DOMINANT, c.layout.value)
        c.toggleLayout(); assertEquals(MultiLayout.GRID, c.layout.value)
    }

    @Test
    fun deviceCap_isHonoured_onEnterAndAdd() {
        val (c, _) = controller(maxTiles = 2)
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c"))) // 3 offered, device allows 2
        assertEquals(2, c.tiles.value.size)
        assertFalse(c.canAddTile)
        c.addTile(channel(4, "d")) // must be refused
        assertEquals(2, c.tiles.value.size)
    }

    @Test
    fun exit_releasesEveryTile_soNothingStreamsInBackground() {
        val (c, engines) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c")))

        c.exit()

        assertFalse(c.active.value)
        assertTrue(c.tiles.value.isEmpty())
        assertEquals("all three tile engines must be released", 3, engines.count { it.released })
    }

    @Test
    fun removeTile_freesASlotAndKeepsOneAudible() {
        val (c, _) = controller()
        c.enter(listOf(channel(1, "a"), channel(2, "b"), channel(3, "c")))

        c.removeTile(1)

        assertEquals(2, c.tiles.value.size)
        assertTrue(c.canAddTile) // back under the cap
        assertTrue(c.active.value)
    }
}
