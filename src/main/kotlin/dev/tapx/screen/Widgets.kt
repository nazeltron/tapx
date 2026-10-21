package dev.tapx.screen

import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

/**
 * The UI's building blocks (buttons, panels, cards, ribbons, tabs) in a glossy mobile-game style.
 * Each is drawn once per size and colour into an image and reused, so the UI costs almost nothing
 * per frame.
 */
object Widgets {
    private val cache = object : java.util.LinkedHashMap<String, BufferedImage>(256, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, BufferedImage>) = size > 800
    }

    /** Any other picture that only depends on [key]. */
    fun custom(key: String, w: Int, h: Int, draw: Graphics2D.() -> Unit) = make("custom:$key", w, h, draw)

    private fun make(key: String, w: Int, h: Int, draw: Graphics2D.() -> Unit): BufferedImage {
        synchronized(cache) { cache[key]?.let { return it } }
        val img = BufferedImage(maxOf(1, w), maxOf(1, h), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.draw()
        g.dispose()
        synchronized(cache) { cache[key] = img }
        return img
    }

    private fun Graphics2D.vgrad(y0: Double, y1: Double, a: Color, b: Color) { paint = GradientPaint(0f, y0.toFloat(), a, 0f, y1.toFloat(), b) }

    /** Raised button: a darker lip underneath, a vertical gradient face, a gloss band and a rim. */
    fun button(w: Int, h: Int, c: Color, lip: Boolean = true) = make("btn:$w:$h:${c.rgb}:$lip", w, h) {
        val r = minOf(9.0, h / 2.5)
        val lipH = if (lip) 3.0 else 0.0
        val face = rrect(0.75, 0.75, w - 1.5, h - 1.5 - lipH, r)
        if (lip) fill(rrect(0.75, 0.75 + lipH, w - 1.5, h - 1.5 - lipH, r), c.shade(-0.5))
        vgrad(0.0, h - lipH, c.shade(0.22), c.shade(-0.14)); fill(face)
        save { clip(face); fill(rrect(3.0, 2.0, w - 6.0, (h - lipH) * 0.42, r - 2), Color(255, 255, 255, 60)) }
        stroke = stroke(1.5f); color = c.shade(-0.62); draw(rrect(0.75, 0.75, w - 1.5, h - 1.5, r))
        stroke = stroke(1f); color = c.shade(0.45).alpha(0.7); draw(rrect(2.0, 2.0, w - 4.0, h - 4.0 - lipH, r - 1.5))
    }

    /** A window: dark rim, a lighter bevel, a vertical gradient inside. */
    fun panel(w: Int, h: Int, top: Color, bottom: Color) = make("panel:$w:$h:${top.rgb}:${bottom.rgb}", w, h) {
        fill(rrect(0.0, 0.0, w.toDouble(), h.toDouble(), 12.0), hex("0e1020"))
        vgrad(0.0, h.toDouble(), hex("6a74a8"), hex("2a3050")); fill(rrect(2.0, 2.0, w - 4.0, h - 4.0, 11.0))
        vgrad(0.0, h.toDouble(), top, bottom); fill(rrect(4.0, 4.0, w - 8.0, h - 8.0, 9.0))
        stroke = stroke(1f); color = Color(255, 255, 255, 40); draw(rrect(4.5, 4.5, w - 9.0, h - 9.0, 9.0))
    }

    /** A list card; lighter with a gold rim when hovered. */
    fun card(w: Int, h: Int, hover: Boolean) = make("card:$w:$h:$hover", w, h) {
        val shape = rrect(0.75, 0.75, w - 1.5, h - 1.5, 8.0)
        vgrad(0.0, h.toDouble(), if (hover) hex("4e5682") else hex("424a72"), if (hover) hex("394068") else hex("2f355a")); fill(shape)
        fill(rrect(2.0, 1.5, w - 4.0, 2.0, 1.0), Color(255, 255, 255, 40))
        stroke = stroke(1.5f); color = if (hover) hex("ffd24a") else hex("181c30"); draw(shape)
    }

    /** The rounded slot behind a list icon. */
    fun iconFrame() = make("iconframe", 46, 46) {
        vgrad(0.0, 46.0, hex("1c2036"), hex("2a3050")); fill(rrect(0.5, 0.5, 45.0, 45.0, 8.0))
        stroke = stroke(1f); color = Color(255, 255, 255, 50); draw(rrect(0.5, 0.5, 45.0, 45.0, 8.0))
    }

    /** A banner strip for section titles. */
    fun ribbon(w: Int, h: Int, c: Color) = make("ribbon:$w:$h:${c.rgb}", w, h + 3) {
        fill(rrect(0.0, 3.0, w.toDouble(), h.toDouble(), h / 2.0), c.shade(-0.55))
        vgrad(0.0, h.toDouble(), c.shade(0.2), c.shade(-0.18)); fill(rrect(0.0, 0.0, w.toDouble(), h.toDouble(), h / 2.0))
        fill(rrect(h / 3.0, 2.0, w - h / 1.5, h * 0.36, h / 4.0), Color(255, 255, 255, 50))
        stroke = stroke(1.3f); color = c.shade(-0.6); draw(rrect(0.65, 0.65, w - 1.3, h - 1.3 + 3, h / 2.0))
    }

    /** A plain bar with a gradient (headers). */
    fun bar(w: Int, h: Int, top: Color, bottom: Color) = make("bar:$w:$h:${top.rgb}:${bottom.rgb}", w, h) {
        vgrad(0.0, h.toDouble(), top, bottom); fill(rrect(0.0, 0.0, w.toDouble(), h.toDouble(), 7.0))
        fill(Rectangle2D.Double(6.0, 1.0, w - 12.0, 1.5), Color(255, 255, 255, 45))
        stroke = stroke(1.2f); color = hex("12142a"); draw(rrect(0.6, 0.6, w - 1.2, h - 1.2, 7.0))
    }

    /** A tab: rounded tile, glossy; the active one gets a white rim. */
    fun tab(w: Int, h: Int, c: Color, active: Boolean) = make("tab:$w:$h:${c.rgb}:$active", w, h) {
        val shape = rrect(1.0, 1.0, w - 2.0, h - 2.0, 8.0)
        vgrad(0.0, h.toDouble(), c.shade(0.2), c.shade(-0.2)); fill(shape)
        save { clip(shape); fill(rrect(3.0, 2.5, w - 6.0, h * 0.4, 6.0), Color(255, 255, 255, if (active) 70 else 35)) }
        stroke = stroke(if (active) 2.2f else 1.5f); color = if (active) Color.WHITE else c.shade(-0.6); draw(shape)
    }
}
