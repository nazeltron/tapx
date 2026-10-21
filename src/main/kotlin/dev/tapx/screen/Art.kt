package dev.tapx.screen

import dev.tapx.Chest
import dev.tapx.Gear
import dev.tapx.Look
import dev.tapx.Pet
import dev.tapx.RARITIES
import dev.tapx.World
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Procedural art: everything on screen is drawn here with shapes, no textures. */
object Art {
    private val WHITE = Color.WHITE
    private val WOOD = hex("8a5a32")

    // ==================================================================== characters
    private fun weaponAngle(kind: String, atk: Double): Double {
        if (kind == "bow" || kind == "gun") return 0.0
        if (atk <= 0 || atk >= 1) return 0.45
        if (atk < 0.25) return lerp(0.45, -1.3, atk / 0.25)
        val k = (atk - 0.25) / 0.75
        return lerp(-1.3, 2.1, 1 - (1 - k).pow(3))
    }

    fun drawWeapon(g: Graphics2D, kind: String, blade: Color = hex("e6edf5"), bladeGlow: Color? = null, glow: Color? = null) {
        when (kind) {
            "sword" -> {
                if (bladeGlow != null) g.save { alpha(0.45); fill(ellipse(0.0, -22.0, 9.0, 24.0), bladeGlow) }
                g.fillStroke(path { moveTo(-3.0, -5.0); lineTo(-3.0, -34.0); lineTo(0.0, -41.0); lineTo(3.0, -34.0); lineTo(3.0, -5.0); closePath() }, blade, 2f)
                g.fill(java.awt.geom.Rectangle2D.Double(-1.0, -33.0, 1.5, 26.0), WHITE.alpha(0.7))
                g.fillStroke(rrect(-8.0, -7.0, 16.0, 4.0, 2.0), hex("e8b33a"), 2f)
                g.fillStroke(rrect(-2.0, -3.0, 4.0, 9.0, 1.5), hex("5a3a22"), 2f)
            }
            "spear" -> {
                g.line(0.0, 12.0, 0.0, -38.0, OUT, 5f); g.line(0.0, 12.0, 0.0, -38.0, WOOD, 3f)
                g.fillStroke(path { moveTo(-4.0, -37.0); lineTo(0.0, -52.0); lineTo(4.0, -37.0); closePath() }, blade, 2f)
                g.fill(path { moveTo(-1.0, -36.0); lineTo(-6.0, -30.0); lineTo(1.0, -33.0); closePath() }, hex("dd3333"))
            }
            "axe" -> {
                g.line(0.0, 10.0, 0.0, -30.0, OUT, 5f); g.line(0.0, 10.0, 0.0, -30.0, WOOD, 3f)
                g.fillStroke(path { moveTo(0.0, -30.0); quadTo(16.0, -36.0, 14.0, -18.0); quadTo(8.0, -22.0, 0.0, -20.0); closePath() }, blade, 2f)
                g.fillStroke(path { moveTo(0.0, -30.0); quadTo(-10.0, -32.0, -9.0, -21.0); quadTo(-5.0, -23.0, 0.0, -22.0); closePath() }, blade, 2f)
            }
            "dagger" -> {
                g.fillStroke(path { moveTo(-2.5, -4.0); lineTo(0.0, -20.0); lineTo(2.5, -4.0); closePath() }, blade, 2f)
                g.fillStroke(rrect(-5.0, -6.0, 10.0, 3.0, 1.5), hex("b23a48"), 2f)
            }
            "mace" -> {
                g.line(0.0, 8.0, 0.0, -22.0, OUT, 4f); g.line(0.0, 8.0, 0.0, -22.0, hex("c9a24a"), 2.5f)
                for (i in 0 until 6) {
                    val a = i / 6.0 * TAU
                    g.line(cos(a) * 5, -28 + sin(a) * 5, cos(a) * 10, -28 + sin(a) * 10, OUT, 2f)
                }
                g.fillStroke(circle(0.0, -28.0, 6.5), hex("f2d55a"), 2f)
            }
            "staff", "leaf" -> {
                val shaft = path { moveTo(0.0, 14.0); quadTo(2.0, -10.0, 0.0, -34.0) }
                g.stroke = stroke(5f); g.color = OUT; g.draw(shaft)
                g.stroke = stroke(3f); g.color = if (kind == "leaf") hex("6b4a2a") else hex("7a4a8a"); g.draw(shaft)
                if (kind == "leaf") {
                    g.fillStroke(path { moveTo(0.0, -34.0); quadTo(12.0, -44.0, 4.0, -54.0); quadTo(-8.0, -44.0, 0.0, -34.0) }, hex("7bd65a"), 2f)
                    g.fillStroke(path { moveTo(0.0, -32.0); quadTo(-12.0, -36.0, -12.0, -46.0); quadTo(-3.0, -42.0, 0.0, -32.0) }, hex("5ab33f"), 2f)
                } else {
                    val c = glow ?: hex("99ccff")
                    g.save { alpha(0.4); fill(circle(0.0, -39.0, 11.0), c) }
                    g.fillStroke(circle(0.0, -39.0, 6.0), c, 2f)
                    g.fill(circle(-2.0, -41.0, 2.0), WHITE.alpha(0.8))
                }
            }
            "scythe" -> {
                g.line(0.0, 14.0, 0.0, -38.0, OUT, 5f); g.line(0.0, 14.0, 0.0, -38.0, hex("3a2a2a"), 3f)
                g.fillStroke(path { moveTo(0.0, -38.0); quadTo(22.0, -44.0, 30.0, -26.0); quadTo(18.0, -36.0, 0.0, -32.0); closePath() }, hex("f2b134"), 2f)
            }
            "bow" -> {
                val bow = arc(-4.0, 0.0, 17.0, -1.25, 1.25)
                g.stroke = stroke(4f); g.color = OUT; g.draw(bow)
                g.stroke = stroke(2.5f); g.color = hex("a0662f"); g.draw(bow)
                g.line(-4 + cos(-1.25) * 17, sin(-1.25) * 17, -6.0, 0.0, hex("eeeeee"), 1f)
                g.line(-6.0, 0.0, -4 + cos(1.25) * 17, sin(1.25) * 17, hex("eeeeee"), 1f)
            }
            "gun" -> {
                g.fillStroke(rrect(-2.0, -4.0, 24.0, 6.0, 2.0), hex("4a4f5a"), 2f)
                g.fillStroke(rrect(-6.0, -2.0, 8.0, 10.0, 2.0), hex("7a5230"), 2f)
                g.fill(java.awt.geom.Rectangle2D.Double(6.0, -3.0, 6.0, 2.0), hex("d9a441"))
            }
        }
    }

