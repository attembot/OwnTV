package tv.own.owntv.ui.stage

import androidx.compose.ui.graphics.Color
import java.util.Locale

/** The short outlined mark that says which playlist a row comes from ("GOLD"), and its colour. */
data class PlaylistMark(val text: String, val color: Color) {
    companion object {
        /**
         * Decision D4: the first four letters of the playlist's name, upper-case, after dropping a
         * leading "IPTV" or "TV" word and every separator — IPTV_GOLD → GOLD, Junior → JUNI, X → X. A
         * prefix only counts as a whole word, so "Tvoli" keeps its T; a name that is nothing but the
         * prefix keeps it.
         *
         * The colour comes from the mockup's palette by [index] (the playlist's position), so marks
         * stay distinct and never change while the playlists do not.
         */
        fun of(name: String, index: Int): PlaylistMark = PlaylistMark(textOf(name), Palette[Math.floorMod(index, Palette.size)])

        internal fun textOf(name: String): String {
            val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            val kept = words.dropWhile { it.equals("IPTV", ignoreCase = true) || it.equals("TV", ignoreCase = true) }
                .ifEmpty { words }
            val letters = kept.joinToString("").uppercase(Locale.ROOT)
            val end = if (letters.codePointCount(0, letters.length) <= 4) letters.length else letters.offsetByCodePoints(0, 4)
            return letters.substring(0, end)
        }

        /** GOLD, JR and X are the mockup's; the rest continue its spacing round the colour wheel. */
        private val Palette = listOf(
            Color(0xFFE8B64A),
            Color(0xFF6FB0FF),
            Color(0xFFFF8A7A),
            Color(0xFF7BE3A4),
            Color(0xFFC792FF),
            Color(0xFF52DBC8),
        )
    }
}
