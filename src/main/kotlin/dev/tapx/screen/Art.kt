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
        l.beard?.let { g.fillStroke(path { moveTo(-4.0, hy + 4); quadTo(4.0, hy + 22, 13.0, hy + 4); quadTo(6.0, hy + 8, -4.0, hy + 4) }, hex(it)) }
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

    /** A chibi figure facing right with feet at (x, y). [atk] is attack progress 0..1. */
    fun drawChibi(g: Graphics2D, l: Look, x: Double, y: Double, s: Double, t: Double, atk: Double = 0.0) = g.save {
        translate(x, y); scale(s, s)
        val bob = sin(t * 5 + l.phase) * 1.2
        val lean = if (atk > 0 && atk < 1) sin(atk * PI) * 4 else 0.0
        fill(ellipse(0.0, 0.0, 17.0, 4.0), Color(0, 0, 0, 60))
        translate(lean, 0.0)
        l.cape?.let {
            val w = sin(t * 6 + l.phase) * 4
            fillStroke(path { moveTo(-5.0, -33 + bob); quadTo(-22.0, -28 + bob, -34 + w, -18 + w * 0.5); lineTo(-30 + w, -10.0); quadTo(-18.0, -16.0, -2.0, -24 + bob); closePath() }, hex(it))
        }
        l.aura?.let {
            val c = hex(it)
            for (i in 0 until 6) {
                val a = t * 1.6 + i / 6.0 * TAU
                fill(circle(cos(a) * 22, -28 + sin(a) * 8 + sin(t * 4 + i) * 3, 2.4), c)
            }
        }
        val step = if (atk > 0 && atk < 1) sin(atk * PI) * 3 else 0.0
        val pants = hex(l.pants); val boots = hex(l.boots)
        fillStroke(rrect(-8 - step, -14.0, 6.0, 12.0, 2.0), pants)
        fillStroke(rrect(2 + step, -14.0, 6.0, 12.0, 2.0), pants)
        fillStroke(rrect(-9 - step, -4.0, 9.0, 5.0, 2.0), boots)
        fillStroke(rrect(1 + step, -4.0, 10.0, 5.0, 2.0), boots)
        fillStroke(rrect(-10.0, -35 + bob, 20.0, 23.0, 6.0), hex(l.outfit))
        fill(java.awt.geom.Rectangle2D.Double(-9.0, -18 + bob, 18.0, 3.0), hex(l.trim))
        fill(java.awt.geom.Rectangle2D.Double(-8.0, -33 + bob, 4.0, 13.0), WHITE.alpha(0.18))
        val hy = -47 + bob
        val skin = hex(l.skin)
        if (l.hairStyle == "hood") {
            fillStroke(circle(-1.0, hy, 15.0), hex(l.hood ?: "2e3550"))
            fillStroke(ellipse(3.0, hy + 2, 9.0, 9.5), skin)
        } else {
            drawHair(this, l, hy, true)
            fillStroke(circle(0.0, hy, 12.5), skin)
        }
        if (l.hairStyle != "helmet" && l.helmGear != "visor" && l.helmGear != "horned") {
            fill(ellipse(3.0, hy + 1, 1.6, 2.6), OUT)
            fill(ellipse(9.0, hy + 1, 1.5, 2.5), OUT)
            fill(circle(8.0, hy + 6, 2.5), Color(255, 120, 120, 90))
        }
        if (l.hairStyle != "hood") drawHair(this, l, hy, false)
        l.helmGear?.let { drawHelmGear(this, it, hex(l.helmColor ?: "999999"), hy) }
        save {
            translate(7.0, -25 + bob)
            rotate(weaponAngle(l.weapon, atk))
            drawWeapon(this, l.weapon, l.blade?.let(::hex) ?: hex("e6edf5"), l.bladeGlow?.let(::hex), l.glow?.let(::hex))
            fillStroke(circle(0.0, 0.0, 3.4), skin, 2f)
        }
    }

    // ==================================================================== monsters
    class MonsterSpec(
        val shape: String, val hue: Double, val w: Double, val h: Double,
        val color: Color, val dark: Color, val light: Color, val belly: Color, val accent: Color,
        val eyes: Int, val eyeR: Double, val mouth: String, val horns: String?, val spikes: Boolean,
        val arms: Boolean, val legs: Boolean, val belly2: Boolean, val spots: Boolean, val float: Boolean,
        val boss: Boolean, val mimic: Boolean, val spotSeed: Int,
    )

    private val SHAPES = listOf("blob", "tower", "cone", "orb", "brute", "slime")

    fun genMonster(seed: Long, boss: Boolean, mimic: Boolean): MonsterSpec {
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
            r.nextDouble() < 0.5, r.nextDouble() < 0.35, shape == "orb", boss, mimic, r.nextInt(1_000_000),
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

    private fun drawEye(g: Graphics2D, x: Double, y: Double, r: Double, blink: Boolean, angry: Boolean) {
        if (blink) { g.line(x - r, y, x + r, y, OUT, 2.5f); return }
        g.fillStroke(circle(x, y, r), WHITE, 2.5f)
        g.fill(circle(x - r * 0.3, y + r * 0.05, r * 0.52), OUT)
        g.fill(circle(x - r * 0.45, y - r * 0.2, r * 0.17), WHITE)
        if (angry) g.line(x - r * 1.1, y - r * 1.25, x + r * 1.1, y - r * 0.75, OUT, 3.5f)
    }

    /** Total drawn height above the ground, for HUD placement. */
    fun monsterHeight(sp: MonsterSpec) = if (sp.mimic) 120.0 else sp.h * (if (sp.boss) 1.22 else 1.0) + (if (sp.legs) 16 else 0) + (if (sp.float) 24 else 0)

    fun drawMonster(g: Graphics2D, sp: MonsterSpec, x: Double, gy: Double, t: Double, hitT: Double, deathT: Double, age: Double, scaleAll: Double) {
        val spawn = clamp(age * 4, 0.0, 1.0)
        if (sp.mimic) return drawMimic(g, x, gy, t, hitT, deathT, spawn, scaleAll)
        val scale = (if (sp.boss) 1.22 else 1.0) * (1 - deathT * 0.6) * (0.4 + 0.6 * spawn) * scaleAll
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
                paint = RadialGradientPaint(0f, (-h / 2).toFloat(), w.toFloat(), floatArrayOf(0f, 1f), arrayOf(hex("ff3a3a"), Color(255, 0, 0, 0)))
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
            val body = bodyPath(sp)
            fill(body, sp.color)
            save {
                clip(body)
                fill(ellipse(w * 0.42, -h * 0.35, w * 0.4, h * 0.75), sp.dark)
                fill(ellipse(-w * 0.22, -h * 0.78, w * 0.16, h * 0.09), sp.light)
                if (sp.belly2) fill(ellipse(0.0, -h * 0.12, w * 0.32, h * 0.32), sp.belly)
                if (sp.spots) {
                    val r2 = Random(sp.spotSeed)
                    repeat(6) { fill(circle((r2.nextDouble() - 0.5) * w * 0.8, -r2.nextDouble() * h * 0.9, 4 + r2.nextDouble() * 7), sp.dark) }
                }
                if (hitT > 0) fill(java.awt.geom.Rectangle2D.Double(-w, -h * 1.2, w * 2, h * 1.4), WHITE.alpha(hitT * 0.55))
            }
            stroke = stroke(4f); color = OUT; draw(body)
            val blink = (t + sp.hue * 0.01) % 3.7 < 0.12
            val er = sp.eyeR
            when (sp.eyes) {
                1 -> drawEye(this, 0.0, fy, er * 1.5, blink, sp.boss)
                2 -> { drawEye(this, -er * 1.25, fy, er, blink, sp.boss); drawEye(this, er * 1.25, fy, er, blink, sp.boss) }
                else -> { drawEye(this, -er * 1.9, fy + 2, er * 0.85, blink, sp.boss); drawEye(this, 0.0, fy - er * 0.6, er, blink, sp.boss); drawEye(this, er * 1.9, fy + 2, er * 0.85, blink, sp.boss) }
            }
            val my = fy + er * 2 + 4
            val open = 1 + hitT * 0.8
            val mouthC = hex("4a1020")
            when (sp.mouth) {
                "grin" -> {
                    fillStroke(path { moveTo(-20.0, my); quadTo(0.0, my + 22 * open, 20.0, my); closePath() }, mouthC, 3.5f)
                    fill(java.awt.geom.Rectangle2D.Double(-9.0, my + 1, 7.0, 6.0), WHITE); fill(java.awt.geom.Rectangle2D.Double(2.0, my + 1, 7.0, 6.0), WHITE)
                }
                "fangs" -> {
                    line(-18.0, my, 18.0, my, OUT, 3.5f)
                    for (d in listOf(-1.0, 1.0)) fillStroke(path { moveTo(d * 12, my); lineTo(d * 8, my + 11 * open); lineTo(d * 4, my); closePath() }, WHITE, 3f)
                }
                "o" -> fillStroke(ellipse(0.0, my + 4, 7.0, 8 * open), mouthC, 3.5f)
                "teeth" -> {
                    fillStroke(rrect(-22.0, my - 2, 44.0, 14 * open, 5.0), mouthC, 3.5f)
                    for (i in 0 until 5) fill(path { moveTo(-20 + i * 8.5, my - 1); lineTo(-16 + i * 8.5, my + 6); lineTo(-12 + i * 8.5, my - 1); closePath() }, WHITE)
                }
                else -> { stroke = stroke(3.5f); color = OUT; draw(arc(0.0, my - 4, 12.0, 0.2, PI - 0.2)) }
            }
            if (sp.boss) {
                val top = if (sp.shape == "orb") -h else -h - (if (sp.shape == "cone") -4 else 2)
                fillStroke(path { moveTo(-20.0, top + 4); lineTo(-24.0, top - 22); lineTo(-10.0, top - 10); lineTo(0.0, top - 28); lineTo(10.0, top - 10); lineTo(24.0, top - 22); lineTo(20.0, top + 4); closePath() }, hex("ffcf3a"), 3f)
                fillStroke(circle(0.0, top - 6, 4.0), hex("e63950"), 3f)
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
    fun drawPet(g: Graphics2D, p: Pet, x: Double, y: Double, s: Double, t: Double, attack: Boolean = false) = g.save {
        translate(x, y); scale(s, s)
        val flap = sin(t * 14) * 0.5
        val wing = hex(p.wing); val body = hex(p.body)
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
        fill(ellipse(0.0, 8.0, 10.0, 8.0), hex(p.belly))
        if (p.sleepy) { line(-9.0, -3.0, -3.0, -3.0, OUT, 2f); line(3.0, -3.0, 9.0, -3.0, OUT, 2f) }
        else {
            val er = if (attack) 3.6 else 2.6
            fill(circle(-6.0, -3.0, er), OUT); fill(circle(6.0, -3.0, er), OUT)
            fill(circle(-6.8, -4.0, 0.9), WHITE); fill(circle(5.2, -4.0, 0.9), WHITE)
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
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
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
        g.dispose()
        return img
    }

    /** Animated bits over the background (clouds, snow, embers, fireflies). */
    fun ambience(g: Graphics2D, world: World, w: Int, gy: Double, t: Double) {
        if (world.kind in setOf("castle", "dunes", "peaks")) for (i in 0 until 3) {
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
    fun coin(g: Graphics2D, x: Double, y: Double, r: Double, spin: Double = 1.0) = g.save {
        translate(x, y); scale(max(0.15, abs(spin)), 1.0)
        fillStroke(circle(0.0, 0.0, r), hex("ffcd3a"), max(1.2f, (r * 0.18).toFloat()), hex("a86a0a"))
        fill(circle(0.0, 0.0, r * 0.65), hex("f2a81e"))
        fill(path { moveTo(-r * 0.4, r * 0.25); lineTo(-r * 0.45, -r * 0.3); lineTo(-r * 0.18, -r * 0.05); lineTo(0.0, -r * 0.38); lineTo(r * 0.18, -r * 0.05); lineTo(r * 0.45, -r * 0.3); lineTo(r * 0.4, r * 0.25); closePath() }, hex("ffe58a"))
    }

    fun gem(g: Graphics2D, x: Double, y: Double, r: Double) = g.save {
        translate(x, y)
        fillStroke(path { moveTo(-r, -r * 0.3); lineTo(-r * 0.5, -r * 0.8); lineTo(r * 0.5, -r * 0.8); lineTo(r, -r * 0.3); lineTo(0.0, r); closePath() }, hex("4ad8ff"), max(1f, (r * 0.12).toFloat()), hex("0a5a8a"))
        fill(path { moveTo(-r * 0.5, -r * 0.8); lineTo(-r * 0.15, -r * 0.3); lineTo(-r, -r * 0.3); closePath() }, hex("b5f1ff"))
        fill(path { moveTo(r * 0.15, -r * 0.3); lineTo(r, -r * 0.3); lineTo(0.0, r); closePath() }, hex("1aa6e0"))
    }

    fun relic(g: Graphics2D, x: Double, y: Double, r: Double) = g.save {
        translate(x, y)
        val lw = max(1f, (r * 0.12).toFloat())
        for (d in listOf(-1.0, 1.0)) save { translate(d * r * 0.7, -r * 0.35); rotate(d * -0.5); fillStroke(ellipse(0.0, 0.0, r * 0.55, r * 0.28), WHITE, lw) }
        fillStroke(path { moveTo(0.0, r); curveTo(-r * 1.1, 0.0, -r * 0.6, -r * 0.9, 0.0, -r * 0.35); curveTo(r * 0.6, -r * 0.9, r * 1.1, 0.0, 0.0, r) }, hex("e8283a"), lw)
        fillStroke(circle(0.0, -r * 0.45, r * 0.25), hex("ffd23a"), lw)
    }

    fun chest(g: Graphics2D, c: Chest?, x: Double, y: Double, s: Double, body: Color = hex(c?.body ?: "8a5a2a"), band: Color = hex(c?.band ?: "c0c0c8")) = g.save {
        translate(x, y); scale(s, s)
        fillStroke(rrect(-26.0, -6.0, 52.0, 26.0, 3.0), body, 2.5f)
        fillStroke(path { moveTo(-26.0, -6.0); lineTo(-26.0, -16.0); quadTo(0.0, -34.0, 26.0, -16.0); lineTo(26.0, -6.0); closePath() }, body.shade(0.12), 2.5f)
        fillStroke(rrect(-20.0, -26.0, 7.0, 46.0, 2.0), band, 2.5f)
        fillStroke(rrect(13.0, -26.0, 7.0, 46.0, 2.0), band, 2.5f)
        fillStroke(rrect(-6.0, -10.0, 12.0, 12.0, 2.0), band, 2.5f)
        fill(java.awt.geom.Rectangle2D.Double(-1.5, -6.0, 3.0, 5.0), OUT)
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
        "trophy" to ("a87a10" to "ffe07a"), "top" to ("2a6a8a" to "a0e0ff"), "gear" to ("4a4e5e" to "a0a4b4"), "exit" to ("8a1a1a" to "ff6a5a"),
    )

    /** A square icon with a coloured tile background, drawn at (x, y) with size [s]. */
    fun icon(g: Graphics2D, key: String, x: Double, y: Double, s: Double) = g.save {
        translate(x, y)
        val bg = ICON_BG[key] ?: ("3a3f55" to "7a809a")
        paint = GradientPaint(0f, 0f, hex(bg.second), s.toFloat(), s.toFloat(), hex(bg.first))
        fill(rrect(0.0, 0.0, s, s, s * 0.16))
        stroke = stroke(2f); color = Color(0, 0, 0, 90); draw(rrect(0.0, 0.0, s, s, s * 0.16))
        translate(s / 2, s / 2); scale(s / 64, s / 64)
        glyph(this, key, hex(bg.first))
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
            "exit" -> {
                line(-12.0, -12.0, 12.0, 12.0, WHITE, 7f); line(12.0, -12.0, -12.0, 12.0, WHITE, 7f)
            }
        }
    }

    fun gearIcon(g: Graphics2D, gear: Gear, x: Double, y: Double, s: Double, owned: Boolean) = g.save {
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

    fun heroPortrait(g: Graphics2D, look: Look, x: Double, y: Double, s: Double, bg: Color) = g.save {
        paint = GradientPaint(x.toFloat(), y.toFloat(), bg.shade(0.25), x.toFloat(), (y + s).toFloat(), bg.shade(-0.25))
        val box = rrect(x, y, s, s, s * 0.14)
        fill(box)
        clip(box)
        drawChibi(this, look.copy(cape = null, aura = null), x + s * 0.47, y + s * 1.32, s / 46, 0.0, 0.0)
    }

    fun petPortrait(g: Graphics2D, p: Pet, x: Double, y: Double, s: Double, owned: Boolean) = g.save {
        paint = GradientPaint(x.toFloat(), y.toFloat(), hex("4aa3d0"), x.toFloat(), (y + s).toFloat(), hex("2a5f8a"))
        fill(rrect(x, y, s, s, s * 0.14))
        if (!owned) alpha(0.35)
        drawPet(this, p, x + s / 2, y + s / 2 + 3, s / 72, 0.3)
    }
}
