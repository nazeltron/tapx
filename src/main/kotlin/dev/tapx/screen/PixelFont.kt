package dev.tapx.screen

import java.awt.Color
import java.awt.Graphics2D

/**
 * A hand-made 5x7 pixel font (8 rows including descenders). Drawing text this way means the
 * plugin never depends on fonts installed on the server, and it matches Minecraft's blocky look.
 */
object PixelFont {
    private const val ROWS = 8

    private class Glyph(val width: Int, val bits: Array<BooleanArray>)

    private val glyphs = HashMap<Char, Glyph>()

    private fun def(chars: String, vararg rows: String) {
        // rows: ROWS strings per glyph, several glyphs separated by spaces inside each row
        val parts = rows.map { it.trim().split(Regex("\\s+")) }
        require(parts.size == ROWS && parts.all { it.size == chars.length }) { "Bad glyph rows for '$chars'" }
        chars.forEachIndexed { i, c ->
            require(parts.all { it[i].length == parts[0][i].length }) { "Ragged glyph '$c'" }
            val bits = Array(ROWS) { r -> parts[r][i].map { it == '#' }.toBooleanArray() }
            glyphs[c] = Glyph(bits[0].size, bits)
        }
    }

    init {
        def("ABCDE",
            ".###. ####. .###. ####. #####",
            "#...# #...# #...# #...# #....",
            "#...# #...# #.... #...# #....",
            "##### ####. #.... #...# ####.",
            "#...# #...# #.... #...# #....",
            "#...# #...# #...# #...# #....",
            "#...# ####. .###. ####. #####",
            "..... ..... ..... ..... .....")
        def("FGHIJ",
            "##### .###. #...# ### ..###",
            "#.... #...# #...# .#. ...#.",
            "#.... #.... #...# .#. ...#.",
            "####. #.### ##### .#. ...#.",
            "#.... #...# #...# .#. ...#.",
            "#.... #...# #...# .#. #..#.",
            "#.... .#### #...# ### .##..",
            "..... ..... ..... ... .....")
        def("KLMNO",
            "#...# #.... #...# #...# .###.",
            "#..#. #.... ##.## ##..# #...#",
            "#.#.. #.... #.#.# #.#.# #...#",
            "##... #.... #.#.# #..## #...#",
            "#.#.. #.... #...# #...# #...#",
            "#..#. #.... #...# #...# #...#",
            "#...# ##### #...# #...# .###.",
            "..... ..... ..... ..... .....")
        def("PQRST",
            "####. .###. ####. .#### #####",
            "#...# #...# #...# #.... ..#..",
            "#...# #...# #...# #.... ..#..",
            "####. #...# ####. .###. ..#..",
            "#.... #.#.# #.#.. ....# ..#..",
            "#.... #..#. #..#. ....# ..#..",
            "#.... .##.# #...# ####. ..#..",
            "..... ..... ..... ..... .....")
        def("UVWXY",
            "#...# #...# #...# #...# #...#",
            "#...# #...# #...# #...# #...#",
            "#...# #...# #...# .#.#. .#.#.",
            "#...# #...# #.#.# ..#.. ..#..",
            "#...# #...# #.#.# .#.#. ..#..",
            "#...# .#.#. #.#.# #...# ..#..",
            ".###. ..#.. .#.#. #...# ..#..",
            "..... ..... ..... ..... .....")
        def("Z",
            "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####", ".....")
        def("abcde",
            "..... #.... ..... ....# .....",
            "..... #.... ..... ....# .....",
            ".###. ####. .###. .#### .###.",
            "....# #...# #.... #...# #...#",
            ".#### #...# #.... #...# #####",
            "#...# #...# #...# #...# #....",
            ".#### ####. .###. .#### .###.",
            "..... ..... ..... ..... .....")
        def("fghij",
            "..## ..... #.... # ..#",
            ".#.. ..... #.... . ...",
            "#### .#### ####. # ..#",
            ".#.. #...# #...# # ..#",
            ".#.. #...# #...# # ..#",
            ".#.. .#### #...# # ..#",
            ".#.. ....# #...# # #.#",
            ".... .###. ..... . .#.")
        def("klmno",
            "#... #. ..... ..... .....",
            "#... #. ..... ..... .....",
            "#..# #. ##.#. ####. .###.",
            "#.#. #. #.#.# #...# #...#",
            "##.. #. #.#.# #...# #...#",
            "#.#. #. #.#.# #...# #...#",
            "#..# .# #.#.# #...# .###.",
            ".... .. ..... ..... .....")
        def("pqrst",
            "..... ..... .... ..... .#..",
            "..... ..... .... ..... .#..",
            "####. .#### #.## .#### ####",
            "#...# #...# ##.. #.... .#..",
            "#...# #...# #... .###. .#..",
            "####. .#### #... ....# .#..",
            "#.... ....# #... ####. ..##",
            "#.... ....# .... ..... ....")
        def("uvwxy",
            "..... ..... ..... ..... .....",
            "..... ..... ..... ..... .....",
            "#...# #...# #...# #...# #...#",
            "#...# #...# #.#.# .#.#. #...#",
            "#...# #...# #.#.# ..#.. #...#",
            "#...# .#.#. #.#.# .#.#. .####",
            ".#### ..#.. .#.#. #...# ....#",
            "..... ..... ..... ..... .###.")
        def("z",
            ".....", ".....", "#####", "...#.", "..#..", ".#...", "#####", ".....")
        def("01234",
            ".###. .#. .###. ####. ...#.",
            "#...# ##. #...# ....# ..##.",
            "#..## .#. ....# ....# .#.#.",
            "#.#.# .#. ...#. .###. #..#.",
            "##..# .#. ..#.. ....# #####",
            "#...# .#. .#... ....# ...#.",
            ".###. ### ##### ####. ...#.",
            "..... ... ..... ..... .....")
        def("56789",
            "##### .###. ##### .###. .###.",
            "#.... #.... ....# #...# #...#",
            "####. #.... ...#. #...# #...#",
            "....# ####. ..#.. .###. .####",
            "....# #...# .#... #...# ....#",
            "#...# #...# .#... #...# ....#",
            ".###. .###. .#... .###. .###.",
            "..... ..... ..... ..... .....")
        def("!\"'.,:;",
            "# #.# # . .. . ..",
            "# #.# # . .. . ..",
            "# ... . . .. # .#",
            "# ... . . .. . ..",
            "# ... . . .. . ..",
            ". ... . . .# # .#",
            "# ... . # .# . #.",
            ". ... . . #. . ..")
        def("()[]{}<>",
            "..# #.. ### ### ..## ##.. ...# #...",
            ".#. .#. #.. ..# .#.. ..#. ..#. .#..",
            "#.. ..# #.. ..# .#.. ..#. .#.. ..#.",
            "#.. ..# #.. ..# #... ...# #... ...#",
            "#.. ..# #.. ..# .#.. ..#. .#.. ..#.",
            ".#. .#. #.. ..# .#.. ..#. ..#. .#..",
            "..# #.. ### ### ..## ##.. ...# #...",
            "... ... ... ... .... .... .... ....")
        def("+-*/=_?",
            "..... .... ..... ....# ..... ..... .###.",
            "..#.. .... ..#.. ...#. ..... ..... #...#",
            "..#.. .... #.#.# ...#. ##### ..... ....#",
            "##### #### .###. ..#.. ..... ..... ...#.",
            "..#.. .... #.#.# .#... ##### ..... ..#..",
            "..#.. .... ..#.. .#... ..... ..... .....",
            "..... .... ..... #.... ..... ##### ..#..",
            "..... .... ..... ..... ..... ..... .....")
        def("#$%&@|\\^~",
            ".#.#. ..#.. ##..# .##.. .###. # #.... ..#.. .....",
            ".#.#. .#### ##.#. #..#. #...# # .#... .#.#. .....",
            "##### #.#.. ...#. #.#.. #.### # .#... #...# .#...",
            ".#.#. .###. ..#.. .#... #.#.# # ..#.. ..... #.#.#",
            "##### ..#.# .#... #.#.# #.### # ...#. ..... ...#.",
            ".#.#. ####. .#.## #..#. #.... # ...#. ..... .....",
            ".#.#. ..#.. #..## .##.# .###. # ....# ..... .....",
            "..... ..... ..... ..... ..... . ..... ..... .....")
        // A few symbols used in the UI.
        def("·•",
            ". ...", ". ...", ". .#.", "# ###", ". .#.", ". ...", ". ...", ". ...")
        glyphs[' '] = Glyph(3, Array(ROWS) { BooleanArray(3) })
    }

