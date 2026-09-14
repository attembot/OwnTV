package tv.own.owntv.features.multiview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.live.OpenStreamRegistry
import tv.own.owntv.core.live.StreamPurpose
import tv.own.owntv.core.live.StreamRefusal

/**
 * Verifies the picture-in-picture corner orchestration using a fake engine — no ExoPlayer, no Android, no
 * second decoder (which is exactly the resource we don't want a test to spend). Confirms the corner activates
 * with the right channel, starts muted (so the main keeps the audio), reuses the decoder instead of retuning
 * the same channel, switches on a new channel, frees the decoder on close, and takes exactly one slot in the
 * playlist's connection budget — or shows the refusal when there is none.
 */
class PipControllerTest {

    /** Records control calls without any playback. */
    private class FakePlayback : CornerPlayback {
        override var isErrored: Boolean = false
        val tuneCalls = mutableListOf<Pair<ChannelEntity, Boolean>>()
        val muteCalls = mutableListOf<Boolean>()
        var stopCalls = 0

        override fun tune(channel: ChannelEntity, muted: Boolean) { tuneCalls += channel to muted }
        override fun setMuted(muted: Boolean) { muteCalls += muted }
        override fun stop() { stopCalls++ }
    }

    private fun channel(id: Long, sourceId: Long = 1) =
        ChannelEntity(id = id, sourceId = sourceId, name = "Ch$id", streamUrl = "http://$id")

    private fun source(maxConnections: Int) =
        SourceEntity(id = 1, name = "P", type = SourceType.M3U, url = "http://p", maxConnections = maxConnections)

    private fun controller(playback: FakePlayback = FakePlayback(), registry: OpenStreamRegistry = OpenStreamRegistry()) =
        PipController(playback, registry)

    @Test
    fun initiallyInactive() {
        val pip = controller()
        assertFalse(pip.active.value)
        assertNull(pip.channel.value)
        assertNull(pip.refusal.value)
    }

    @Test
    fun openCorner_activatesAndTunesMutedByDefault() {
        val playback = FakePlayback()
        val pip = controller(playback)
        val ch = channel(1)

        pip.openCorner(ch)

        assertTrue(pip.active.value)
        assertEquals(ch, pip.channel.value)
        assertEquals(1, playback.tuneCalls.size)
        assertEquals(ch, playback.tuneCalls[0].first)
        assertTrue("corner must start muted so the main stream keeps the audio", playback.tuneCalls[0].second)
    }

    @Test
    fun openCorner_sameChannel_doesNotRetune() {
        val playback = FakePlayback()
        val pip = controller(playback)
        val ch = channel(1)

        pip.openCorner(ch)
        pip.openCorner(ch) // same channel → must NOT spin up the decoder again (just re-apply mute)

        assertEquals(1, playback.tuneCalls.size)
        assertEquals(1, playback.muteCalls.size)
        assertTrue(pip.active.value)
    }

    @Test
    fun openCorner_sameChannelAfterError_retunes() {
        val playback = FakePlayback()
        val pip = controller(playback)
        val ch = channel(1)

        pip.openCorner(ch)
        playback.isErrored = true
        pip.openCorner(ch)

        assertEquals(2, playback.tuneCalls.size)
    }

    @Test
    fun openCorner_differentChannel_switchesStream() {
        val playback = FakePlayback()
        val pip = controller(playback)

        pip.openCorner(channel(1))
        pip.openCorner(channel(2))

        assertEquals(2, playback.tuneCalls.size)
        assertEquals(2L, playback.tuneCalls[1].first.id)
        assertEquals(2L, pip.channel.value?.id)
    }

    @Test
    fun closeCorner_deactivatesAndStops() {
        val playback = FakePlayback()
        val pip = controller(playback)
        pip.openCorner(channel(1))

        pip.closeCorner()

        assertFalse(pip.active.value)
        assertNull(pip.channel.value)
        assertEquals(1, playback.stopCalls)
    }

    @Test
    fun corner_holdsExactlyOneConnectionClaim_untilClosed() {
        val registry = OpenStreamRegistry()
        val pip = controller(registry = registry)

        pip.openCorner(channel(1))
        assertEquals(1, registry.openOn(1).watching)
        pip.openCorner(channel(2)) // retune: the old claim is released, not stacked
        assertEquals(1, registry.openOn(1).watching)

        pip.closeCorner()
        assertEquals(0, registry.openOn(1).watching)
    }

    @Test
    fun openCorner_refusedWhenPlaylistHasNoConnectionToSpare() {
        val playback = FakePlayback()
        val registry = OpenStreamRegistry()
        val pip = controller(playback, registry)
        pip.sourceOf = { source(maxConnections = 1) }
        registry.claim(1, StreamPurpose.WATCHING) // something else (a tile, a recording) already has the one stream

        pip.openCorner(channel(1))

        assertTrue("the window opens to say why", pip.active.value)
        assertNotNull(pip.refusal.value)
        assertEquals(StreamRefusal.SINGLE_CONNECTION, pip.refusal.value?.reason)
        assertTrue("nothing was tuned", playback.tuneCalls.isEmpty())
        assertEquals("no claim was taken", 1, registry.openOn(1).watching)

        // Retrying the same channel once the other stream is gone goes through.
        registry.release(registry.claims.value.single())
        pip.openCorner(channel(1))
        assertNull(pip.refusal.value)
        assertEquals(1, playback.tuneCalls.size)
    }
}
