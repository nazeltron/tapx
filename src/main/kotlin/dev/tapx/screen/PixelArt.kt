package dev.tapx.screen

import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Turns the vector-drawn art into chunky pixel art (the look of the reference sprites): the image
 * is rebuilt on a grid of [cell]-pixel blocks, each block taking the colour that covers most of it
 * (dark outlines win, so line art survives), colours get more saturated and are snapped to exact
 * map colours (solid on the map, no dither speckle), and the silhouette gets a dark one-block
 * outline. Done once when a sprite is cached, so it costs nothing per frame.
 */
object PixelArt {
    /** The outline colour around silhouettes. */
    val OUTLINE = 0x1a1222

    /** Saturation boost used everywhere ("more vibrant"). */
    const val VIVID = 1.35

    /** [vivid] at the default strength for every colour (6 bits per channel), built once. */
    private val vividLut: IntArray by lazy {
        IntArray(1 shl 18) { k ->
            val r = ((k shr 12) and 63) * 255 / 63; val g = ((k shr 6) and 63) * 255 / 63; val b = (k and 63) * 255 / 63
            vividNow((r shl 16) or (g shl 8) or b, VIVID)
        }
    }

    /** More saturated [rgb] (pushed away from its own grey), then snapped to a map colour. */
    fun vivid(rgb: Int, amount: Double = VIVID): Int =
        if (amount == VIVID) vividLut[(((rgb shr 18) and 63) shl 12) or (((rgb shr 10) and 63) shl 6) or ((rgb shr 2) and 63)]
        else vividNow(rgb, amount)

    private fun vividNow(rgb: Int, amount: Double): Int {
        val r = (rgb shr 16) and 255; val g = (rgb shr 8) and 255; val b = rgb and 255
        val l = 0.299 * r + 0.587 * g + 0.114 * b
        fun f(c: Int) = (l + (c - l) * amount).toInt().coerceIn(0, 255)
        return Palette.snap((f(r) shl 16) or (f(g) shl 8) or f(b))
    }

    fun vivid(c: Color): Color = Color(vivid(c.rgb and 0xffffff))

    /** The pixel array of an INT_ARGB image (no per-pixel colour-model calls, unlike getRGB/setRGB). */
    private fun data(img: BufferedImage): IntArray = (img.raster.dataBuffer as java.awt.image.DataBufferInt).data

    /** [img]'s pixels as ARGB ints, fast for the usual INT_ARGB / INT_RGB images. */
    private fun argb(img: BufferedImage): IntArray = when (img.type) {
        BufferedImage.TYPE_INT_ARGB -> data(img)
        BufferedImage.TYPE_INT_RGB -> data(img).let { d -> IntArray(d.size) { d[it] or (0xff shl 24) } }
        else -> img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
    }

    private fun lum(rgb: Int) = (299 * ((rgb shr 16) and 255) + 587 * ((rgb shr 8) and 255) + 114 * (rgb and 255)) / 1000

