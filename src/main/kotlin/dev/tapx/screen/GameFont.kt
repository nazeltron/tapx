package dev.tapx.screen

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * The game's text: Lilita One (a bold, rounded display font, SIL Open Font License, bundled in the
 * jar) with a dark outline, a drop shadow and a slight top-to-bottom shine, like mobile game
 * labels. [HEIGHT] * scale is the height of capital letters; (x, y) is their top-left corner.
 * Each label is rendered once into a small image and reused.
 */
object GameFont {
    const val HEIGHT = 8

    private val base: Font = GameFont::class.java.getResourceAsStream("/fonts/LilitaOne-Regular.ttf")!!.use {
        Font.createFont(Font.TRUETYPE_FONT, it)
    }
    private val frc = FontRenderContext(null, true, true)
    private val capRatio: Double = TextLayout("H", base.deriveFont(100f), frc).bounds.height / 100.0
    private val fonts = HashMap<Int, Font>()

    /** The font at [scale], in device pixels (Res.k per layout unit). */
    private fun font(scale: Int): Font = synchronized(fonts) {
        fonts.getOrPut(scale * 100 + Res.k) { base.deriveFont((HEIGHT * scale * Res.k / capRatio).toFloat()) }
    }

    private fun clean(text: String): String {
        val f = font(1)
        return if (f.canDisplayUpTo(text) == -1) text else String(text.map { if (f.canDisplay(it)) it else '?' }.toCharArray())
    }

    fun width(text: String, scale: Int = 1): Int =
        if (text.isEmpty()) 0 else Math.ceil(font(scale).getStringBounds(clean(text), frc).width / Res.k).toInt()

    /** Plain text with just a soft shadow. */
    fun draw(g: Graphics2D, text: String, x: Int, y: Int, color: Color, scale: Int = 1) = put(g, text, x, y, color, scale, null)

    /** Text with a dark outline and a drop shadow. */
    fun drawOutlined(g: Graphics2D, text: String, x: Int, y: Int, color: Color, scale: Int = 1, outline: Color = Color(22, 18, 34)) =
        put(g, text, x, y, color, scale, outline)

    private fun put(g: Graphics2D, text: String, x: Int, y: Int, color: Color, scale: Int, outline: Color?) {
        if (text.isEmpty()) return
        val s = sprite(text, color, scale, outline)
        g.blit(s.img, x - s.dx, y - s.dy)
    }

    private class Sprite(val img: BufferedImage, val dx: Int, val dy: Int)
    private data class Key(val text: String, val color: Int, val scale: Int, val outline: Int?)

    private val sprites = ThreadLocal.withInitial {
        object : java.util.LinkedHashMap<Key, Sprite>(256, 0.75f, true) {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<Key, Sprite>) = size > 2000
        }
    }

    private fun sprite(raw: String, color: Color, scale: Int, outline: Color?): Sprite =
        sprites.get().getOrPut(Key(raw, color.rgb, scale, outline?.rgb)) {
            // everything here is in device pixels; offsets are whole layout units so text lands on the grid
            val k = Res.k
            fun units(px: Double) = Math.ceil(px / k).toInt()
            val text = clean(raw)
            val f = font(scale)
            val layout = TextLayout(text, f, frc)
            val ow = if (outline != null) (1.6f + scale * 0.9f) * k else 0f
            val padU = units(ow + 2.0 * k)
            val pad = padU * k
            val baseU = padU + units(layout.ascent.toDouble())
            val capU = HEIGHT * scale
            val w = (units(layout.advance.toDouble()) + padU * 2 + 1) * k
            val h = (baseU + units(layout.descent.toDouble()) + padU + 1) * k
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            val shape = layout.getOutline(AffineTransform.getTranslateInstance(pad.toDouble(), (baseU * k).toDouble()))
            val drop = AffineTransform.getTranslateInstance(0.0, (1.0 + scale * 0.6) * k).createTransformedShape(shape)
            if (outline != null) {
                g.stroke = BasicStroke(ow * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.color = Color(0, 0, 0, 110); g.draw(drop)
                g.color = outline; g.draw(shape)
            } else {
                g.color = Color(0, 0, 0, 120); g.fill(drop)
            }
            val top = ((baseU - capU) * k).toFloat()
            g.paint = GradientPaint(0f, top, color.shade(0.28), 0f, top + capU * k, color)
            g.fill(shape)
            g.dispose()
            // (x, y) is the top-left of the capitals, in layout units
            Sprite(img, padU, baseU - capU)
        }
}