    const val HEIGHT = 7

    fun width(text: String, scale: Int = 1): Int {
        if (text.isEmpty()) return 0
        var w = 0
        for (c in text) w += ((glyphs[c] ?: glyphs['?']!!).width + 1)
        return (w - 1) * scale
    }

    /** Draw [text] with its top-left corner at (x, y). */
    fun draw(g: Graphics2D, text: String, x: Int, y: Int, color: Color, scale: Int = 1) {
        g.color = color
        var cx = x
        for (c in text) {
            val gl = glyphs[c] ?: glyphs['?']!!
            for (r in 0 until ROWS) {
                val row = gl.bits[r]
                var col = 0
                while (col < gl.width) {
                    if (row[col]) {
                        // merge horizontal runs into one rectangle
                        var end = col
                        while (end + 1 < gl.width && row[end + 1]) end++
                        g.fillRect(cx + col * scale, y + r * scale, (end - col + 1) * scale, scale)
                        col = end + 1
                    } else col++
                }
            }
            cx += (gl.width + 1) * scale
        }
    }

    /** Text with a 1-pixel (times scale) dark outline, like Minecraft's shadowed labels. */
    fun drawOutlined(g: Graphics2D, text: String, x: Int, y: Int, color: Color, scale: Int = 1, outline: Color = Color(20, 18, 28)) {
        if (text.isEmpty()) return
        g.drawImage(sprite(text, color, scale, outline), x - scale, y - scale, null)
    }

    private data class Key(val text: String, val color: Int, val scale: Int, val outline: Int)

    // Outlined labels are drawn often and change rarely, so each one is drawn once into a small image.
    private val sprites = ThreadLocal.withInitial {
        object : java.util.LinkedHashMap<Key, java.awt.image.BufferedImage>(256, 0.75f, true) {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<Key, java.awt.image.BufferedImage>) = size > 1500
        }
    }

    private fun sprite(text: String, color: Color, scale: Int, outline: Color): java.awt.image.BufferedImage =
        sprites.get().getOrPut(Key(text, color.rgb, scale, outline.rgb)) {
            val img = java.awt.image.BufferedImage(width(text, scale) + 2 * scale, (ROWS + 2) * scale, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            for (dx in 0..2) for (dy in 0..2) if (dx != 1 || dy != 1) draw(g, text, dx * scale, dy * scale, outline, scale)
            draw(g, text, scale, scale, color, scale)
            g.dispose()
            img
        }
}
