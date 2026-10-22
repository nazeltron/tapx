package dev.tapx.screen

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * The UI's building blocks (buttons, panels, cards, ribbons, tabs) in a chunky pixel-art style like
 * classic pixel game UI kits: flat, vivid colours (snapped to exact map colours, so they're solid on
 * the map), a black one-block outline with stepped corners, a light top row and a darker lip at the
 * bottom. Each is drawn once per size and colour into an image and reused, so the UI costs almost
 * nothing per frame.
 */
object Widgets {
    private val cache = object : java.util.LinkedHashMap<String, BufferedImage>(256, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, BufferedImage>) = size > 800
    }

    /** UI block size in layout units. */
    private const val B = 2

    private val INK = Color(PixelArt.OUTLINE)

    /** Any other picture that only depends on [key], turned into pixel art like the sprites. */
    fun custom(key: String, w: Int, h: Int, draw: Graphics2D.() -> Unit): BufferedImage {
        synchronized(cache) { cache["custom:$key"]?.let { return it } }
        val (img, g) = hiRes(w, h)
        g.draw()
        g.dispose()
        val px = PixelArt.pixelize(img, Res.k)
        synchronized(cache) { cache["custom:$key"] = px }
        return px
    }

    /**
     * A pixel meter (HP, mana): black outline, dark empty track, a bright fill with a light top row
     * and a darker bottom row, filled to [frac] (in whole blocks, so there are few cache entries).
     */
    fun meter(w: Int, h: Int, c: Color, frac: Double): BufferedImage {
        val bw = maxOf(4, (w + B - 1) / B)
        val filled = Math.round((bw - 2) * frac.coerceIn(0.0, 1.0)).toInt()
        return pixel("meter:$w:$h:${c.rgb}:$filled", w, h) { mw, mh ->
            blockRect(0, 0, mw, mh, INK, 1)
            blockRect(1, 1, mw - 2, mh - 2, v(hex("2a1a30")), 0)
            if (filled > 0) {
                color = v(c); fillRect(1, 1, filled, mh - 2)
                color = v(c, 0.45); fillRect(1, 1, filled, 1)
                color = v(c, -0.3); fillRect(1, mh - 2, filled, 1)
            }
        }
    }

    /**
     * A widget drawn on a grid of B-unit blocks: [draw] paints in block coordinates (bw x bh blocks)
     * with anti-aliasing off; the result is scaled up with hard edges.
     */
    private fun pixel(key: String, w: Int, h: Int, draw: Graphics2D.(bw: Int, bh: Int) -> Unit): BufferedImage {
        synchronized(cache) { cache[key]?.let { return it } }
        val bw = maxOf(4, (w + B - 1) / B); val bh = maxOf(4, (h + B - 1) / B)
        val small = UnitImage(bw, bh, B)
        val g = small.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.draw(bw, bh)
        g.dispose()
        synchronized(cache) { cache[key] = small }
        return small
    }

    private fun v(c: Color) = PixelArt.vivid(c)
    private fun v(c: Color, amt: Double) = PixelArt.vivid(c.shade(amt))

    /** A block-shaped rounded rectangle: the corner blocks are cut ([r] blocks deep). */
    private fun Graphics2D.blockRect(x: Int, y: Int, w: Int, h: Int, c: Color, r: Int = 1) {
        color = c
        fillRect(x + r, y, w - 2 * r, h)
        fillRect(x, y + r, w, h - 2 * r)
        if (r >= 2) { fillRect(x + 1, y + 1, w - 2, h - 2) }
    }

    /** Raised button: black stepped outline, a light top row, a flat face, a darker lip underneath. */
    fun button(w: Int, h: Int, c: Color, lip: Boolean = true) = pixel("btn:$w:$h:${c.rgb}:$lip", w, h) { bw, bh ->
        val face = v(c); val light = v(c, 0.4); val dark = v(c, -0.35)
        val lipH = if (lip) 2 else 0
        blockRect(0, 0, bw, bh, INK, 2)
        blockRect(1, 1, bw - 2, bh - 2, dark, 1)                    // lip (shows at the bottom)
        blockRect(1, 1, bw - 2, bh - 2 - lipH, face, 1)              // face
        color = light; fillRect(2, 1, bw - 4, 1)                     // top highlight row
        color = Color.WHITE; fillRect(2, 2, 2, 1); fillRect(2, 3, 1, 1) // shine in the corner
    }

    /** A window: black outline, a lighter inner border, a solid inside (top colour, darker bottom band). */
    fun panel(w: Int, h: Int, top: Color, bottom: Color) = pixel("panel:$w:$h:${top.rgb}:${bottom.rgb}", w, h) { bw, bh ->
        blockRect(0, 0, bw, bh, INK, 2)
        blockRect(1, 1, bw - 2, bh - 2, v(hex("6a74c8")), 1)
        blockRect(2, 2, bw - 4, bh - 4, v(bottom), 1)
        color = v(top); fillRect(2, 2, bw - 4, (bh - 4) * 2 / 3)
        color = v(top, 0.25); fillRect(3, 2, bw - 6, 1)
    }

    /** A list card; lighter with a gold outline when hovered. */
    fun card(w: Int, h: Int, hover: Boolean) = pixel("card:$w:$h:$hover", w, h) { bw, bh ->
        blockRect(0, 0, bw, bh, if (hover) v(hex("ffd21a")) else INK, 1)
        blockRect(1, 1, bw - 2, bh - 2, v(if (hover) hex("4a52a0") else hex("3a4080")), 1)
        color = v(if (hover) hex("5e66b8") else hex("4a5296")); fillRect(2, 1, bw - 4, 1)
        color = v(hex("2a2f60")); fillRect(2, bh - 2, bw - 4, 1)
    }

    /** The slot behind a list icon. */
    fun iconFrame() = pixel("iconframe", 46, 46) { bw, bh ->
        blockRect(0, 0, bw, bh, INK, 1)
        blockRect(1, 1, bw - 2, bh - 2, v(hex("232a58")), 1)
        color = v(hex("3a4288")); fillRect(2, bh - 2, bw - 4, 1)
    }

    /** A banner strip for section titles. */
    fun ribbon(w: Int, h: Int, c: Color) = pixel("ribbon:$w:$h:${c.rgb}", w, h + 3) { bw, bh ->
        blockRect(0, 0, bw, bh, INK, 2)
        blockRect(1, 1, bw - 2, bh - 2, v(c, -0.35), 1)
        blockRect(1, 1, bw - 2, bh - 3, v(c), 1)
        color = v(c, 0.4); fillRect(2, 1, bw - 4, 1)
    }

    /** A plain bar (headers). */
    fun bar(w: Int, h: Int, top: Color, bottom: Color) = pixel("bar:$w:$h:${top.rgb}:${bottom.rgb}", w, h) { bw, bh ->
        blockRect(0, 0, bw, bh, INK, 1)
        blockRect(1, 1, bw - 2, bh - 2, v(bottom), 1)
        color = v(top); fillRect(1, 1, bw - 2, (bh - 2) / 2)
        color = v(top, 0.3); fillRect(2, 1, bw - 4, 1)
    }

    /** A tab: a chunky tile; the active one gets a white outline. */
    fun tab(w: Int, h: Int, c: Color, active: Boolean) = pixel("tab:$w:$h:${c.rgb}:$active", w, h) { bw, bh ->
        blockRect(0, 0, bw, bh, if (active) Color.WHITE else INK, 2)
        blockRect(1, 1, bw - 2, bh - 2, v(c, -0.35), 1)
        blockRect(1, 1, bw - 2, bh - 4, v(if (active) c.shade(0.1) else c), 1)
        color = v(c, 0.4); fillRect(2, 1, bw - 4, 1)
        color = Color.WHITE; fillRect(2, 2, 2, 1); fillRect(2, 3, 1, 1)
    }
}
