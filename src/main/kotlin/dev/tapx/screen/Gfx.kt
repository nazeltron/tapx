package dev.tapx.screen

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.cos
import kotlin.math.sin

// Small drawing toolkit shared by the art and UI code.

const val TAU = Math.PI * 2
val OUT = Color(0x1d, 0x1a, 0x26)

fun hex(s: String): Color = Color(s.removePrefix("#").toInt(16))

/** Lighten (amt > 0) or darken (amt < 0) a colour. */
fun Color.shade(amt: Double): Color {
    fun f(c: Int) = (if (amt > 0) c + (255 - c) * amt else c * (1 + amt)).toInt().coerceIn(0, 255)
    return Color(f(red), f(green), f(blue))
}

fun Color.alpha(a: Double) = Color(red, green, blue, (a * 255).toInt().coerceIn(0, 255))

fun hsl(h: Double, s: Double, l: Double): Color {
    val sat = s / 100
    val lit = l.coerceIn(0.0, 100.0) / 100
    val c = (1 - kotlin.math.abs(2 * lit - 1)) * sat
    val hp = ((h % 360) + 360) % 360 / 60
    val x = c * (1 - kotlin.math.abs(hp % 2 - 1))
    val (r, g, b) = when {
        hp < 1 -> Triple(c, x, 0.0); hp < 2 -> Triple(x, c, 0.0); hp < 3 -> Triple(0.0, c, x)
        hp < 4 -> Triple(0.0, x, c); hp < 5 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
    }
    val m = lit - c / 2
    return Color(((r + m) * 255).toInt().coerceIn(0, 255), ((g + m) * 255).toInt().coerceIn(0, 255), ((b + m) * 255).toInt().coerceIn(0, 255))
}

fun path(build: Path2D.Double.() -> Unit): Path2D.Double = Path2D.Double().apply(build)
fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double): Shape = Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2)
fun circle(cx: Double, cy: Double, r: Double) = ellipse(cx, cy, r, r)
fun rrect(x: Double, y: Double, w: Double, h: Double, r: Double): Shape = RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2)

/** Canvas-style arc (clockwise in screen space, radians) appended to a path. */
fun Path2D.Double.arcTo(cx: Double, cy: Double, rx: Double, ry: Double, a0: Double, a1: Double, start: Boolean = false) {
    val steps = 16
    for (i in 0..steps) {
        val a = a0 + (a1 - a0) * i / steps
        val x = cx + cos(a) * rx
        val y = cy + sin(a) * ry
        if (i == 0 && start) moveTo(x, y) else lineTo(x, y)
    }
}

fun arc(cx: Double, cy: Double, r: Double, a0: Double, a1: Double) = path { arcTo(cx, cy, r, r, a0, a1, start = true) }

private val strokes = HashMap<Float, BasicStroke>()
fun stroke(w: Float): BasicStroke = strokes.getOrPut(w) { BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) }

fun Graphics2D.fill(shape: Shape, c: Color) { color = c; fill(shape) }

fun Graphics2D.fillStroke(shape: Shape, fill: Color, width: Float = 2.2f, outline: Color = OUT) {
    color = fill; fill(shape)
    stroke = stroke(width); color = outline; draw(shape)
}

fun Graphics2D.line(x0: Double, y0: Double, x1: Double, y1: Double, c: Color, width: Float) {
    stroke = stroke(width); color = c
    draw(java.awt.geom.Line2D.Double(x0, y0, x1, y1))
}

/** Run [block] and restore transform, clip and composite afterwards. */
inline fun Graphics2D.save(block: Graphics2D.() -> Unit) {
    val t = transform
    val c = clip
    val comp = composite
    try { block() } finally { transform = t; clip = c; composite = comp }
}

fun Graphics2D.alpha(a: Double) {
    composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a.toFloat().coerceIn(0f, 1f))
}

fun clamp(v: Double, a: Double, b: Double) = if (v < a) a else if (v > b) b else v
fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