    private fun drawHair(g: Graphics2D, l: Look, hy: Double, back: Boolean) {
        val hair = hex(l.hair)
        if (back) {
            if (l.hairStyle == "long") g.fillStroke(path { moveTo(-4.0, hy - 10); quadTo(-18.0, hy - 4, -14.0, hy + 20); lineTo(-4.0, hy + 18); quadTo(-8.0, hy + 6, 2.0, hy - 6); closePath() }, hair)
            return
        }
        when (l.hairStyle) {
            "spiky" -> {
                val p = listOf(-12 to 3, -21 to -2, -12 to -6, -19 to -15, -7 to -12, -6 to -23, 1 to -13, 9 to -20, 8 to -11, 16 to -9, 13 to -3, 5 to -8, -3 to -6, -7 to 5)
                g.fillStroke(path { p.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x.toDouble(), hy + y) else lineTo(x.toDouble(), hy + y) }; closePath() }, hair)
            }
            "bob", "long" -> g.fillStroke(path {
                arcTo(0.0, hy, 13.5, 13.5, PI * 0.92, PI * 2.02, start = true)
                quadTo(6.0, hy - 4, 2.0, hy - 3); quadTo(-6.0, hy - 2, -12.0, hy + 6); closePath()
            }, hair)
            "wizard" -> {
                g.fillStroke(path { moveTo(-12.0, hy); quadTo(-10.0, hy + 22, -4.0, hy + 14); lineTo(-4.0, hy) }, hair)
                val hat = hex(l.hat ?: "7b3fbf")
                g.fillStroke(path { moveTo(-15.0, hy - 6); lineTo(15.0, hy - 6); quadTo(0.0, hy - 18, -14.0, hy - 34); quadTo(-8.0, hy - 18, -15.0, hy - 6); closePath() }, hat)
                g.fillStroke(ellipse(0.0, hy - 6, 18.0, 4.0), hat)
                g.fillStroke(circle(-14.0, hy - 34, 3.0), hex("f2c94c"))
            }
            "bald" -> g.fill(ellipse(-3.0, hy - 7, 5.0, 3.0), WHITE.alpha(0.35))
            "bandana" -> {
                g.fillStroke(path { arcTo(0.0, hy, 13.0, 13.0, PI, TAU, start = true); closePath() }, hair)
                val band = hex(l.band ?: "e2463c")
                g.fillStroke(rrect(-13.0, hy - 7, 26.0, 5.0, 2.0), band)
                g.fillStroke(path { moveTo(-12.0, hy - 5); lineTo(-22.0, hy - 1); lineTo(-19.0, hy - 7); closePath() }, band)
            }
            "helmet" -> {
                g.fillStroke(path { arcTo(0.0, hy, 14.0, 14.0, PI * 0.85, PI * 2.15, start = true); closePath() }, hex(l.helm ?: "596273"))
                g.fill(java.awt.geom.Rectangle2D.Double(1.0, hy - 1, 12.0, 3.0), OUT)
                g.fillStroke(path { moveTo(-2.0, hy - 14); quadTo(-14.0, hy - 26, -20.0, hy - 18); quadTo(-10.0, hy - 18, -6.0, hy - 12) }, hex(l.trim))
            }
        }
        if (l.hairStyle in setOf("spiky", "bob", "long", "bandana")) {
            val shine = hair.shade(0.35)
            g.line(-8.0, hy - 6, -3.0, hy - 10, shine, 1.6f)
            if (l.hairStyle != "bandana") g.line(2.0, hy - 11, 6.0, hy - 10, shine, 1.4f)
        }
        l.beard?.let {
            g.fillStroke(path { moveTo(-4.0, hy + 4); quadTo(4.0, hy + 22, 13.0, hy + 4); quadTo(6.0, hy + 8, -4.0, hy + 4) }, hex(it))
            g.line(2.0, hy + 9, 4.0, hy + 14, hex(it).shade(0.25), 1.2f)
        }
    }

    fun drawHelmGear(g: Graphics2D, kind: String, color: Color, hy: Double) {
        when (kind) {
            "cap" -> {
                g.fillStroke(path { arcTo(0.0, hy - 1, 13.5, 13.5, PI, TAU, start = true); closePath() }, color)
                g.fillStroke(rrect(2.0, hy - 4, 16.0, 4.0, 2.0), color)
            }
            "visor", "horned" -> {
                g.fillStroke(path { arcTo(0.0, hy, 14.5, 14.5, PI * 0.85, PI * 2.15, start = true); closePath() }, color)
                g.fill(java.awt.geom.Rectangle2D.Double(2.0, hy - 1, 12.0, 3.0), OUT)
                if (kind == "horned") {
                    val bone = hex("f1e6c8")
                    g.fillStroke(path { moveTo(-10.0, hy - 9); quadTo(-22.0, hy - 14, -20.0, hy - 28); quadTo(-14.0, hy - 16, -5.0, hy - 13) }, bone)
                    g.fillStroke(path { moveTo(8.0, hy - 11); quadTo(18.0, hy - 18, 14.0, hy - 30); quadTo(12.0, hy - 18, 3.0, hy - 13) }, bone)
                }
            }
            "circlet" -> {
                g.fillStroke(rrect(-13.0, hy - 9, 26.0, 4.0, 2.0), color)
                g.fillStroke(circle(8.0, hy - 7, 2.5), hex("ff4a6a"))
            }
            "crown" -> {
                g.fillStroke(path { moveTo(-11.0, hy - 8); lineTo(-13.0, hy - 22); lineTo(-6.0, hy - 15); lineTo(0.0, hy - 25); lineTo(6.0, hy - 15); lineTo(13.0, hy - 22); lineTo(11.0, hy - 8); closePath() }, color)
                g.fillStroke(circle(0.0, hy - 12, 2.5), hex("3ad1ff"))
            }
        }
    }

    /**
     * A chibi figure facing right with feet at (x, y). [atk] is attack progress 0..1.
     * Poses are quantised (idle bob, cape, arm swing; 6 attack frames) and each pose is drawn once
     * into a cached image, so a character costs one image copy per frame.
     */
    fun drawChibi(g: Graphics2D, l: Look, x: Double, y: Double, s: Double, t: Double, atk: Double = 0.0) {
        val attacking = atk > 0 && atk < 1
        val atkQ = if (attacking) 1 + (atk * 6).toInt().coerceAtMost(5) else 0
        val bob = if (attacking) 0 else Math.round(sin(t * 5 + l.phase)).toInt()
        val cape = if (attacking || l.cape == null) 0 else Math.round(sin(t * 6 + l.phase) * 1.4).toInt()
        val swing = if (attacking) 0 else Math.round(sin(t * 5 + l.phase + 1)).toInt()
        val pose = Pose(l.copy(phase = 0.0, aura = null), Math.round(s * 100).toInt(), bob, cape, swing, atkQ)
        val img = chibiSprite(pose)
        g.blit(img, Math.round(x - CHIBI_X * s).toInt(), Math.round(y - CHIBI_Y * s).toInt())
        l.aura?.let { // floating sparkles, live
            val c = hex(it)
            for (i in 0 until 6) {
                val a = t * 1.6 + i / 6.0 * TAU
                val px = x + cos(a) * 22 * s; val py = y + (-28 + sin(a) * 8 + sin(t * 4 + i) * 3) * s
                g.fill(circle(px, py, 2.6 * s), c.alpha(0.6)); g.fill(circle(px, py, 1.2 * s), WHITE)
            }
        }
    }

    private data class Pose(val look: Look, val scale: Int, val bob: Int, val cape: Int, val swing: Int, val atk: Int)
    private const val CHIBI_X = 62.0
    private const val CHIBI_Y = 104.0
    private val chibiCache = object : java.util.LinkedHashMap<Pose, BufferedImage>(256, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Pose, BufferedImage>) = size > 900
    }

    private fun chibiSprite(p: Pose): BufferedImage {
        synchronized(chibiCache) { chibiCache[p]?.let { return it } }
        val s = p.scale / 100.0
        val (img, g) = hiRes(Math.ceil(134 * s).toInt(), Math.ceil(142 * s).toInt())
        g.scale(s, s); g.translate(CHIBI_X, CHIBI_Y)
        val atk = if (p.atk == 0) 0.0 else (p.atk - 0.5) / 6.0
        drawChibiNow(g, p.look, p.bob.toDouble(), p.cape * 2.8, p.swing * 1.5, atk)
        g.dispose()
        synchronized(chibiCache) { chibiCache[p] = img }
        return img
    }

    /** The full character in local units, feet at (0, 0). */
    private fun drawChibiNow(g: Graphics2D, l: Look, bob: Double, capeW: Double, swing: Double, atk: Double) = with(g) {
        val lean = if (atk > 0 && atk < 1) sin(atk * PI) * 4 else 0.0
        val step = if (atk > 0 && atk < 1) sin(atk * PI) * 3 else 0.0
        val skin = hex(l.skin); val outfit = hex(l.outfit)
        fill(ellipse(0.0, 0.0, 18.0, 4.5), Color(0, 0, 0, 70))
        translate(lean, 0.0)
        l.cape?.let {
            val w = capeW
            val cape = path { moveTo(-5.0, -33 + bob); quadTo(-22.0, -28 + bob, -34 + w, -18 + w * 0.5); lineTo(-30 + w, -10.0); quadTo(-18.0, -16.0, -2.0, -24 + bob); closePath() }
            paint = GradientPaint(-2f, -30f, hex(it).shade(0.15), -30f, -12f, hex(it).shade(-0.35)); fill(cape)
            save { clip(cape); fill(path { moveTo(-4.0, -28 + bob); quadTo(-16.0, -22.0, -31 + w, -12.0); lineTo(-30 + w, -8.0); quadTo(-16.0, -14.0, -2.0, -22 + bob); closePath() }, hex(it).shade(-0.4)) }
            stroke = stroke(1.8f); color = OUT; draw(cape)
        }
        // back arm (behind the body)
        line(-6.0, -31 + bob, -9.0 - swing, -21 + bob, OUT, 6.2f); line(-6.0, -31 + bob, -9.0 - swing, -21 + bob, outfit.shade(-0.3), 4.2f)
        fillStroke(circle(-9.5 - swing, -19.5 + bob, 2.8), skin.shade(-0.12), 1.6f)
        // legs: shaded pants, boots with soles and a shine
        val pants = hex(l.pants); val boots = hex(l.boots)
        for ((lx, front) in listOf(-8 - step to false, 2 + step to true)) {
            val pc = if (front) pants else pants.shade(-0.15)
            paint = GradientPaint(lx.toFloat(), -14f, pc.shade(0.15), (lx + 6).toFloat(), -4f, pc.shade(-0.25))
            fill(rrect(lx, -14.0, 6.0, 12.0, 2.0)); stroke = stroke(1.8f); color = OUT; draw(rrect(lx, -14.0, 6.0, 12.0, 2.0))
            val bx = lx - 1; val bw = if (front) 10.0 else 9.0
            paint = GradientPaint(0f, -4f, boots.shade(0.25), 0f, 1f, boots.shade(-0.25))
            fill(rrect(bx, -4.0, bw, 5.0, 2.0)); stroke = stroke(1.8f); color = OUT; draw(rrect(bx, -4.0, bw, 5.0, 2.0))
            fill(rrect(bx + 0.8, 0.0, bw - 1.6, 1.2, 0.6), boots.shade(-0.5))
            fill(ellipse(bx + 3, -2.6, 1.6, 0.8), boots.shade(0.5))
        }
        save { translate(0.0, bob); drawUpper(this, l) }
        // front arm and weapon
        line(4.0, -32 + bob, 7.0, -25 + bob, OUT, 6.2f); line(4.0, -32 + bob, 7.0, -25 + bob, outfit.shade(0.05), 4.2f)
        translate(7.0, -25 + bob)
        rotate(weaponAngle(l.weapon, atk))
        drawWeapon(this, l.weapon, l.blade?.let(::hex) ?: hex("e6edf5"), l.bladeGlow?.let(::hex), l.glow?.let(::hex))
        fillStroke(circle(0.0, 0.0, 3.4), skin, 1.8f)
        fill(circle(-1.0, -1.0, 1.1), skin.shade(0.35))
    }

    /** Torso and head, feet at (0, 0): shading, collar, belt, face with eyes, brows and ears. */
    private fun drawUpper(g: Graphics2D, l: Look) = with(g) {
        val outfit = hex(l.outfit); val trim = hex(l.trim); val skin = hex(l.skin)
        val torso = rrect(-10.0, -35.0, 20.0, 23.0, 6.0)
        paint = GradientPaint(-10f, -35f, outfit.shade(0.28), 10f, -12f, outfit.shade(-0.28)); fill(torso)
        save {
            clip(torso)
            fill(ellipse(-5.0, -30.0, 3.0, 6.0), WHITE.alpha(0.18))
            fill(java.awt.geom.Rectangle2D.Double(-10.0, -14.5, 20.0, 3.0), trim.shade(-0.15)) // hem
            fill(path { moveTo(-5.0, -36.0); lineTo(0.0, -29.0); lineTo(5.0, -36.0); closePath() }, skin.shade(-0.08)) // neckline
            line(-5.0, -35.0, 0.0, -29.0, trim, 1.6f); line(0.0, -29.0, 5.0, -35.0, trim, 1.6f)
            fill(path { moveTo(1.0, -26.0); lineTo(3.0, -24.0); lineTo(1.0, -22.0); lineTo(-1.0, -24.0); closePath() }, trim) // emblem
        }
        stroke = stroke(1.8f); color = OUT; draw(torso)
        // belt and buckle
        fill(java.awt.geom.Rectangle2D.Double(-9.0, -19.5, 18.0, 3.5), hex("3a2a20"))
        fill(rrect(-2.5, -20.5, 5.0, 5.5, 1.0), trim.shade(-0.2))
        fill(java.awt.geom.Rectangle2D.Double(-1.5, -19.5, 3.0, 3.5), trim)
        fill(java.awt.geom.Rectangle2D.Double(-1.5, -19.5, 1.0, 1.0), trim.shade(0.5))
        val hy = -47.0
        if (l.hairStyle == "hood") {
            val hood = hex(l.hood ?: "2e3550")
            fillStroke(circle(-1.0, hy, 15.0), hood)
            save { clip(circle(-1.0, hy, 15.0)); fill(circle(-6.0, hy - 5, 9.0), hood.shade(0.15)) }
            fillStroke(ellipse(3.0, hy + 2, 9.0, 9.5), skin)
            save { clip(ellipse(3.0, hy + 2, 9.0, 9.5)); fill(ellipse(3.0, hy - 6, 10.0, 4.0), hood.shade(-0.4).alpha(0.5)) }
        } else {
            drawHair(this, l, hy, true)
            // ear, head with a soft shade on the back of the head
            fillStroke(ellipse(-4.0, hy + 2, 3.0, 3.8), skin, 1.8f)
            fill(ellipse(-4.0, hy + 2, 1.3, 2.0), skin.shade(-0.2))
            val head = circle(0.0, hy, 12.5)
            paint = RadialGradientPaint(3f, (hy - 4).toFloat(), 17f, floatArrayOf(0f, 0.7f, 1f), arrayOf(skin.shade(0.18), skin, skin.shade(-0.18)))
            fill(head)
            save { clip(head); fill(ellipse(3.0, hy + 11, 10.0, 3.0), skin.shade(-0.14)) }
            stroke = stroke(1.8f); color = OUT; draw(head)
            fill(ellipse(-4.0, hy + 2, 2.2, 3.0), skin) // ear sits over the outline
            fill(ellipse(-4.0, hy + 2, 1.0, 1.7), skin.shade(-0.22))
        }
        if (l.hairStyle != "helmet" && l.helmGear != "visor" && l.helmGear != "horned") {
            for (ex in listOf(3.2, 9.0)) {
                fill(ellipse(ex, hy + 1, 2.0, 2.9), WHITE)
                fill(ellipse(ex + 0.5, hy + 1.3, 1.4, 2.3), hex("2a2340"))
                fill(java.awt.geom.Rectangle2D.Double(ex - 0.2, hy - 0.6, 1.0, 1.0), WHITE)
            }
            val brow = hex(l.hair).shade(-0.35)
            line(1.5, hy - 3.5, 4.5, hy - 4.2, brow, 1.3f); line(7.5, hy - 4.2, 10.5, hy - 3.6, brow, 1.3f)
            fill(circle(8.5, hy + 6, 2.4), Color(255, 120, 120, 80))
            line(5.0, hy + 7.2, 7.5, hy + 7.6, hex("8a3a3a"), 1.2f)
        }
        if (l.hairStyle != "hood") drawHair(this, l, hy, false)
        l.helmGear?.let { drawHelmGear(this, it, hex(l.helmColor ?: "999999"), hy) }
    }

    // ==================================================================== monsters
    class MonsterSpec(
        val shape: String, val hue: Double, val w: Double, val h: Double,
        val color: Color, val dark: Color, val light: Color, val belly: Color, val accent: Color,
        val eyes: Int, val eyeR: Double, val mouth: String, val horns: String?, val spikes: Boolean,
        val arms: Boolean, val legs: Boolean, val belly2: Boolean, val spots: Boolean, val float: Boolean,
        val boss: Boolean, val mimic: Boolean, val spotSeed: Int, val lord: Boolean = false,
    )

    private val SHAPES = listOf("blob", "tower", "cone", "orb", "brute", "slime")

    fun genMonster(seed: Long, boss: Boolean, mimic: Boolean, lord: Boolean = false): MonsterSpec {
        val r = Random(seed)
        fun <T> pick(l: List<T>) = l[r.nextInt(l.size)]
        val shape = pick(SHAPES)
        val hue = r.nextDouble() * 360
        val sat = 40 + r.nextDouble() * 35
        val lit = 42 + r.nextDouble() * 14
        val dims = mapOf("blob" to (130.0 to 125.0), "tower" to (92.0 to 185.0), "cone" to (140.0 to 180.0), "orb" to (112.0 to 112.0), "brute" to (165.0 to 150.0), "slime" to (150.0 to 95.0))[shape]!!
        val k = 0.88 + r.nextDouble() * 0.2
        return MonsterSpec(
            shape, hue, dims.first * k, dims.second * k,
            hsl(hue, sat, lit), hsl(hue, sat, lit - 16), hsl(hue, sat, lit + 18), hsl(hue, sat - 10, lit + 28),
            hsl((hue + 150 + r.nextDouble() * 60) % 360, 70.0, 55.0),
            pick(listOf(1, 2, 2, 2, 3)), 9 + r.nextDouble() * 6, pick(listOf("grin", "fangs", "o", "teeth", "smile")),
            if (r.nextDouble() < 0.5) pick(listOf("curl", "spike", "antler")) else null,
            shape != "orb" && r.nextDouble() < 0.35,
            shape != "slime" && shape != "orb" && r.nextDouble() < 0.6,
            shape != "orb" && shape != "slime" && r.nextDouble() < 0.55,
            r.nextDouble() < 0.5, r.nextDouble() < 0.35, shape == "orb", boss, mimic, r.nextInt(1_000_000), lord,
        )
    }

    private fun bodyPath(sp: MonsterSpec) = path {
        val w = sp.w; val h = sp.h
        when (sp.shape) {
            "blob" -> {
                moveTo(-w / 2, -h * 0.12); curveTo(-w / 2, -h * 1.02, w / 2, -h * 1.02, w / 2, -h * 0.12)
                quadTo(w / 2, 0.0, w * 0.3, 0.0); lineTo(-w * 0.3, 0.0); quadTo(-w / 2, 0.0, -w / 2, -h * 0.12)
            }
            "tower" -> append(rrect(-w / 2, -h, w, h, w * 0.42), false)
            "cone" -> {
                moveTo(-w / 2, 0.0); quadTo(-w * 0.28, -h * 0.55, -w * 0.05, -h * 0.97)
                quadTo(0.0, -h * 1.02, w * 0.05, -h * 0.97); quadTo(w * 0.28, -h * 0.55, w / 2, 0.0)
            }
            "orb" -> append(circle(0.0, -h / 2, w / 2), false)
            "brute" -> {
                moveTo(-w * 0.36, 0.0); curveTo(-w * 0.62, -h * 0.5, -w * 0.5, -h * 0.92, -w * 0.12, -h * 0.95)
                quadTo(0.0, -h * 1.08, w * 0.12, -h * 0.95); curveTo(w * 0.5, -h * 0.92, w * 0.62, -h * 0.5, w * 0.36, 0.0)
            }
            else -> { // slime
                moveTo(-w / 2, 0.0); curveTo(-w * 0.55, -h * 1.3, w * 0.55, -h * 1.3, w / 2, 0.0)
                for (i in 4 downTo 0) {
                    val x0 = -w / 2 + w * i / 5
                    quadTo(x0 + w / 10, 8 * ((i % 2) + 0.5), x0, 0.0)
                }
            }
        }
        closePath()
    }

    private fun faceY(sp: MonsterSpec) = sp.h * when (sp.shape) { "blob" -> 0.6; "tower" -> 0.72; "cone" -> 0.42; "orb" -> 0.52; "brute" -> 0.68; else -> 0.55 }

    private fun drawEye(g: Graphics2D, x: Double, y: Double, r: Double, blink: Boolean, angry: Boolean, iris: Color = hex("6a4a2a")) {
        if (blink) { g.line(x - r, y, x + r, y, OUT, 2.5f); return }
        g.fillStroke(circle(x, y, r), WHITE, 2.5f)
        g.save { clip(circle(x, y, r)); fill(ellipse(x, y + r * 0.9, r, r * 0.45), hex("d8dce8")) } // lower lid shade
        g.fill(circle(x - r * 0.3, y + r * 0.05, r * 0.6), iris)
        g.fill(circle(x - r * 0.3, y + r * 0.05, r * 0.36), OUT)
        g.fill(circle(x - r * 0.48, y - r * 0.22, r * 0.18), WHITE)
        g.fill(circle(x - r * 0.08, y + r * 0.3, r * 0.08), WHITE)
        if (angry) g.line(x - r * 1.1, y - r * 1.25, x + r * 1.1, y - r * 0.75, OUT, 3.5f)
    }

    /** Total drawn height above the ground, for HUD placement. */
    fun monsterHeight(sp: MonsterSpec) = if (sp.mimic) 120.0 else sp.h * (if (sp.lord) 1.4 else if (sp.boss) 1.22 else 1.0) + (if (sp.legs) 16 else 0) + (if (sp.float) 24 else 0)

    fun drawMonster(g: Graphics2D, sp: MonsterSpec, x: Double, gy: Double, t: Double, hitT: Double, deathT: Double, age: Double, scaleAll: Double) {
        val spawn = clamp(age * 4, 0.0, 1.0)
        if (sp.mimic) return drawMimic(g, x, gy, t, hitT, deathT, spawn, scaleAll)
        val scale = (if (sp.lord) 1.4 else if (sp.boss) 1.22 else 1.0) * (1 - deathT * 0.6) * (0.4 + 0.6 * spawn) * scaleAll
        val breathe = sin(t * 2.4 + sp.hue) * 0.03
        val sq = hitT * 0.09
        val legH = if (sp.legs) 16.0 else 0.0
        val floatY = if (sp.float) -24 + sin(t * 2) * 6 else 0.0
        g.save {
            alpha(1 - deathT)
            translate(x, gy)
            fill(ellipse(0.0, 0.0, sp.w * 0.45 * scale, 8 * scale), Color(0, 0, 0, 64))
            scale(scale * (1 + sq - breathe), scale * (1 - sq + breathe))
            rotate(deathT * 0.4)
            translate(hitT * 6, floatY - legH)
            val w = sp.w; val h = sp.h; val fy = -faceY(sp)
            if (sp.boss) save {
                alpha((0.3 + sin(t * 4) * 0.1) * (1 - deathT))
                val auraC = if (sp.lord) hex("b03aff") else hex("ff3a3a")
                paint = RadialGradientPaint(0f, (-h / 2).toFloat(), w.toFloat(), floatArrayOf(0f, 1f), arrayOf(auraC, Color(auraC.red, auraC.green, auraC.blue, 0)))
                fill(circle(0.0, -h / 2, w))
            }
            if (sp.legs) {
                val st = sin(t * 3) * 2
                fillStroke(rrect(-w * 0.3, -6.0, w * 0.2, legH + 8 + st, 6.0), sp.dark, 4f)
                fillStroke(rrect(w * 0.1, -6.0, w * 0.2, legH + 8 - st, 6.0), sp.dark, 4f)
            }
            if (sp.spikes) for (i in 0 until 7) {
                val a = PI * (1.08 + i * 0.14)
                val cx = cos(a) * w * 0.47; val cy = -h * 0.5 + sin(a) * h * 0.5
                val ox = cos(a) * 18; val oy = sin(a) * 18
                val px = -sin(a) * 8; val py = cos(a) * 8
                fillStroke(path { moveTo(cx - px, cy - py); lineTo(cx + ox, cy + oy); lineTo(cx + px, cy + py); closePath() }, sp.accent, 4f)
                fill(path { moveTo(cx, cy); lineTo(cx + ox * 0.8, cy + oy * 0.8); lineTo(cx + px * 0.8, cy + py * 0.8); closePath() }, sp.accent.shade(-0.25))
            }
            sp.horns?.let { horns ->
                val hx = if (sp.shape == "cone") w * 0.13 else w * 0.26
                val hy = when (sp.shape) { "cone" -> -h * 0.78; "tower" -> -h * 0.95; else -> -h * 0.86 }
                val c = if (horns == "antler") hex("c9a27a") else hex("e9c48a")
                for (d in listOf(-1.0, 1.0)) fillStroke(path {
                    when (horns) {
                        "curl" -> {
                            moveTo(d * (hx - 8), hy + 6); quadTo(d * (hx + 4), hy - 42, d * (hx + 26), hy - 34)
                            quadTo(d * (hx + 32), hy - 20, d * (hx + 20), hy - 22); quadTo(d * (hx + 14), hy - 24, d * (hx + 8), hy + 4)
                        }
                        "spike" -> { moveTo(d * (hx - 9), hy + 4); quadTo(d * (hx + 2), hy - 20, d * (hx + 10), hy - 40); quadTo(d * (hx + 10), hy - 14, d * (hx + 9), hy + 4) }
                        else -> {
                            moveTo(d * (hx - 3), hy + 4); lineTo(d * (hx - 1), hy - 30); lineTo(d * (hx - 12), hy - 44); lineTo(d * (hx + 3), hy - 34)
                            lineTo(d * (hx + 16), hy - 46); lineTo(d * (hx + 6), hy - 26); lineTo(d * (hx + 7), hy + 4)
                        }
                    }
                    closePath()
                }, c, 3.5f)
                for (d in listOf(-1.0, 1.0)) for (k in 1..2) line(d * (hx - 6), hy - k * 9.0, d * (hx + 6), hy - k * 9.0 - 3, c.shade(-0.3), 2f)
            }
            if (sp.arms) {
                val sway = sin(t * 3 + 1) * 6
                for (d in listOf(-1.0, 1.0)) {
                    val sx = d * w * 0.4; val sy = -h * 0.45
                    val arm = path { moveTo(sx, sy); quadTo(d * (w * 0.68), sy - 10 + sway * d, d * (w * 0.62), sy + 38 + sway) }
                    stroke = stroke(13f); color = OUT; draw(arm)
                    stroke = stroke(8f); color = sp.dark; draw(arm)
                    val cx = d * (w * 0.62); val cy = sy + 38 + sway
                    for (c in -1..1) fillStroke(path { moveTo(cx + c * 5, cy); lineTo(cx + c * 7, cy + 11); lineTo(cx + c * 5 + 3, cy + 1); closePath() }, hex("efe6d2"), 3f)
                }
            }
            if (!sp.legs && !sp.float && sp.shape != "slime") for (d in listOf(-1.0, 1.0)) {
                val fx = d * w * 0.26
                fillStroke(ellipse(fx, -2.0, w * 0.12, 7.0), sp.dark, 3.5f)
                for (c in -1..1) fill(path { moveTo(fx + c * 6 + d * 2 - 2, 2.0); lineTo(fx + c * 6 + d * 2, 7.0); lineTo(fx + c * 6 + d * 2 + 2, 2.0); closePath() }, hex("efe6d2"))
            }
            val body = bodyPath(sp)
            fill(body, sp.color)
            save {
                clip(body)
                // smooth shading lit from the top-left, a glossy highlight, a darker rim at the bottom-right
                val rad = max(w, h).toFloat() * 1.05f
                paint = RadialGradientPaint((-w * 0.22).toFloat(), (-h * 0.72).toFloat(), rad, floatArrayOf(0f, 0.5f, 0.85f, 1f),
                    arrayOf(sp.light.shade(0.2), sp.color, sp.dark, sp.dark.shade(-0.25)))
                fill(java.awt.geom.Rectangle2D.Double(-w, -h * 1.3, w * 2, h * 1.5))
                save { rotate(-0.5, -w * 0.22, -h * 0.8); fill(ellipse(-w * 0.22, -h * 0.8, w * 0.17, h * 0.08), Color(255, 255, 255, 120)) }
                fill(ellipse(-w * 0.3, -h * 0.66, w * 0.04, h * 0.03), Color(255, 255, 255, 150))
                if (sp.belly2) fill(ellipse(0.0, -h * 0.12, w * 0.32, h * 0.32), sp.belly)
                // soft shadow at the base, and texture: spots, or scale/fur marks
                fill(ellipse(0.0, 0.0, w * 0.62, h * 0.14), sp.dark.shade(-0.15))
                val r2 = Random(sp.spotSeed)
                if (sp.spots) repeat(7) {
                    val sx = (r2.nextDouble() - 0.5) * w * 0.8; val sy = -r2.nextDouble() * h * 0.85; val sr = 4 + r2.nextDouble() * 7
                    fill(circle(sx, sy, sr), sp.dark); fill(circle(sx - sr * 0.3, sy - sr * 0.3, sr * 0.35), sp.color)
                } else repeat(14) {
                    val mx = (r2.nextDouble() - 0.5) * w * 0.85; val my = -h * (0.1 + r2.nextDouble() * 0.8)
                    stroke = stroke(2f); color = sp.dark.alpha(0.7)
                    draw(path { moveTo(mx - 4, my); quadTo(mx, my + 4, mx + 4, my) })
                }

                if (hitT > 0) fill(java.awt.geom.Rectangle2D.Double(-w, -h * 1.2, w * 2, h * 1.4), WHITE.alpha(hitT * 0.55))
            }
            stroke = stroke(3.5f); color = sp.dark.shade(-0.65); draw(body)
            val blink = (t + sp.hue * 0.01) % 3.7 < 0.12
            val er = sp.eyeR
            when (sp.eyes) {
                1 -> drawEye(this, 0.0, fy, er * 1.5, blink, sp.boss, sp.accent)
                2 -> { drawEye(this, -er * 1.25, fy, er, blink, sp.boss, sp.accent); drawEye(this, er * 1.25, fy, er, blink, sp.boss, sp.accent) }
                else -> { drawEye(this, -er * 1.9, fy + 2, er * 0.85, blink, sp.boss, sp.accent); drawEye(this, 0.0, fy - er * 0.6, er, blink, sp.boss, sp.accent); drawEye(this, er * 1.9, fy + 2, er * 0.85, blink, sp.boss, sp.accent) }
            }
            val my = fy + er * 2 + 4
            val open = 1 + hitT * 0.8
            val mouthC = hex("4a1020")
            for (d in listOf(-1.0, 1.0)) fill(ellipse(d * (er * 2.6 + 8), my - 2, 7.0, 4.0), Color(255, 110, 140, 70)) // cheeks
            when (sp.mouth) {
                "grin" -> {
                    val grin = path { moveTo(-20.0, my); quadTo(0.0, my + 22 * open, 20.0, my); closePath() }
                    fill(grin, mouthC)
                    save { clip(grin); fill(ellipse(4.0, my + 11 * open, 9.0, 5.0), hex("e0506a")) } // tongue
                    stroke = stroke(3.5f); color = OUT; draw(grin)
                    fill(java.awt.geom.Rectangle2D.Double(-9.0, my + 1, 7.0, 6.0), WHITE); fill(java.awt.geom.Rectangle2D.Double(2.0, my + 1, 7.0, 6.0), WHITE)
                }
                "fangs" -> {
                    line(-18.0, my, 18.0, my, OUT, 3.5f)
                    for (d in listOf(-1.0, 1.0)) fillStroke(path { moveTo(d * 12, my); lineTo(d * 8, my + 11 * open); lineTo(d * 4, my); closePath() }, WHITE, 3f)
                }
                "o" -> fillStroke(ellipse(0.0, my + 4, 7.0, 8 * open), mouthC, 3.5f)
                "teeth" -> {
                    fill(rrect(-22.0, my - 2, 44.0, 14 * open, 5.0), mouthC)
                    fill(ellipse(0.0, my + 12 * open - 2, 12.0, 4.0), hex("e0506a"))
                    stroke = stroke(3.5f); color = OUT; draw(rrect(-22.0, my - 2, 44.0, 14 * open, 5.0))
                    for (i in 0 until 5) fill(path { moveTo(-20 + i * 8.5, my - 1); lineTo(-16 + i * 8.5, my + 6); lineTo(-12 + i * 8.5, my - 1); closePath() }, WHITE)
                }
                else -> { stroke = stroke(3.5f); color = OUT; draw(arc(0.0, my - 4, 12.0, 0.2, PI - 0.2)) }
            }
            if (sp.boss) {
                val top = if (sp.shape == "orb") -h else -h - (if (sp.shape == "cone") -4 else 2)
                fillStroke(path { moveTo(-20.0, top + 4); lineTo(-24.0, top - 22); lineTo(-10.0, top - 10); lineTo(0.0, top - 28); lineTo(10.0, top - 10); lineTo(24.0, top - 22); lineTo(20.0, top + 4); closePath() }, hex("ffcf3a"), 3f)
                fill(path { moveTo(-16.0, top + 1); lineTo(-19.0, top - 15); lineTo(-13.0, top - 9); closePath() }, hex("fff2a8"))
                fillStroke(circle(0.0, top - 6, 4.0), hex("e63950"), 3f)
                fill(circle(-1.2, top - 7.2, 1.2), WHITE)
                fillStroke(circle(-14.0, top - 3, 2.5), hex("3ad1ff"), 2f); fillStroke(circle(14.0, top - 3, 2.5), hex("3ad1ff"), 2f)
            }
        }
    }

    private fun drawMimic(g: Graphics2D, x: Double, gy: Double, t: Double, hitT: Double, deathT: Double, spawn: Double, scaleAll: Double) = g.save {
        val s = (1 - deathT * 0.6) * (0.4 + 0.6 * spawn) * scaleAll
        val chomp = abs(sin(t * 3)) * 0.35 + hitT * 0.3
        alpha(1 - deathT)
        translate(x, gy)
        fill(ellipse(0.0, 0.0, 70 * s, 8 * s), Color(0, 0, 0, 64))
        scale(s * (1 + hitT * 0.08), s * (1 - hitT * 0.08))
        translate(0.0, -abs(sin(t * 6)) * 6)
        fillStroke(rrect(-70.0, -70.0, 140.0, 70.0, 8.0), hex("a8662f"), 4f)
        for (i in 0..1) fill(java.awt.geom.Rectangle2D.Double(-68.0, -42.0 + i * 18, 136.0, 3.0), hex("7a4520"))
        for (i in 0 until 5) line(-50.0 + i * 24, -60.0 + (i % 2) * 8, -40.0 + i * 24, -60.0 + (i % 2) * 8, hex("8a5326"), 2f)
        for (nx in listOf(-67.0, 67.0)) for (ny in listOf(-62.0, -10.0)) fill(circle(nx, ny, 2.0), hex("5a3a10"))
        fillStroke(rrect(-74.0, -72.0, 14.0, 72.0, 4.0), hex("e8b83a"), 4f)
        fillStroke(rrect(60.0, -72.0, 14.0, 72.0, 4.0), hex("e8b83a"), 4f)
        fill(rrect(-64.0, -86.0, 128.0, 20.0, 6.0), hex("3a0a14"))
        fill(ellipse(10.0, -74.0, 24.0, 8.0), hex("ff6a8a"))
        save {
            translate(-70.0, -72.0); rotate(-chomp)
            fillStroke(path { moveTo(0.0, 0.0); lineTo(0.0, -34.0); quadTo(70.0, -70.0, 140.0, -34.0); lineTo(140.0, 0.0); closePath() }, hex("b8743a"), 4f)
            fillStroke(rrect(60.0, -16.0, 20.0, 22.0, 4.0), hex("e8b83a"), 3f)
            for (i in 0 until 8) fillStroke(path { moveTo(10.0 + i * 16, 0.0); lineTo(18.0 + i * 16, 12.0); lineTo(26.0 + i * 16, 0.0); closePath() }, WHITE, 2.5f)
        }
        fill(circle(-24.0, -80.0, 6.0), hex("ffe34a")); fill(circle(24.0, -80.0, 6.0), hex("ffe34a"))
        fill(circle(-26.0, -80.0, 2.5), OUT); fill(circle(22.0, -80.0, 2.5), OUT)
        if (hitT > 0) fill(java.awt.geom.Rectangle2D.Double(-76.0, -140.0, 152.0, 140.0), WHITE.alpha(hitT * 0.5))
    }

    // ==================================================================== pets
    fun drawPet(g: Graphics2D, p: Pet, x: Double, y: Double, s: Double, t: Double, attack: Boolean = false) {
        val flapQ = Math.round(sin(t * 14) * 2).toInt(); val wagQ = Math.round(sin(t * 6)).toInt()
        val img = cached("pet", "${p.id}:$flapQ:$wagQ:$attack", 96 * s) { drawPetNow(it, p, 48 * s, 48 * s, s, flapQ * 0.25, wagQ * 0.4, attack) }
        g.blit(img, Math.round(x - 48 * s - 4).toInt(), Math.round(y - 48 * s - 4).toInt())
    }

    private fun drawPetNow(g: Graphics2D, p: Pet, x: Double, y: Double, s: Double, flap: Double, wag: Double, attack: Boolean) = g.save {
        translate(x, y); scale(s, s)
        val wing = hex(p.wing); val body = hex(p.body)
        save { // tail
            translate(-12.0, 10.0); rotate(wag)
            fillStroke(path { moveTo(0.0, 0.0); quadTo(-16.0, 4.0, -18.0, -10.0); quadTo(-10.0, -4.0, 2.0, -6.0); closePath() }, body, 2.5f)
            fill(circle(-17.0, -9.0, 3.0), hex(p.belly))
        }
        for (d in listOf(-1.0, 1.0)) fillStroke(ellipse(d * 7, 18.0, 4.0, 3.0), body.shade(-0.2), 2f) // feet
        for (d in listOf(-1.0, 1.0)) save {
            scale(d, 1.0); rotate(flap * 0.6)
            fillStroke(path { moveTo(10.0, -4.0); quadTo(30.0, -26.0, 40.0, -10.0); quadTo(30.0, -8.0, 32.0, 2.0); quadTo(22.0, 0.0, 10.0, 6.0); closePath() }, wing, 2.5f)
        }
        if (p.ears != "none") for (d in listOf(-1.0, 1.0)) fillStroke(path {
            if (p.ears == "fox") { moveTo(d * 6, -14.0); lineTo(d * 14, -30.0); lineTo(d * 17, -10.0) }
            else { moveTo(d * 5, -14.0); quadTo(d * 10, -28.0, d * 16, -30.0); quadTo(d * 14, -18.0, d * 13, -10.0) }
            closePath()
        }, if (p.ears == "horns") hex("f1e3c0") else body, 2.5f)
        if (p.fuzzy) fillStroke(path {
            for (i in 0..20) { val a = i / 20.0 * TAU; val r = if (i % 2 == 1) 17.0 else 20.0; if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r) }
            closePath()
        }, body, 2.5f) else fillStroke(ellipse(0.0, 0.0, 18.0, 19.0), body, 2.5f)
        save { // shading and a shine
            clip(ellipse(0.0, 0.0, 17.0, 18.0))
            fill(circle(9.0, 9.0, 16.0).let { java.awt.geom.Area(ellipse(0.0, 0.0, 17.0, 18.0)).apply { subtract(java.awt.geom.Area(circle(-3.0, -3.0, 18.0))) } }, body.shade(-0.18))
            fill(ellipse(-8.0, -10.0, 4.0, 2.5), body.shade(0.4))
        }
        fill(ellipse(0.0, 8.0, 10.0, 8.0), hex(p.belly))
        for (d in listOf(-1.0, 1.0)) fill(ellipse(d * 11, 3.0, 3.0, 2.0), Color(255, 110, 140, 90))
        if (p.sleepy) { line(-9.0, -3.0, -3.0, -3.0, OUT, 2f); line(3.0, -3.0, 9.0, -3.0, OUT, 2f) }
        else {
            val er = if (attack) 3.6 else 2.6
            fill(circle(-6.0, -3.0, er), OUT); fill(circle(6.0, -3.0, er), OUT)
            fill(circle(-6.8, -4.0, 0.9 * er / 2.6), WHITE); fill(circle(5.2, -4.0, 0.9 * er / 2.6), WHITE)
            fill(circle(-5.2, -1.8, 0.5), WHITE); fill(circle(6.8, -1.8, 0.5), WHITE)
        }
        fillStroke(path { moveTo(-3.0, 3.0); lineTo(-1.5, 6.0); lineTo(0.0, 3.0); lineTo(1.5, 6.0); lineTo(3.0, 3.0); closePath() }, WHITE, 1.5f)
        if (p.bow) {
            fillStroke(path { moveTo(0.0, 10.0); lineTo(-8.0, 5.0); lineTo(-8.0, 15.0); closePath() }, hex("ffd23a"), 2f)
            fillStroke(path { moveTo(0.0, 10.0); lineTo(8.0, 5.0); lineTo(8.0, 15.0); closePath() }, hex("ffd23a"), 2f)
        }
    }

    // ==================================================================== backgrounds
    /** Static scenery for a world, drawn once into an image of the battle area. */
    fun background(world: World, w: Int, h: Int, gy: Double): BufferedImage {
        val (img, g) = hiRes(w, h, opaque = true)
        val r = Random(world.name.length * 1013L + 7)
        g.paint = GradientPaint(0f, 0f, hex(world.sky.first), 0f, gy.toFloat(), hex(world.sky.second))
        g.fillRect(0, 0, w, h)
        val dark = world.kind in setOf("forest", "lava", "void")
        val sx = w * 0.8; val sy = gy * 0.24
        g.save { alpha(0.85); fill(circle(sx, sy, 34.0), if (dark) hex("fff3d0") else hex("fffbe8")) }
        g.save { alpha(0.35); fill(circle(sx, sy, 50.0), if (dark) hex("fff3d0") else hex("fffbe8")) }
        if (dark) repeat(70) { g.fill(java.awt.geom.Rectangle2D.Double(r.nextDouble() * w, r.nextDouble() * gy * 0.7, 2.0, 2.0), Color(255, 255, 255, 120 + r.nextInt(130))) }
        g.fill(path {
            moveTo(0.0, gy)
            var x = 0.0
            while (x <= w + 40) { lineTo(x, gy * (0.5 + r.nextDouble() * 0.18)); x += 40 }
            lineTo(w.toDouble(), gy); closePath()
        }, hex(world.far))
        val mid = hex(world.mid); val md = hex(world.midDark)
        val horizon = gy * 0.62
        when (world.kind) {
            "castle" -> {
                repeat(4) {
                    val tw = 34 + r.nextDouble() * 30; val th = 70 + r.nextDouble() * 80; val tx = r.nextDouble() * w
                    g.fill(java.awt.geom.Rectangle2D.Double(tx, horizon - th, tw, th + gy), mid.shade(-0.06))
                    var k = 0.0
                    while (k < tw) { g.fill(java.awt.geom.Rectangle2D.Double(tx + k, horizon - th - 8, 6.0, 8.0), mid.shade(-0.06)); k += 10 }
                    for (j in 0 until 3) g.fill(rrect(tx + tw / 2 - 4, horizon - th + 16 + j * 24, 8.0, 12.0, 4.0), md.shade(-0.25))
                    g.line(tx + tw / 2, horizon - th - 8, tx + tw / 2, horizon - th - 26, md.shade(-0.2), 1.5f)
                    g.fill(path { moveTo(tx + tw / 2, horizon - th - 26); lineTo(tx + tw / 2 + 12, horizon - th - 22); lineTo(tx + tw / 2, horizon - th - 18); closePath() }, hex("d14b4b"))
                }
                val wy = gy - gy * 0.32
                g.fill(java.awt.geom.Rectangle2D.Double(0.0, wy, w.toDouble(), gy - wy), mid)
                g.fill(java.awt.geom.Rectangle2D.Double(0.0, wy - 8, w.toDouble(), 10.0), mid.shade(0.25))
                var x = 10.0
                while (x < w) { g.fill(rrect(x, wy + 14, 36.0, gy - wy - 14, 18.0), md); x += 54 }
                x = 0.0
                while (x < w) { g.fill(java.awt.geom.Rectangle2D.Double(x, wy + 6, 9.0, gy - wy), mid.shade(0.12)); x += 54 }
            }
            "dunes" -> {
                repeat(3) {
                    val px = r.nextDouble() * w; val pw = 70 + r.nextDouble() * 70
                    g.fill(path { moveTo(px - pw, horizon + 30); lineTo(px, horizon - pw * 0.8); lineTo(px + pw, horizon + 30); closePath() }, mid)
                    g.fill(path { moveTo(px, horizon - pw * 0.8); lineTo(px + pw, horizon + 30); lineTo(px + pw * 0.2, horizon + 30); closePath() }, md)
                }
                for (k in 0..1) g.fill(path {
                    moveTo(0.0, gy); var x = 0.0
                    while (x <= w) { lineTo(x, gy - 40 - k * 20 + sin(x / 70 + k * 2) * 18); x += 10 }
                    lineTo(w.toDouble(), gy); closePath()
                }, if (k == 1) mid else mid.shade(0.1))
                repeat(4) {
                    val cx = r.nextDouble() * w; val ch = 30 + r.nextDouble() * 25
                    g.fillStroke(rrect(cx, gy - ch, 10.0, ch, 5.0), hex("4f8a3a"), 1.5f, hex("4f8a3a").shade(-0.4))
                    g.fillStroke(rrect(cx - 10, gy - ch * 0.7, 8.0, 16.0, 4.0), hex("4f8a3a"), 1.5f, hex("4f8a3a").shade(-0.4))
                }
            }
            "peaks" -> {
                for (i in 0 until 6) {
                    val px = i / 5.0 * w + (r.nextDouble() - 0.5) * 60; val ph = 90 + r.nextDouble() * 90; val pw = 70 + r.nextDouble() * 50
                    g.fill(path { moveTo(px - pw, gy - 20); lineTo(px, gy - 20 - ph); lineTo(px + pw, gy - 20); closePath() }, md)
                    g.fill(path { moveTo(px - pw * 0.35, gy - 20 - ph * 0.65); lineTo(px, gy - 20 - ph); lineTo(px + pw * 0.35, gy - 20 - ph * 0.65); lineTo(px + 10, gy - 20 - ph * 0.55); lineTo(px - 8, gy - 20 - ph * 0.68); closePath() }, mid)
                }
                repeat(9) {
                    val tx = r.nextDouble() * w; val th = 26 + r.nextDouble() * 20
                    g.fill(path { moveTo(tx, gy - th - 8); lineTo(tx - th * 0.4, gy - 6); lineTo(tx + th * 0.4, gy - 6); closePath() }, hex("2f5a4a"))
                    g.fill(path { moveTo(tx, gy - th - 8); lineTo(tx - th * 0.16, gy - th * 0.6); lineTo(tx + th * 0.16, gy - th * 0.6); closePath() }, WHITE)
                }
            }
            "forest" -> {
                for (k in 0..1) repeat(14) {
                    val tx = r.nextDouble() * w; val th = (if (k == 1) 90.0 else 130.0) + r.nextDouble() * 60
                    val c = if (k == 1) mid else hex(world.far).shade(-0.15)
                    for (j in 0 until 3) g.fill(path { moveTo(tx, gy - th + j * th * 0.22); lineTo(tx - th * (0.2 + j * 0.07), gy - th * 0.45 + j * th * 0.2); lineTo(tx + th * (0.2 + j * 0.07), gy - th * 0.45 + j * th * 0.2); closePath() }, c)
                    g.fill(java.awt.geom.Rectangle2D.Double(tx - 3, gy - th * 0.2, 6.0, th * 0.2), c)
                }
                repeat(6) {
                    val mx = r.nextDouble() * w
                    g.fill(java.awt.geom.Rectangle2D.Double(mx - 2, gy - 10, 4.0, 10.0), hex("d8d0c0"))
                    g.fill(path { arcTo(mx, gy - 10, 7.0, 7.0, PI, TAU, start = true); closePath() }, hex("7ef0c8"))
                }
            }
            "lava" -> {
                g.paint = GradientPaint(0f, horizon.toFloat(), hex("ff9a2a"), 0f, gy.toFloat(), hex("c2280e"))
                g.fill(java.awt.geom.Rectangle2D.Double(0.0, horizon + 20, w.toDouble(), gy - horizon))
                repeat(8) {
                    val rx = r.nextDouble() * w; val rh = 60 + r.nextDouble() * 110; val rw = 30 + r.nextDouble() * 40
                    g.fill(path { moveTo(rx - rw, gy); lineTo(rx - rw * 0.3, gy - rh); lineTo(rx + rw * 0.2, gy - rh * 0.8); lineTo(rx + rw, gy); closePath() }, mid)
                    g.stroke = stroke(2f); g.color = hex("ff6a1a")
                    g.draw(path { moveTo(rx - rw * 0.3, gy - rh * 0.8); lineTo(rx - rw * 0.1, gy - rh * 0.5); lineTo(rx - rw * 0.3, gy - rh * 0.25) })
                }
            }
            "ocean" -> {
                repeat(14) {
                    val kx = r.nextDouble() * w; val kh = 60 + r.nextDouble() * 120
                    val kelp = path { moveTo(kx, gy); var y = gy; var x = kx; while (y > gy - kh) { y -= 12; x += sin(y / 15) * 4; lineTo(x, y) } }
                    g.stroke = stroke(5f); g.color = hex("2a8a5a"); g.draw(kelp)
                }
                repeat(6) {
                    val cx = r.nextDouble() * w; val cs = 18 + r.nextDouble() * 22
                    for (k in 0 until 5) g.fill(circle(cx + (k - 2) * cs * 0.35, gy - cs * 0.5 - abs(k - 2) * -cs * 0.15, cs * 0.35), if (k % 2 == 0) mid else md)
                }
                repeat(20) { g.fill(circle(r.nextDouble() * w, r.nextDouble() * gy * 0.8, 1.5 + r.nextDouble() * 2), Color(255, 255, 255, 90)) }
            }
            "sky" -> {
                repeat(5) {
                    val ix = r.nextDouble() * w; val iy = horizon - r.nextDouble() * 120; val iw = 50 + r.nextDouble() * 50
                    g.fill(path { moveTo(ix - iw, iy); lineTo(ix + iw, iy); lineTo(ix + iw * 0.3, iy + iw * 0.7); lineTo(ix - iw * 0.4, iy + iw * 0.5); closePath() }, hex("8a6a4a"))
                    g.fill(Rectangle2D.Double(ix - iw, iy - 6, iw * 2, 8.0), hex(world.groundTop))
                    g.fill(Rectangle2D.Double(ix - 8, iy - 40, 16.0, 34.0), md); g.fill(path { moveTo(ix - 12, iy - 40); lineTo(ix, iy - 56); lineTo(ix + 12, iy - 40); closePath() }, hex("5a8ad0"))
                }
                for (k in 0 until 3) g.fill(path {
                    moveTo(0.0, gy); var x = 0.0
                    while (x <= w) { lineTo(x, gy - 30 - k * 14 + sin(x / 40 + k) * 10); x += 8 }
                    lineTo(w.toDouble(), gy); closePath()
                }, Color(255, 255, 255, 200 - k * 40))
            }
            else -> { // void
                repeat(10) {
                    val cx = r.nextDouble() * w; val cy = horizon - r.nextDouble() * horizon * 0.6; val cs = 8 + r.nextDouble() * 22
                    g.fill(path { moveTo(cx, cy - cs); lineTo(cx + cs * 0.45, cy); lineTo(cx, cy + cs * 0.7); lineTo(cx - cs * 0.45, cy); closePath() }, if (r.nextBoolean()) mid else hex("7ef0ff"))
                }
                repeat(7) {
                    val cx = r.nextDouble() * w; val ch = 40 + r.nextDouble() * 90
                    g.fill(path { moveTo(cx - 14, gy); lineTo(cx - 6, gy - ch); lineTo(cx + 4, gy - ch - 14); lineTo(cx + 14, gy); closePath() }, md)
                    g.fill(path { moveTo(cx - 6, gy - ch); lineTo(cx + 4, gy - ch - 14); lineTo(cx + 2, gy); lineTo(cx - 10, gy); closePath() }, mid)
                }
            }
        }
        g.fill(java.awt.geom.Rectangle2D.Double(0.0, gy, w.toDouble(), h - gy), hex(world.ground))
        g.fill(java.awt.geom.Rectangle2D.Double(0.0, gy - 3, w.toDouble(), 7.0), hex(world.groundTop))
        g.fill(java.awt.geom.Rectangle2D.Double(0.0, gy + 14, w.toDouble(), h - gy), hex(world.dirt))
        var x = 0.0
        while (x < w) { g.fill(java.awt.geom.Rectangle2D.Double(x + r.nextDouble() * 6, gy + 20 + r.nextDouble() * max(1.0, h - gy - 26), 10 + r.nextDouble() * 8, 4.0), hex(world.dirt).shade(0.15)); x += 22 }
        x = 0.0
        while (x < w) { g.fill(java.awt.geom.Rectangle2D.Double(x, gy + 6, 16.0, 3.0), hex(world.ground).shade(-0.15)); x += 28 }
        // soil layer with stones
        g.fill(java.awt.geom.Rectangle2D.Double(0.0, gy + 14, w.toDouble(), 4.0), hex(world.dirt).shade(-0.25))
        repeat(26) {
            val px = r.nextDouble() * w; val py = gy + 24 + r.nextDouble() * max(1.0, h - gy - 34); val pr = 2.5 + r.nextDouble() * 4
            g.fill(ellipse(px, py, pr * 1.4, pr), hex(world.dirt).shade(-0.3))
            g.fill(ellipse(px - pr * 0.3, py - pr * 0.3, pr * 0.7, pr * 0.4), hex(world.dirt).shade(0.25))
        }
        // tufts along the edge of the ground (grass, snow, sand or rock depending on the world)
        val tuft = when (world.kind) {
            "peaks" -> hex("f4f8ff"); "lava" -> hex("5a2a1a"); "void" -> hex("6ad8f0"); "dunes" -> hex("b8963a")
            "ocean" -> hex("2a9a6a"); "forest" -> hex("4a8a5a"); "sky" -> hex("7ac85a"); else -> hex("5aa84a")
        }
        x = r.nextDouble() * 20
        while (x < w) {
            val th = 7 + r.nextDouble() * 7
            for ((dx, k) in listOf(-3.0 to 0.75, 3.0 to 0.85, 0.0 to 1.0)) {
                val c = if (dx == 0.0) tuft.shade(0.18) else tuft.shade(-0.12)
                g.fill(path { moveTo(x + dx - 2, gy + 2); quadTo(x + dx - 1, gy - th * k * 0.5, x + dx + dx * 0.6, gy - th * k); lineTo(x + dx + 2, gy + 2); closePath() }, c)
            }
            if (world.kind == "lava") g.fill(circle(x, gy - th, 1.5), hex("ff8a2a"))
            x += 16 + r.nextDouble() * 30
        }
        // a few flowers / shells / crystals by the ground
        repeat(10) {
            val fx = r.nextDouble() * w; val c = when (world.kind) { "lava" -> hex("ff7a2a"); "void" -> hex("c8a8ff"); "ocean" -> hex("ff9ab0"); "peaks" -> hex("a8d8ff"); else -> listOf(hex("ff6a8a"), hex("ffe34a"), hex("ffffff"), hex("8ac8ff"))[r.nextInt(4)] }
            g.line(fx, gy, fx, gy - 7, hex("4a7a3a"), 1.5f)
            g.fill(circle(fx, gy - 8, 2.5), c); g.fill(circle(fx, gy - 8, 1.0), hex("fff2a0"))
        }
        g.dispose()
        return img
    }

    /** Animated bits over the background (clouds, snow, embers, fireflies). */
    fun ambience(g: Graphics2D, world: World, w: Int, gy: Double, t: Double) {
        if (world.kind == "ocean") for (i in 0 until 12) {
            val x = (i * 79 + sin(t + i) * 10) % w; val y = gy - ((t * (18 + (i % 5) * 6) + i * 50) % gy)
            g.stroke = stroke(1.5f); g.color = Color(220, 240, 255); g.draw(circle(x, y, 2.0 + (i % 3)))
        }
        if (world.kind in setOf("castle", "dunes", "peaks", "sky")) for (i in 0 until 3) {
            val x = ((t * (6 + i * 3) + i * 170) % (w + 160)) - 80; val y = 34.0 + i * 24
            g.fill(ellipse(x, y, 30.0, 9.0), WHITE); g.fill(ellipse(x + 18, y - 6, 18.0, 9.0), WHITE); g.fill(ellipse(x - 16, y - 3, 14.0, 7.0), WHITE)
        }
        if (world.kind == "peaks") for (i in 0 until 30) {
            val x = (i * 97 + sin(t + i) * 20 + t * 10) % w; val y = (i * 53 + t * (20 + (i % 5) * 6)) % gy
            g.fill(java.awt.geom.Rectangle2D.Double(x, y, 2.0, 2.0), WHITE)
        }
        if (world.kind == "lava") for (i in 0 until 20) {
            val x = (i * 83) % w + sin(t * 2 + i) * 8; val y = gy - ((t * (25 + (i % 7) * 8) + i * 40) % gy)
            g.fill(java.awt.geom.Rectangle2D.Double(x, y, 3.0, 3.0), Color(255, 120 + (i % 4) * 30, 40))
        }
        if (world.kind == "forest" || world.kind == "void") for (i in 0 until 14) {
            if (sin(t * 3 + i) < 0) continue
            val x = (i * 71 + sin(t * 0.7 + i) * 30) % w; val y = gy * 0.4 + ((i * 37) % (gy * 0.55)) + cos(t + i) * 10
            g.fill(circle(x, y, 2.0), if (world.kind == "forest") hex("d8ff7a") else hex("9ff4ff"))
        }
    }

    // ==================================================================== small items
    fun coin(g: Graphics2D, x: Double, y: Double, r: Double, spin: Double = 1.0) {
        val sq = Math.round(max(0.15, abs(spin)) * 8) / 8.0 // 8 spin steps are plenty at this size
        val img = cached("coin", sq.toString(), r * 2) { coinNow(it, r, r, r, sq) }
        g.blit(img, Math.round(x - r - 4).toInt(), Math.round(y - r - 4).toInt())
    }

    private fun coinNow(g: Graphics2D, x: Double, y: Double, r: Double, spin: Double) = g.save {
        translate(x, y); scale(spin, 1.0)
        paint = RadialGradientPaint((-r * 0.35).toFloat(), (-r * 0.4).toFloat(), (r * 1.6).toFloat(), floatArrayOf(0f, 0.6f, 1f), arrayOf(hex("fff4b0"), hex("ffc82a"), hex("d08a10")))
        fill(circle(0.0, 0.0, r))
        stroke = stroke(max(1.2f, (r * 0.14).toFloat())); color = hex("8a5a08"); draw(circle(0.0, 0.0, r))
        paint = GradientPaint(0f, (-r * 0.6).toFloat(), hex("e89a14"), 0f, (r * 0.6).toFloat(), hex("ffd040"))
        fill(circle(0.0, 0.0, r * 0.66))
        fill(path { moveTo(-r * 0.4, r * 0.25); lineTo(-r * 0.45, -r * 0.3); lineTo(-r * 0.18, -r * 0.05); lineTo(0.0, -r * 0.38); lineTo(r * 0.18, -r * 0.05); lineTo(r * 0.45, -r * 0.3); lineTo(r * 0.4, r * 0.25); closePath() }, hex("fff2a8"))
        fill(ellipse(-r * 0.35, -r * 0.55, r * 0.3, r * 0.14), Color(255, 255, 255, 170))
    }

    fun gem(g: Graphics2D, x: Double, y: Double, r: Double) =
        g.blit(cached("gem", "", r * 2) { gemNow(it, r, r, r) }, Math.round(x - r - 4).toInt(), Math.round(y - r - 4).toInt())

    private fun gemNow(g: Graphics2D, x: Double, y: Double, r: Double) = g.save {
        translate(x, y)
        val gemShape = path { moveTo(-r, -r * 0.3); lineTo(-r * 0.5, -r * 0.8); lineTo(r * 0.5, -r * 0.8); lineTo(r, -r * 0.3); lineTo(0.0, r); closePath() }
        paint = GradientPaint(0f, (-r).toFloat(), hex("b8f4ff"), 0f, r.toFloat(), hex("1a8ad0")); fill(gemShape)
        stroke = stroke(max(1f, (r * 0.12).toFloat())); color = hex("0a4a7a"); draw(gemShape)
        fill(path { moveTo(-r * 0.5, -r * 0.8); lineTo(-r * 0.15, -r * 0.3); lineTo(-r, -r * 0.3); closePath() }, hex("b5f1ff"))
        fill(path { moveTo(r * 0.15, -r * 0.3); lineTo(r, -r * 0.3); lineTo(0.0, r); closePath() }, hex("1aa6e0"))
    }

    fun relic(g: Graphics2D, x: Double, y: Double, r: Double) =
        g.blit(cached("relic", "", r * 2.6) { relicNow(it, r * 1.3, r * 1.3, r) }, Math.round(x - r * 1.3 - 4).toInt(), Math.round(y - r * 1.3 - 4).toInt())

    private fun relicNow(g: Graphics2D, x: Double, y: Double, r: Double) = g.save {
        translate(x, y)
        val lw = max(1f, (r * 0.12).toFloat())
        for (d in listOf(-1.0, 1.0)) save { translate(d * r * 0.7, -r * 0.35); rotate(d * -0.5); fillStroke(ellipse(0.0, 0.0, r * 0.55, r * 0.28), WHITE, lw) }
        fillStroke(path { moveTo(0.0, r); curveTo(-r * 1.1, 0.0, -r * 0.6, -r * 0.9, 0.0, -r * 0.35); curveTo(r * 0.6, -r * 0.9, r * 1.1, 0.0, 0.0, r) }, hex("e8283a"), lw)
        fillStroke(circle(0.0, -r * 0.45, r * 0.25), hex("ffd23a"), lw)
    }

    fun chest(g: Graphics2D, c: Chest?, x: Double, y: Double, s: Double, body: Color = hex(c?.body ?: "8a5a2a"), band: Color = hex(c?.band ?: "c0c0c8")) =
        g.blit(cached("chest", "${body.rgb}:${band.rgb}", 62 * s) { chestNow(it, 31 * s, 38 * s, s, body, band) },
            Math.round(x - 31 * s - 4).toInt(), Math.round(y - 38 * s - 4).toInt())

    private fun chestNow(g: Graphics2D, x: Double, y: Double, s: Double, body: Color, band: Color) = g.save {
        translate(x, y); scale(s, s)
        fillStroke(rrect(-26.0, -6.0, 52.0, 26.0, 3.0), body, 2.5f)
        fillStroke(path { moveTo(-26.0, -6.0); lineTo(-26.0, -16.0); quadTo(0.0, -34.0, 26.0, -16.0); lineTo(26.0, -6.0); closePath() }, body.shade(0.12), 2.5f)
        fillStroke(rrect(-20.0, -26.0, 7.0, 46.0, 2.0), band, 2.5f)
        fillStroke(rrect(13.0, -26.0, 7.0, 46.0, 2.0), band, 2.5f)
        fillStroke(rrect(-6.0, -10.0, 12.0, 12.0, 2.0), band, 2.5f)
        fill(java.awt.geom.Rectangle2D.Double(-1.5, -6.0, 3.0, 5.0), OUT)
    }

    fun egg(g: Graphics2D, x: Double, y: Double, r: Double) =
        g.blit(cached("egg", "", r * 2.6) { eggNow(it, r * 1.3, r * 1.3, r) }, Math.round(x - r * 1.3 - 4).toInt(), Math.round(y - r * 1.3 - 4).toInt())

    private fun eggNow(g: Graphics2D, x: Double, y: Double, r: Double) = g.save {
        translate(x, y)
        fillStroke(path { moveTo(0.0, -r * 1.25); curveTo(r * 0.95, -r * 1.2, r * 1.05, r * 0.9, 0.0, r); curveTo(-r * 1.05, r * 0.9, -r * 0.95, -r * 1.2, 0.0, -r * 1.25) }, hex("fff4e0"), max(1f, (r * 0.13).toFloat()))
        fill(circle(-r * 0.35, -r * 0.2, r * 0.22), hex("6ad0a0")); fill(circle(r * 0.3, r * 0.35, r * 0.18), hex("ff9a6a")); fill(circle(r * 0.25, -r * 0.6, r * 0.14), hex("6aa0ff"))
    }

    fun wisp(g: Graphics2D, x: Double, y: Double, t: Double) = g.save {
        translate(x, y)
        alpha(0.35); fill(circle(0.0, 0.0, 24.0), hex("a0fff0"))
        alpha(1.0)
        for (d in listOf(-1.0, 1.0)) save { scale(d, 1.0); rotate(sin(t * 20) * 0.4); fill(ellipse(10.0, -6.0, 10.0, 5.0), Color(255, 255, 255, 200)) }
        fill(circle(0.0, 0.0, 9.0), hex("dffffa")); fill(circle(0.0, 0.0, 5.0), WHITE)
    }

    fun skull(g: Graphics2D, x: Double, y: Double, r: Double) = g.save {
        translate(x, y)
        fill(circle(0.0, 0.0, r), OUT)
        fill(path { moveTo(-r * 0.9, -r * 0.3); lineTo(-r * 1.1, -r * 1.2); lineTo(-r * 0.3, -r * 0.8); closePath() }, OUT)
        fill(path { moveTo(r * 0.9, -r * 0.3); lineTo(r * 1.1, -r * 1.2); lineTo(r * 0.3, -r * 0.8); closePath() }, OUT)
        val red = hex("ff3a2a")
        fill(path { moveTo(-r * 0.65, -r * 0.1); lineTo(-r * 0.15, r * 0.15); lineTo(-r * 0.6, r * 0.25); closePath() }, red)
        fill(path { moveTo(r * 0.65, -r * 0.1); lineTo(r * 0.15, r * 0.15); lineTo(r * 0.6, r * 0.25); closePath() }, red)
    }

    // ==================================================================== icons
    private val ICON_BG = mapOf(
        "thunder" to ("2a6fe0" to "8fd0ff"), "frenzy" to ("d04a2a" to "ffb06a"), "warcry" to ("b8861a" to "ffe08a"), "midas" to ("c9a01a" to "fff0a0"),
        "berserk" to ("b3202a" to "ff7a4a"), "eagle" to ("2a8a6a" to "a8ffd8"), "surge" to ("d0502a" to "ffd23a"), "phantom" to ("5a3ab0" to "c8a8ff"),
        "shower" to ("c98a1a" to "ffe58a"), "flask" to ("1a7ab0" to "8ae8ff"), "blade" to ("5a6a80" to "d8e2f0"), "gauntlet" to ("7a4a2a" to "e0b080"),
        "banner" to ("9a2a2a" to "ff8a6a"), "idol" to ("a88a1a" to "ffe680"), "clover" to ("2a8a3a" to "a8f08a"), "fang" to ("6a2a8a" to "e0a8ff"),
        "hourglass" to ("6a5a3a" to "f0dca0"), "crystal" to ("2a5ab0" to "8ad8ff"), "tome" to ("5a2a8a" to "d0a0ff"), "boots" to ("3a5a2a" to "b0e08a"),
        "crown" to ("8a1a3a" to "ffb0c0"), "key" to ("8a6a1a" to "ffe08a"), "prestige" to ("2a3a8a" to "d8e8ff"), "tap" to ("c0402a" to "ffc080"),
        "trophy" to ("a87a10" to "ffe07a"), "moon" to ("1a1a4a" to "5a5aa0"), "clock" to ("8a5a1a" to "ffd08a"), "quest" to ("2a4a8a" to "8ab0ff"), "top" to ("2a6a8a" to "a0e0ff"), "gear" to ("4a4e5e" to "a0a4b4"), "exit" to ("8a1a1a" to "ff6a5a"),
    )

    /** A square icon with a coloured tile background, drawn at (x, y) with size [s]. */
    fun icon(g: Graphics2D, key: String, x: Double, y: Double, s: Double) {
        val img = cached("icon", key, s) { iconNow(it, key, 0.0, 0.0, s) }
        g.blit(img, Math.round(x - 4).toInt(), Math.round(y - 4).toInt())
    }

    // Static pictures (icons, coins) are drawn once into small images and reused every frame.
    private val spriteCache = object : java.util.LinkedHashMap<String, BufferedImage>(128, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, BufferedImage>) = size > 600
    }

    /** A sprite with a 4px margin: [draw] paints with (0, 0) at the sprite's (4, 4). */
    private fun cached(kind: String, key: String, size: Double, draw: (Graphics2D) -> Unit): BufferedImage {
        val k = "$kind:$key:${Math.round(size * 10)}"
        synchronized(spriteCache) { spriteCache[k]?.let { return it } }
        val n = Math.ceil(size).toInt() + 8
        val (img, g) = hiRes(n, n)
        g.translate(4, 4)
        draw(g)
        g.dispose()
        synchronized(spriteCache) { spriteCache[k] = img }
        return img
    }


    private fun iconNow(g: Graphics2D, key: String, x: Double, y: Double, s: Double) = g.save {
        translate(x, y)
        val bg = ICON_BG[key] ?: ("3a3f55" to "7a809a")
        val tile = rrect(0.0, 0.0, s, s, s * 0.2)
        paint = RadialGradientPaint((s * 0.35).toFloat(), (s * 0.3).toFloat(), (s * 0.95).toFloat(), floatArrayOf(0f, 1f), arrayOf(hex(bg.second), hex(bg.first)))
        fill(tile)
        save { translate(s / 2, s / 2); scale(s / 64, s / 64); glyph(this, key, hex(bg.first)) }
        save { clip(tile); fill(ellipse(s * 0.5, -s * 0.05, s * 0.75, s * 0.42), Color(255, 255, 255, 55)) }
        stroke = stroke(max(1.2f, (s / 24).toFloat())); color = hex(bg.first).shade(-0.6); draw(tile)
        stroke = stroke(1f); color = Color(255, 255, 255, 70); draw(rrect(1.5, 1.5, s - 3, s - 3, s * 0.18))
    }

    private fun glyph(g: Graphics2D, key: String, bg: Color) = with(g) {
        when (key) {
            "thunder", "surge" -> {
                fillStroke(path { moveTo(4.0, -26.0); lineTo(-14.0, 4.0); lineTo(-1.0, 4.0); lineTo(-6.0, 26.0); lineTo(14.0, -6.0); lineTo(1.0, -6.0); closePath() }, hex("fff36a"), 3f)
                if (key == "surge") fillStroke(path { moveTo(14.0, 4.0); lineTo(24.0, 18.0); lineTo(4.0, 18.0); closePath() }, hex("ff5a2a"), 3f)
            }
            "frenzy", "tap", "blade", "berserk" -> {
                save {
                    rotate(0.75)
                    fillStroke(path { moveTo(-4.0, 12.0); lineTo(-4.0, -22.0); lineTo(0.0, -30.0); lineTo(4.0, -22.0); lineTo(4.0, 12.0); closePath() }, if (key == "berserk") hex("ffb07a") else hex("eef3fa"), 3f)
                    fillStroke(rrect(-11.0, 10.0, 22.0, 5.0, 2.0), hex("e8b33a"), 3f)
                    fillStroke(rrect(-3.0, 14.0, 6.0, 12.0, 2.0), hex("6a3a22"), 3f)
                }
                if (key == "frenzy") for (i in 0 until 3) { stroke = stroke(3f); color = WHITE; draw(arc(0.0, 0.0, 18.0 + i * 6, -2.6 + i * 0.2, -1.6 + i * 0.2)) }
            }
            "warcry", "banner" -> {
                line(-14.0, 26.0, -14.0, -26.0, OUT, 4f)
                fillStroke(path { moveTo(-14.0, -24.0); lineTo(18.0, -24.0); lineTo(10.0, -12.0); lineTo(18.0, 0.0); lineTo(-14.0, 0.0); closePath() }, if (key == "banner") hex("e83a3a") else hex("ffd23a"), 3f)
                fill(circle(0.0, -12.0, 4.0), WHITE)
            }
            "midas", "shower" -> {
                coin(this, -8.0, 6.0, 14.0); coin(this, 10.0, -4.0, 14.0)
                if (key == "shower") coin(this, 0.0, -18.0, 10.0)
            }
            "idol", "trophy" -> {
                if (key == "trophy") {
                    fillStroke(path { moveTo(-16.0, -22.0); lineTo(16.0, -22.0); quadTo(16.0, 6.0, 0.0, 8.0); quadTo(-16.0, 6.0, -16.0, -22.0); closePath() }, hex("ffd23a"), 3f)
                    fillStroke(rrect(-4.0, 6.0, 8.0, 10.0, 2.0), hex("e8a82a"), 3f)
                    fillStroke(rrect(-14.0, 16.0, 28.0, 8.0, 2.0), hex("8a5a2a"), 3f)
                } else {
                    fillStroke(circle(0.0, -14.0, 9.0), hex("ffd23a"), 3f); fillStroke(rrect(-12.0, -4.0, 24.0, 26.0, 6.0), hex("ffd23a"), 3f)
                    fill(circle(-3.0, -15.0, 1.8), OUT); fill(circle(3.0, -15.0, 1.8), OUT)
                }
            }
            "eagle" -> {
                fillStroke(path { moveTo(-26.0, 0.0); quadTo(0.0, -24.0, 26.0, 0.0); quadTo(0.0, 24.0, -26.0, 0.0) }, WHITE, 3f)
                fillStroke(circle(0.0, 0.0, 10.0), hex("2a9a6a"), 3f); fill(circle(0.0, 0.0, 4.5), OUT)
            }
            "phantom" -> {
                fillStroke(rrect(-14.0, -4.0, 28.0, 26.0, 8.0), WHITE, 3f)
                for (i in 0 until 4) fillStroke(rrect(-14.0 + i * 7.5, -24.0 + abs(i - 1.5) * 4, 6.0, 24.0, 3.0), WHITE, 3f)
            }
            "flask" -> {
                fillStroke(path { moveTo(-6.0, -22.0); lineTo(-6.0, -8.0); quadTo(-22.0, 0.0, -16.0, 16.0); quadTo(0.0, 28.0, 16.0, 16.0); quadTo(22.0, 0.0, 6.0, -8.0); lineTo(6.0, -22.0); closePath() }, hex("5ad8ff"), 3f)
                fillStroke(rrect(-8.0, -28.0, 16.0, 7.0, 2.0), hex("c98a4a"), 3f)
            }
            "crystal" -> {
                fillStroke(path { moveTo(0.0, -28.0); lineTo(16.0, -6.0); lineTo(0.0, 28.0); lineTo(-16.0, -6.0); closePath() }, hex("7ae0ff"), 3f)
                fill(path { moveTo(0.0, -28.0); lineTo(-6.0, -6.0); lineTo(0.0, 22.0); lineTo(-16.0, -6.0); closePath() }, WHITE.alpha(0.6))
            }
            "gauntlet" -> {
                fillStroke(rrect(-14.0, -8.0, 28.0, 24.0, 6.0), hex("c0c8d4"), 3f)
                for (i in 0 until 4) fillStroke(rrect(-14.0 + i * 7, -20.0, 7.0, 14.0, 3.0), hex("c0c8d4"), 3f)
                fillStroke(rrect(-12.0, 16.0, 24.0, 10.0, 3.0), hex("8a5a3a"), 3f)
            }
            "clover" -> {
                for (i in 0 until 4) { val a = i * PI / 2; fillStroke(circle(cos(a) * 10, sin(a) * 10 - 4, 10.0), hex("5ad84a"), 3f) }
                stroke = stroke(4f); color = hex("2a6a1a"); draw(path { moveTo(0.0, 4.0); quadTo(4.0, 18.0, 12.0, 26.0) })
            }
            "fang" -> fillStroke(path { moveTo(-12.0, -24.0); quadTo(14.0, -24.0, 12.0, -20.0); quadTo(10.0, 6.0, -6.0, 28.0); quadTo(0.0, 0.0, -12.0, -24.0) }, hex("f6efe0"), 3f)
            "hourglass" -> {
                fillStroke(rrect(-18.0, -28.0, 36.0, 6.0, 2.0), hex("c98a4a"), 3f); fillStroke(rrect(-18.0, 22.0, 36.0, 6.0, 2.0), hex("c98a4a"), 3f)
                fillStroke(path { moveTo(-14.0, -22.0); lineTo(14.0, -22.0); lineTo(2.0, 0.0); lineTo(14.0, 22.0); lineTo(-14.0, 22.0); lineTo(-2.0, 0.0); closePath() }, hex("dcefff"), 3f)
                fill(path { moveTo(-8.0, 21.0); lineTo(8.0, 21.0); lineTo(0.0, 10.0); closePath() }, hex("f2c94c"))
            }
            "tome" -> {
                fillStroke(rrect(-18.0, -22.0, 36.0, 44.0, 4.0), hex("7a3ab0"), 3f)
                fill(java.awt.geom.Rectangle2D.Double(12.0, -18.0, 5.0, 36.0), hex("f2e8d0"))
                fill(path { moveTo(-2.0, -10.0); lineTo(2.0, -2.0); lineTo(10.0, 0.0); lineTo(2.0, 2.0); lineTo(-2.0, 10.0); lineTo(-6.0, 2.0); lineTo(-14.0, 0.0); lineTo(-6.0, -2.0); closePath() }, hex("ffd23a"))
            }
            "boots" -> {
                fillStroke(path { moveTo(-10.0, -24.0); lineTo(6.0, -24.0); lineTo(6.0, 8.0); lineTo(22.0, 12.0); lineTo(22.0, 22.0); lineTo(-10.0, 22.0); closePath() }, hex("8a5a2a"), 3f)
                fill(path { moveTo(-10.0, -18.0); lineTo(-26.0, -24.0); lineTo(-22.0, -14.0); lineTo(-10.0, -10.0); closePath() }, WHITE)
            }
            "crown" -> {
                fillStroke(path { moveTo(-22.0, 16.0); lineTo(-24.0, -14.0); lineTo(-10.0, 0.0); lineTo(0.0, -20.0); lineTo(10.0, 0.0); lineTo(24.0, -14.0); lineTo(22.0, 16.0); closePath() }, hex("ffcf3a"), 3f)
                fillStroke(circle(0.0, 6.0, 4.0), hex("e63950"), 3f)
            }
            "key" -> {
                fillStroke(circle(-10.0, -10.0, 11.0), hex("ffd23a"), 3f); fill(circle(-10.0, -10.0, 4.0), bg)
                save { translate(-2.0, -2.0); rotate(0.785); fillStroke(rrect(0.0, -3.0, 30.0, 6.0, 2.0), hex("ffd23a"), 3f); fillStroke(rrect(18.0, 2.0, 5.0, 8.0, 1.0), hex("ffd23a"), 3f) }
            }
            "prestige" -> {
                fillStroke(path { moveTo(0.0, -26.0); lineTo(10.0, 0.0); lineTo(4.0, 0.0); lineTo(4.0, 26.0); lineTo(-4.0, 26.0); lineTo(-4.0, 0.0); lineTo(-10.0, 0.0); closePath() }, WHITE, 3f)
            }
            "top" -> {
                fillStroke(rrect(-24.0, 0.0, 14.0, 22.0, 2.0), hex("c0c8d2"), 3f)
                fillStroke(rrect(-7.0, -14.0, 14.0, 36.0, 2.0), hex("ffd23a"), 3f)
                fillStroke(rrect(10.0, 6.0, 14.0, 16.0, 2.0), hex("cd7f32"), 3f)
            }
            "gear" -> {
                fillStroke(path { for (i in 0..16) { val a = i / 16.0 * TAU; val r = if (i % 2 == 1) 15.0 else 22.0; if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r) }; closePath() }, hex("c8ccd6"), 3f)
                fillStroke(circle(0.0, 0.0, 7.0), hex("5a5e6e"), 3f)
            }
            "moon" -> {
                fill(path { arcTo(0.0, 0.0, 22.0, 22.0, PI * 0.35, PI * 1.65, start = true); arcTo(10.0, -2.0, 18.0, 18.0, PI * 1.55, PI * 0.45); closePath() }, hex("fff0a8"))
                for ((sx, sy) in listOf(14.0 to -18.0, 20.0 to 4.0, 8.0 to 18.0)) fill(circle(sx, sy, 2.0), Color.WHITE)
            }
            "clock" -> {
                fillStroke(circle(0.0, 0.0, 22.0), hex("fff4e0"), 3f)
                line(0.0, 0.0, 0.0, -14.0, OUT, 3.5f); line(0.0, 0.0, 10.0, 6.0, OUT, 3.5f)
                fill(circle(0.0, 0.0, 3.0), hex("c0392b"))
            }
            "quest" -> {
                fillStroke(rrect(-18.0, -24.0, 36.0, 48.0, 4.0), hex("f2e8d0"), 3f)
                for (i in 0 until 3) {
                    fillStroke(rrect(-12.0, -14.0 + i * 13, 8.0, 8.0, 1.5), Color.WHITE, 2f)
                    line(-1.0, -10.0 + i * 13, 12.0, -10.0 + i * 13, hex("8a7a6a"), 2.5f)
                }
                line(-11.0, -10.0, -8.0, -7.0, hex("2a9a3a"), 2.5f); line(-8.0, -7.0, -3.0, -15.0, hex("2a9a3a"), 2.5f)
            }
            "exit" -> {
                line(-12.0, -12.0, 12.0, 12.0, WHITE, 7f); line(12.0, -12.0, -12.0, 12.0, WHITE, 7f)
            }
        }
    }

    fun gearIcon(g: Graphics2D, gear: Gear, x: Double, y: Double, s: Double, owned: Boolean) =
        g.blit(cached("gear", "${gear.id}:$owned", s) { gearIconNow(it, gear, 0.0, 0.0, s, owned) }, Math.round(x - 4).toInt(), Math.round(y - 4).toInt())

    private fun gearIconNow(g: Graphics2D, gear: Gear, x: Double, y: Double, s: Double, owned: Boolean) = g.save {
        translate(x, y)
        val rc = hex(RARITIES[gear.r].color)
        paint = GradientPaint(0f, 0f, rc.shade(-0.35), 0f, s.toFloat(), rc.shade(-0.65))
        fill(rrect(0.0, 0.0, s, s, s * 0.14))
        stroke = stroke(2.5f); color = rc; draw(rrect(0.0, 0.0, s, s, s * 0.14))
        translate(s / 2, s / 2); scale(s / 64, s / 64)
        if (!owned) alpha(0.25)
        when (gear.slot) {
            "weapon" -> save { rotate(0.78); translate(0.0, 18.0); drawWeapon(this, "sword", hex(gear.blade ?: "dfe6ee"), gear.glow?.let(::hex)) }
            "helm" -> save { translate(0.0, 12.0); fillStroke(circle(0.0, 0.0, 13.0), hex("f2c9a0"), 2.5f); drawHelmGear(this, gear.helm ?: "cap", hex(gear.color ?: "888888"), 0.0) }
            "armor" -> {
                val o = hex(gear.outfit ?: "888888")
                fillStroke(rrect(-16.0, -18.0, 32.0, 36.0, 9.0), o, 2.5f)
                fill(java.awt.geom.Rectangle2D.Double(-15.0, 4.0, 30.0, 5.0), hex(gear.trim ?: "cccccc"))
                fillStroke(rrect(-22.0, -18.0, 10.0, 14.0, 5.0), o, 2.5f); fillStroke(rrect(12.0, -18.0, 10.0, 14.0, 5.0), o, 2.5f)
            }
            else -> {
                val c = hex(gear.aura ?: "ffffff")
                save { alpha(0.4); fill(circle(0.0, 0.0, 22.0), c) }
                fill(circle(0.0, 0.0, 10.0), WHITE)
                for (i in 0 until 6) { val a = i / 6.0 * TAU; fill(circle(cos(a) * 20, sin(a) * 20, 3.5), c) }
            }
        }
    }

    fun heroPortrait(g: Graphics2D, look: Look, x: Double, y: Double, s: Double, bg: Color) =
        g.blit(cached("hero", "$look:${bg.rgb}", s) { heroPortraitNow(it, look, 0.0, 0.0, s, bg) }, Math.round(x - 4).toInt(), Math.round(y - 4).toInt())

    private fun heroPortraitNow(g: Graphics2D, look: Look, x: Double, y: Double, s: Double, bg: Color) = g.save {
        paint = GradientPaint(x.toFloat(), y.toFloat(), bg.shade(0.25), x.toFloat(), (y + s).toFloat(), bg.shade(-0.25))
        val box = rrect(x, y, s, s, s * 0.14)
        fill(box)
        clip(box)
        drawChibi(this, look.copy(cape = null, aura = null), x + s * 0.47, y + s * 1.32, s / 46, 0.0, 0.0)
    }

    fun petPortrait(g: Graphics2D, p: Pet, x: Double, y: Double, s: Double, owned: Boolean) =
        g.blit(cached("petp", "${p.id}:$owned", s) { petPortraitNow(it, p, 0.0, 0.0, s, owned) }, Math.round(x - 4).toInt(), Math.round(y - 4).toInt())

    private fun petPortraitNow(g: Graphics2D, p: Pet, x: Double, y: Double, s: Double, owned: Boolean) = g.save {
        paint = GradientPaint(x.toFloat(), y.toFloat(), hex("4aa3d0"), x.toFloat(), (y + s).toFloat(), hex("2a5f8a"))
        fill(rrect(x, y, s, s, s * 0.14))
        if (!owned) alpha(0.35)
        drawPet(this, p, x + s / 2, y + s / 2 + 3, s / 72, 0.3)
    }
}