    /**
     * The pixel-art version of [src] (same size). [cell] is the block size in device pixels;
     * [outline] adds the dark border around the silhouette.
     */
    fun pixelize(src: BufferedImage, cell: Int, outline: Boolean = true): BufferedImage {
        val w = src.width; val h = src.height
        val cw = (w + cell - 1) / cell; val ch = (h + cell - 1) / cell
        val px = argb(src)
        val cells = IntArray(cw * ch) // ARGB; alpha 0 = empty
        for (cy in 0 until ch) for (cx in 0 until cw) {
            var n = 0; var nOpaque = 0; var nDark = 0; var nSoft = 0
            var r = 0L; var g = 0L; var b = 0L; var dr = 0L; var dg = 0L; var db = 0L; var sa = 0L; var sr = 0L; var sg = 0L; var sb = 0L
            for (y in cy * cell until minOf(h, cy * cell + cell)) for (x in cx * cell until minOf(w, cx * cell + cell)) {
                n++
                val p = px[y * w + x]
                val a = p ushr 24
                if (a >= 128) {
                    nOpaque++
                    if (lum(p) < 58) { nDark++; dr += (p shr 16) and 255; dg += (p shr 8) and 255; db += p and 255 }
                    else { r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255 }
                } else if (a >= 24) {
                    nSoft++; sa += a; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255
                }
            }
            val i = cy * cw + cx
            cells[i] = when {
                nOpaque * 20 >= n * 9 -> { // mostly solid
                    val rgb = if (nDark * 3 >= nOpaque || nDark == nOpaque) {
                        ((dr / nDark).toInt() shl 16) or ((dg / nDark).toInt() shl 8) or (db / nDark).toInt()
                    } else {
                        val m = nOpaque - nDark
                        ((r / m).toInt() shl 16) or ((g / m).toInt() shl 8) or (b / m).toInt()
                    }
                    (0xff shl 24) or vivid(rgb)
                }
                (nSoft + nOpaque) * 20 >= n * 9 && nSoft > 0 -> { // a soft shadow or glow: keep it see-through
                    val a = ((sa / nSoft) / 64 * 64 + 48).toInt().coerceIn(48, 176)
                    (a shl 24) or (((sr / nSoft).toInt() shl 16) or ((sg / nSoft).toInt() shl 8) or (sb / nSoft).toInt())
                }
                else -> 0
            }
        }
        if (outline) addOutline(cells, cw, ch)
        // one pixel per block when blocks are whole layout units (the usual case): far less memory
        if (cell % Res.k == 0) {
            val img = UnitImage(cw, ch, cell / Res.k)
            System.arraycopy(cells, 0, data(img), 0, cells.size)
            return img
        }
        return finish(cells, cw, ch, cell, w, h)
    }

    /**
     * Pixel art drawn directly at block resolution (fast enough for every animation frame):
     * [draw] paints in layout units onto a canvas of [wU] x [hU] units where one pixel is [cellU]
     * units, with anti-aliasing off. Returns the image at block resolution (one pixel per block):
     * draw it at [wU] x [hU] layout units and the default nearest-neighbour scaling keeps it crisp.
     */
    fun render(wU: Int, hU: Int, cellU: Int, outline: Boolean = true, prescale: Boolean = false, draw: (java.awt.Graphics2D) -> Unit): BufferedImage {
        val cw = (wU + cellU - 1) / cellU; val ch = (hU + cellU - 1) / cellU
        val small = UnitImage(cw, ch, cellU, prescale)
        val g = small.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF)
        g.scale(1.0 / cellU, 1.0 / cellU)
        draw(g)
        g.dispose()
        val cells = data(small) // edited in place
        for (i in cells.indices) {
            val p = cells[i]; val a = p ushr 24
            cells[i] = when {
                a >= 128 -> (0xff shl 24) or vivid(p and 0xffffff)
                a >= 24 -> ((a / 64 * 64 + 48).coerceIn(48, 176) shl 24) or (p and 0xffffff)
                else -> 0
            }
        }
        if (outline) addOutline(cells, cw, ch)
        return small
    }

    private fun finish(cells: IntArray, cw: Int, ch: Int, cell: Int, w: Int, h: Int): BufferedImage {
        val dst = IntArray(w * h)
        for (y in 0 until h) {
            val row = (y / cell) * cw
            for (x in 0 until w) dst[y * w + x] = cells[row + x / cell]
        }
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        System.arraycopy(dst, 0, data(img), 0, dst.size)
        return img
    }

    /** Dark one-block border around the silhouette (around light blocks; dark ones are already outline). */
    private fun addOutline(cells: IntArray, cw: Int, ch: Int) {
        run {
            val solid = BooleanArray(cells.size) { (cells[it] ushr 24) == 0xff && lum(cells[it]) >= 58 }
            val out = cells.copyOf()
            for (cy in 0 until ch) for (cx in 0 until cw) {
                val i = cy * cw + cx
                if ((cells[i] ushr 24) == 0xff) continue
                val touch = (cx > 0 && solid[i - 1]) || (cx < cw - 1 && solid[i + 1]) || (cy > 0 && solid[i - cw]) || (cy < ch - 1 && solid[i + cw])
                if (touch) out[i] = (0xff shl 24) or OUTLINE
            }
            System.arraycopy(out, 0, cells, 0, cells.size)
        }
    }
}
