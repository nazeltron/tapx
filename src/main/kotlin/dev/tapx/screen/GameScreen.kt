package dev.tapx.screen

import dev.tapx.*
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** What the screen needs from its owner (sounds and leaving the game). */
interface ScreenHost {
    val game: Game
    val playerName: String
    val version: String
    fun sfx(s: Sfx)
    fun exit()
    fun leaderboard(): List<TopEntry>
    fun resetProgress()
    /** Whether players may leave the game screen (EXIT button). */
    val allowExit: Boolean
    /** Change how big the screen looks (moves the camera); 1.0 = sized for the configured FOV. */
    fun setZoom(zoom: Double)
}

enum class Sfx { HIT, CRIT, KILL, BOSS, BOSS_KILL, LEVEL_UP, BUY, CLICK, ERROR, THUNDER, SPELL, PET, CHEST, LEGENDARY, EQUIP, WISP, FAIL, ACHIEVEMENT, COIN }

class TopEntry(val uuid: java.util.UUID, val name: String, val best: Int, val prestiges: Int)

/**
 * The whole 2D game screen: draws a frame into an image (battle on the left, menus on the right,
 * pop-ups on top) and turns clicks at screen pixels into game actions.
 */
class GameScreen(private val host: ScreenHost, val width: Int, val height: Int) : GameEvents {
    private val g get() = host.game
    private val d get() = host.game.d

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val pixels: IntArray = (image.raster.dataBuffer as DataBufferInt).data
    private val gfx: Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    // ------------------------------------------------------------------ layout
    private val BX = 6; private val BY = 6; private val BW = 500; private val BH = 500
    private val RX = 512; private val RY = 6; private val RW = 378; private val RH = 500
    private val GY = 400.0          // ground line inside the battle panel
    private val U = 1.1             // scene scale
    private val LIST_Y = 112; private val LIST_H = 318

    // ------------------------------------------------------------------ UI state
    // A mouse cursor (driven by the player's head rotation) plus keyboard jumps between buttons.
    private var curX = width / 2.0
    private var curY = height / 2.0
    private var selected: String? = null // button under the cursor (from the last frame)
    private val held = HashMap<String, Double>() // direction -> seconds until auto-repeat
    private var t = 0.0
    /** Idle animations (breathing, bobbing, clouds) step 4 times a second to save bandwidth. */
    private val ta get() = floor(t * 4) / 4
    private var tab = "hero"
    private var scroll = 0.0
    /** Dragging the scroll bar: the list follows the cursor until a click or the cursor leaves the bar. */
    private var dragging = false
    private var listContentH = 0
    private var modal: Modal? = null
    private var footer = ""

    private class Button(val id: String, val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit, val inList: Boolean) {
        var visible = true
    }
    private var drawingList = false
    private var buttons = ArrayList<Button>()
    private var nextButtons = ArrayList<Button>()

    // ------------------------------------------------------------------ effects
    private class Floater(var text: String, var x: Double, var y: Double, val vx: Double, var vy: Double, val color: Color, val scale: Int, var life: Double, val max: Double)
    private class Part(var x: Double, var y: Double, var vx: Double, var vy: Double, var life: Double, val color: Color, val size: Double, val grav: Double)
    private class Slash(val x: Double, val y: Double, val a: Double, var life: Double, val crit: Boolean)
    private class Coin(var x: Double, var y: Double, var vx: Double, var vy: Double, var t: Double = 0.0, var fly: Boolean = false, var spin: Double = Random.nextDouble(TAU))
    private class Shot(val kind: String, val x0: Double, val y0: Double, val x1: Double, val y1: Double, var t: Double, val dur: Double, val arc: Double, val color: Color)
    private class Banner(val text: String, val color: Color, var life: Double)

    private val floaters = ArrayList<Floater>()
    private val parts = ArrayList<Part>()
    private val slashes = ArrayList<Slash>()
    private val coins = ArrayList<Coin>()
    private val shots = ArrayList<Shot>()
    private var bolt = 0.0
    private var banner: Banner? = null
    private var shake = 0.0
    private var flash = 0.0
    private var flashColor = Color.WHITE
    private var heroAtk = 0.0
    private var petAtk = 0.0
    private val allyAtk = HashMap<String, Double>()
    private var wisp: DoubleArray? = null // x, y, baseY, t
    private var wispTimer = Random.nextDouble(25.0, 50.0)

    private var spec: Art.MonsterSpec? = null
    private var specFor: Monster? = null
    private var bg: BufferedImage? = null
    private var bgWorld: World? = null

    // ================================================================== input
    /** Mouse movement in degrees (from the client's head rotation). */
    fun mouse(dYaw: Double, dPitch: Double) {
        val k = 7.0 * d.cursorSpeed.coerceIn(0.25, 4.0)
        curX = clamp(curX + dYaw * k, 0.0, width - 1.0)
        curY = clamp(curY + dPitch * k, 0.0, height - 1.0)
        if (dragging) {
            if (modal != null || curX < SB_X - 40 || curX > SB_X + SB_W + 40) dragging = false else dragTo(curY)
        }
    }

    /** Mouse wheel notches (positive = down): scrolls the list, or a pop-up's list. */
    fun wheel(steps: Int) {
        if (modal != null) return
        scrollBy(steps * 52)
    }

    /** Left-click (or space): press the button under the cursor, otherwise attack. */
    fun click() {
        if (dragging) { dragging = false; return }
        val b = buttonAt(curX.toInt(), curY.toInt())
        if (b != null) { b.action(); return }
        if (modal == null && curX.toInt() in BX until BX + BW && curY.toInt() in BY + 40 until BY + BH) g.tap(false)
    }

    private fun buttonAt(x: Int, y: Int) = buttons.lastOrNull { it.visible && x >= it.x && y >= it.y && x < it.x + it.w && y < it.y + it.h }

    private fun catchWisp(w: DoubleArray) {
        val msg = g.wispReward()
        burst(w[0], w[1], hex("bbffff"), 24, 260.0, 5.0, 100.0)
        floater(msg, w[0], w[1] - 20, hex("bff8ff"), 2, vy = -50.0, life = 1.6)
        host.sfx(Sfx.WISP)
        wisp = null; wispTimer = Random.nextDouble(45.0, 90.0) / g.wispRate()
    }

    /** Movement keys (WASD), jump (space) and sneak (shift), as pressed/released. */
    fun input(forward: Boolean, back: Boolean, left: Boolean, right: Boolean, jump: Boolean, sneak: Boolean, prevJump: Boolean, prevSneak: Boolean) {
        fun dir(name: String, on: Boolean, dx: Int, dy: Int) {
            if (on && name !in held) { held[name] = 0.4; if (dy == 0 || !overList()) move(dx, dy) }
            if (!on) held.remove(name)
        }
        dir("up", forward, 0, -1); dir("down", back, 0, 1); dir("left", left, -1, 0); dir("right", right, 1, 0)
        if (jump && !prevJump) click()
        if (sneak && !prevSneak) backOut()
    }

    private fun backOut() {
        val m = modal
        if (m != null) { m.back(); return }
        buttons.firstOrNull { it.id == "tab:$tab" }?.let { moveCursorTo(it) }
        host.sfx(Sfx.CLICK)
    }

    private fun moveCursorTo(b: Button) { curX = b.x + b.w / 2.0; curY = b.y + b.h / 2.0 }

    /** Jump the cursor to the nearest button in a direction (keyboard control). */
    private fun move(dx: Int, dy: Int) {
        val m = modal
        if (m is CalibrateModal) { m.nudge(dx, dy); return }
        val cx = curX; val cy = curY
        val cur = buttonAt(cx.toInt(), cy.toInt())
        var best: Button? = null
        var bestScore = Double.MAX_VALUE
        for (b in buttons) {
            if (b === cur || !b.visible) continue
            val vx = b.x + b.w / 2.0 - cx; val vy = b.y + b.h / 2.0 - cy
            val along = vx * dx + vy * dy
            if (along <= 4) continue
            val perp = kotlin.math.abs(vx * dy) + kotlin.math.abs(vy * dx)
            val score = along + perp * 2.5
            if (score < bestScore) { bestScore = score; best = b }
        }
        if (best != null) { moveCursorTo(best); host.sfx(Sfx.CLICK) }
        else if (dy != 0 && cx >= RX && cy in LIST_Y.toDouble()..(LIST_Y + LIST_H).toDouble()) scrollBy(dy * 52)
    }

    // ================================================================== update
    fun update(dt: Double) {
        t += dt
        for (k in held.keys.toList()) {
            if ((k == "up" || k == "down") && overList()) { scrollBy((if (k == "up") -520 else 520) * dt); continue }
            val left = held[k]!! - dt
            if (left <= 0) {
                held[k] = 0.12
                when (k) { "up" -> move(0, -1); "down" -> move(0, 1); "left" -> move(-1, 0); else -> move(1, 0) }
            } else held[k] = left
        }
        shake = max(0.0, shake - dt * 40); flash = max(0.0, flash - dt * 2.5)
        if (heroAtk > 0) { heroAtk += dt * 4.5; if (heroAtk >= 1) heroAtk = 0.0 }
        if (petAtk > 0) { petAtk += dt * 2.5; if (petAtk >= 1) petAtk = 0.0 }
        bolt = max(0.0, bolt - dt)
        g.monster?.let { m -> m.hitT = max(0.0, m.hitT - dt * 6); if (m.dead) m.deathT = min(1.0, m.deathT + dt * 2.6) }
        for (f in floaters) { f.x += f.vx * dt; f.y += f.vy * dt; f.vy += 60 * dt; f.life -= dt }
        floaters.removeIf { it.life <= 0 }
        for (p in parts) { p.x += p.vx * dt; p.y += p.vy * dt; p.vy += p.grav * dt; p.vx *= 0.98; p.life -= dt }
        parts.removeIf { it.life <= 0 }
        for (s in slashes) s.life -= dt
        slashes.removeIf { it.life <= 0 }
        banner?.let { it.life -= dt; if (it.life <= 0) banner = null }
        val gold = goldPos()
        val ci = coins.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            c.t += dt; c.spin += dt * 8
            if (!c.fly) {
                c.vy += 900 * dt; c.x += c.vx * dt; c.y += c.vy * dt
                if (c.y > GY - 6) { c.y = GY - 6; c.vy *= -0.45; c.vx *= 0.7 }
                if (c.t > 0.8) c.fly = true
            } else {
                val dx = gold.first - c.x; val dy = gold.second - c.y; val dist = hypot(dx, dy)
                if (dist < 24) { ci.remove(); host.sfx(Sfx.COIN); continue }
                val sp = 900 + c.t * 600
                c.x += dx / dist * sp * dt; c.y += dy / dist * sp * dt
            }
        }
        val si = shots.iterator()
        while (si.hasNext()) { val s = si.next(); s.t += dt; if (s.t >= s.dur) { si.remove(); burst(s.x1, s.y1, s.color, 4, 120.0, 3.0) } }

        for ((id, a) in allyAtk) if (a > 0) allyAtk[id] = if (a + dt * 3.5 >= 1) 0.0 else a + dt * 3.5

        val wp = wisp
        if (wp == null) {
            wispTimer -= dt
            if (wispTimer <= 0) wisp = doubleArrayOf(-20.0, 0.0, Random.nextDouble(150.0, 260.0), 0.0)
        } else {
            wp[3] += dt; wp[0] += 60 * dt; wp[1] = wp[2] + sin(wp[3] * 2.5) * 30
            if (wp[0] > BW + 30) { wisp = null; wispTimer = Random.nextDouble(45.0, 90.0) / g.wispRate() }
        }
    }

    // ================================================================== positions (battle-panel coordinates)
    private fun heroPos() = 215.0 to GY
    private fun monPos() = 350.0 to GY
    private fun goldPos() = BW / 2.0 - 30 to 90.0
    private fun allyPos(i: Int): Triple<Double, Double, Double> {
        val row = i % 2; val col = i / 2
        return Triple(178.0 - col * 22 - row * 10, GY - (if (row == 1) 20 else 4), U * (if (row == 1) 0.66 else 0.76))
    }
    private fun currentSpec(): Art.MonsterSpec? {
        val m = g.monster ?: return null
        if (specFor !== m) { spec = Art.genMonster(m.seed, m.boss, m.mimic, m.lord); specFor = m }
        return spec
    }
    private fun monsterCenter(): Pair<Double, Double> {
        val sp = currentSpec()
        val h = if (sp == null) 150.0 else Art.monsterHeight(sp) * U * 0.95
        return monPos().first to GY - h / 2
    }

    // ================================================================== effects helpers
    private fun floater(text: String, x: Double, y: Double, color: Color, scale: Int, vx: Double = Random.nextDouble(-20.0, 20.0), vy: Double = -90.0, life: Double = 1.0) {
        floaters += Floater(text, x, y, vx, vy, color, scale, life, life)
        if (floaters.size > 30) floaters.removeAt(0)
    }
    private fun burst(x: Double, y: Double, color: Color, n: Int, speed: Double = 220.0, size: Double = 4.0, grav: Double = 500.0) {
        repeat(n) {
            val a = Random.nextDouble(TAU); val v = Random.nextDouble(speed * 0.3, speed)
            parts += Part(x, y, cos(a) * v, sin(a) * v - speed * 0.3, Random.nextDouble(0.4, 0.9), color, Random.nextDouble(size * 0.5, size), grav)
        }
        while (parts.size > 160) parts.removeAt(0)
    }
    private fun showBanner(text: String, color: Color) { banner = Banner(text, color, 1.8) }

    // ================================================================== game events
    override fun onTap(damage: Double, crit: Boolean, auto: Boolean) {
        heroAtk = 0.0001
        val (cx, cy) = monsterCenter()
        slashes += Slash(cx + Random.nextDouble(-30.0, 30.0), cy + Random.nextDouble(-40.0, 40.0), Random.nextDouble(-0.9, 0.9) + if (Random.nextBoolean()) 0.0 else PI, 0.25, crit)
        floater(fmt(damage), cx + Random.nextDouble(-50.0, 50.0), cy - 30, if (crit) hex("ff8a1a") else Color.WHITE, 2, vy = if (crit) -150.0 else -110.0)
        if (crit) {
            shake = max(shake, 5.0)
            floater("CRIT!", cx, cy - 70, hex("ffd23a"), 2, vx = 0.0, vy = -60.0, life = 0.6)
            host.sfx(Sfx.CRIT)
        } else if (!auto) host.sfx(Sfx.HIT)
        burst(cx, cy, if (crit) hex("ffb43a") else Color.WHITE, if (crit) 10 else 4, if (crit) 260.0 else 160.0)
    }

    override fun onKill(m: Monster, gold: Double, gems: Int) {
        val (cx, cy) = monsterCenter()
        burst(cx, cy, currentSpec()?.color ?: Color.WHITE, 26, 340.0, 7.0)
        repeat(if (m.boss) 12 else if (m.mimic) 16 else 4) { coins += Coin(cx, cy, Random.nextDouble(-160.0, 160.0), Random.nextDouble(-380.0, -200.0)) }
        floater("+" + fmt(gold), cx, cy - 60, hex("ffd23a"), 2, vx = 0.0, vy = -70.0, life = 1.2)
        if (gems > 0) floater("+$gems GEM" + (if (gems > 1) "S" else ""), cx, cy - 90, hex("6ae4ff"), 2, vx = 0.0, vy = -50.0, life = 1.4)
        host.sfx(if (m.boss) Sfx.BOSS_KILL else Sfx.KILL)
        if (m.boss) { shake = 12.0; flash = 0.6; flashColor = Color.WHITE }
    }

    override fun onSpawn(m: Monster) {
        if (m.lord) { host.sfx(Sfx.BOSS); showBanner("TITAN LORD!", hex("c58aff")) }
        else if (m.boss) { host.sfx(Sfx.BOSS); showBanner("BOSS BATTLE!", hex("ff5a4a")) }
        if (m.mimic) showBanner("A TREASURE MIMIC!", hex("ffd23a"))
    }

    override fun onStage(skipped: Int) {
        host.sfx(Sfx.LEVEL_UP)
        if (skipped > 1) showBanner("WARP! STAGE ${d.stage}", hex("7affb0"))
        else if (worldFor(d.stage) != worldFor(d.stage - 1)) showBanner(worldFor(d.stage).name.uppercase(), Color.WHITE)
    }

    override fun onBossFail() {
        host.sfx(Sfx.FAIL)
        showBanner("THE BOSS ESCAPED!", hex("ff7a6a"))
    }

    override fun onSkill(id: String, damage: Double) {
        host.sfx(Sfx.SPELL)
        if (id == "thunder") {
            host.sfx(Sfx.THUNDER)
            bolt = 0.45; flash = 0.7; flashColor = hex("d8f0ff"); shake = 14.0
            val (cx, cy) = monsterCenter()
            floater(fmt(damage), cx, cy - 40, hex("8ad8ff"), 3, vx = 0.0, vy = -80.0, life = 1.4)
        } else showBanner(SKILLS.first { it.id == id }.name.uppercase() + "!", hex("ffe08a"))
    }

    override fun onPetAttack(damage: Double) {
        petAtk = 0.0001
        host.sfx(Sfx.PET)
        val (cx, cy) = monsterCenter()
        burst(cx, cy, petDef(d.activePet)?.body?.let(::hex) ?: Color.WHITE, 18, 280.0, 6.0)
        floater(fmt(damage), cx - 20, cy - 50, hex("9bff7a"), 2, vx = 0.0, vy = -100.0)
    }

    /** An ally's attack (once per second each): swing, a projectile and a small damage number. */
    override fun onAllyAttack(id: String, damage: Double) {
        val i = HEROES.indexOfFirst { it.id == id }
        if (i < 0) return
        val h = HEROES[i]
        allyAtk[id] = 0.0001
        val (ax, ay, s) = allyPos(i); val (cx, cy) = monsterCenter()
        val w = h.look.weapon
        val col = h.look.glow?.let(::hex) ?: when (w) { "bow" -> hex("dddddd"); "gun" -> hex("ffe08a"); "leaf" -> hex("7bd65a"); else -> Color.WHITE }
        val tx = cx + Random.nextDouble(-20.0, 20.0); val ty = cy + Random.nextDouble(-25.0, 25.0)
        shots += Shot(w, ax + 10, ay - 30 * s, tx, ty, 0.0, if (w == "gun") 0.1 else 0.22, if (w == "bow") 30.0 else if (w == "gun") 0.0 else 16.0, col)
        floater(fmt(damage), tx, ty - 20, hex("bfe4ff"), 1, vy = -70.0, life = 0.8)
    }

    override fun onAchievement(group: AchGroup, target: Double, gems: Int) {
        host.sfx(Sfx.ACHIEVEMENT)
        showBanner(if (gems > 0) "${group.name.uppercase()}  +$gems GEMS" else "ACHIEVEMENT: ${group.name.uppercase()}", hex("ffd23a"))
    }

    /** Gems arrived from the web store / an admin. */
    fun gemsReceived(n: Long) {
        host.sfx(Sfx.LEGENDARY)
        modal = InfoModal("THANK YOU!", listOf("Your purchase has arrived:"), "+${fmt(n)} gems")
    }

    fun welcome(seconds: Double, gold: Double) {
        if (gold > 0) modal = InfoModal("WELCOME BACK!", listOf("You were away ${fmtTime(seconds)}.", "Your allies earned:"), "+${fmt(gold)} gold")
    }

    // ================================================================== drawing helpers
    private fun text(s: String, x: Int, y: Int, c: Color = Color.WHITE, scale: Int = 1, align: Int = 0, outline: Boolean = true) {
        val w = PixelFont.width(s, scale)
        val sx = when (align) { 1 -> x - w / 2; 2 -> x - w; else -> x }
        if (outline) PixelFont.drawOutlined(gfx, s, sx, y, c, scale) else PixelFont.draw(gfx, s, sx, y, c, scale)
    }

    private fun fit(s: String, maxW: Int, scale: Int = 1): String {
        if (PixelFont.width(s, scale) <= maxW) return s
        var r = s
        while (r.isNotEmpty() && PixelFont.width("$r..", scale) > maxW) r = r.dropLast(1)
        return "$r.."
    }

    /** True if the cursor is inside this rectangle. */
    private fun hovered(x: Int, y: Int, w: Int, h: Int): Boolean {
        if (modal != null && !drawingModal) return false
        val cx = curX.toInt(); val cy = curY.toInt()
        return cx >= x && cy >= y && cx < x + w && cy < y + h
    }
    private var drawingModal = false

    private fun panel(x: Int, y: Int, w: Int, h: Int, fill: Color = hex("2b2d3c"), border: Color = hex("8a8c4a")) {
        gfx.fill(Rectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()), border)
        gfx.fill(Rectangle2D.Double(x + 3.0, y + 3.0, w - 6.0, h - 6.0), fill)
    }

    /** A clickable box; returns true if hovered. */
    private fun button(x: Int, y: Int, w: Int, h: Int, label: String, base: Color, enabled: Boolean = true, scale: Int = 1, id: String? = null, action: () -> Unit): Boolean {
        val hov = hovered(x, y, w, h)
        val c = when { !enabled -> hex("4a4d5a"); hov -> base.shade(0.25); else -> base }
        gfx.fill(Rectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()), OUT)
        gfx.fill(Rectangle2D.Double(x + 2.0, y + 2.0, w - 4.0, h - 4.0), c)
        gfx.fill(Rectangle2D.Double(x + 2.0, y + 2.0, w - 4.0, 2.0), c.shade(0.3))
        gfx.fill(Rectangle2D.Double(x + 2.0, y + h - 4.0, w - 4.0, 2.0), c.shade(-0.3))
        if (label.isNotEmpty()) text(label, x + w / 2, y + (h - PixelFont.HEIGHT * scale) / 2, if (enabled) Color.WHITE else hex("9a9db0"), scale, 1)
        nextButtons += Button(id ?: "$x,$y", x, y, w, h, { if (enabled) { action() } else host.sfx(Sfx.ERROR) }, drawingList)
        return hov
    }

    private fun hit(x: Int, y: Int, w: Int, h: Int, id: String? = null, action: () -> Unit) { nextButtons += Button(id ?: "$x,$y", x, y, w, h, action, drawingList) }

    // ================================================================== frame
    fun render() {
        nextButtons = ArrayList()
        footer = ""
        gfx.color = hex("14131c"); gfx.fillRect(0, 0, width, height)
        drawBattle()
        drawPanel()
        drawingModal = true
        modal?.draw()
        drawingModal = false
        buttons = nextButtons
        val sel = buttonAt(curX.toInt(), curY.toInt())
        selected = sel?.id
        if (sel != null && sel.id != "tap-monster" && sel.id != "scrollbar") drawSelection(sel)
        // hovering near the top/bottom of a list scrolls it
        if (modal == null && curX >= RX && curX < RX + RW) {
            if (curY in LIST_Y.toDouble()..LIST_Y + 14.0) scrollBy(-10)
            if (curY in LIST_Y + LIST_H - 14.0..LIST_Y + LIST_H.toDouble()) scrollBy(10)
        }
    }

    private fun drawSelection(b: Button) {
        gfx.save {
            if (b.inList) clip(Rectangle2D.Double(RX + 2.0, LIST_Y.toDouble(), RW - 4.0, LIST_H.toDouble()))
            stroke = stroke(2f); color = hex("ffe34a")
            draw(Rectangle2D.Double(b.x - 1.0, b.y - 1.0, b.w + 2.0, b.h + 2.0))
        }
    }

    // Where the cursor is drawn. Mouse movement arrives 20 times a second but the cursor is drawn 40
    // times a second: a frame with fresh movement goes halfway, the next one the rest of the way.
    private var drawX = curX; private var drawY = curY
    private var seenX = curX; private var seenY = curY

    /** Advance the drawn cursor one step; returns the top-left pixel of the cursor sprite. */
    fun stepCursor(): Pair<Int, Int> {
        val fresh = curX != seenX || curY != seenY
        seenX = curX; seenY = curY
        if (fresh) { drawX = (drawX + curX) / 2; drawY = (drawY + curY) / 2 } else { drawX = curX; drawY = curY }
        return drawX.toInt() - 1 to drawY.toInt() - 1
    }

    /**
     * Draw the cursor straight onto a frame of map colours. It is kept out of [render] so it can
     * move every client tick without redrawing the whole screen.
     */
    fun overlayCursor(map: ByteArray) = overlayCursorAt(map, curX.toInt() - 1, curY.toInt() - 1)

    /** Draw the cursor sprite with its top-left at ([x0], [y0]). */
    fun overlayCursorAt(map: ByteArray, x0: Int, y0: Int) {
        for (y in 0 until CURSOR_H) {
            val ty = y0 + y
            if (ty !in 0 until height) continue
            for (x in 0 until CURSOR_W) {
                val tx = x0 + x
                val i = y * CURSOR_W + x
                if (cursorMask[i] && tx in 0 until width) map[ty * width + tx] = cursorSprite[i]
            }
        }
    }

    private val SB_X get() = RX + RW - 17
    private val SB_W = 10

    /** Scroll bar thumb: top y and height. */
    private fun thumb(): Pair<Int, Int> {
        val sh = max(24, LIST_H * LIST_H / max(1, listContentH))
        val range = max(1, listContentH - LIST_H)
        return LIST_Y + ((LIST_H - sh) * (scroll / range)).toInt() to sh
    }

    /** Scroll so the thumb is centred on screen y [y]. */
    private fun dragTo(y: Double) {
        val sh = thumb().second
        val k = clamp((y - LIST_Y - sh / 2.0) / max(1, LIST_H - sh), 0.0, 1.0)
        scroll = k * max(0, listContentH - LIST_H)
    }

    private fun overList() = modal == null && curX >= RX && curX < RX + RW && curY >= LIST_Y - 30 && curY < LIST_Y + LIST_H

    private fun scrollBy(pixels: Double) {
        scroll = clamp(scroll + pixels, 0.0, max(0.0, (listContentH - LIST_H).toDouble()))
    }

    private fun scrollBy(pixels: Int) {
        scroll = clamp(scroll + pixels, 0.0, max(0.0, (listContentH - LIST_H).toDouble()))
    }

    // ================================================================== battle
    private fun drawBattle() = gfx.save {
        val world = worldFor(d.stage)
        translate(BX.toDouble(), BY.toDouble())
        clip(Rectangle2D.Double(0.0, 0.0, BW.toDouble(), BH.toDouble()))
        val shakeX = if (shake > 0) Random.nextDouble(-shake, shake) else 0.0
        val shakeY = if (shake > 0) Random.nextDouble(-shake, shake) else 0.0
        save {
            translate(shakeX, shakeY)
            if (bg == null || bgWorld != world) { bg = Art.background(world, BW, BH, GY); bgWorld = world }
            drawImage(bg, 0, 0, null)
            Art.ambience(this, world, BW, GY, ta)

            HEROES.forEachIndexed { i, h ->
                if (g.heroLvl(h.id) == 0) return@forEachIndexed
                val (x, y, s) = allyPos(i)
                Art.drawChibi(this, h.look.copy(phase = i.toDouble()), x, y, s, ta, allyAtk[h.id] ?: 0.0)
            }
            val m = g.monster
            val sp = currentSpec()
            if (m != null && sp != null) Art.drawMonster(this, sp, monPos().first, GY, ta, m.hitT, m.deathT, m.age, U * 0.95)
            val (hx, hy) = heroPos()
            Art.drawChibi(this, heroLook(), hx, hy, U * 1.3, ta, heroAtk)
            petDef(d.activePet)?.let { p ->
                val homeX = hx - 38; val homeY = GY - 118 + sin(ta * 3) * 6
                var px = homeX; var py = homeY
                if (petAtk > 0) { val k = sin(petAtk * PI); val (cx, cy) = monsterCenter(); px = lerp(homeX, cx - 30, k); py = lerp(homeY, cy, k) }
                Art.drawPet(this, p, px, py, 0.85, t, petAtk > 0)
                val f = d.petCharge.toDouble() / PET_CHARGE
                stroke = stroke(3f); color = Color(0, 0, 0, 90); draw(circle(homeX, homeY + 30, 7.0))
                color = hex("9bff7a"); draw(arc(homeX, homeY + 30, 7.0, -PI / 2, -PI / 2 + TAU * f))
            }
            for (s in shots) {
                val k = s.t / s.dur
                val x = lerp(s.x0, s.x1, k); val y = lerp(s.y0, s.y1, k) - sin(k * PI) * s.arc
                when (s.kind) {
                    "bow" -> { line(x - 9, y, x + 7, y, hex("6a4a2a"), 2f); fill(path { moveTo(x + 7, y - 3); lineTo(x + 12, y); lineTo(x + 7, y + 3); closePath() }, hex("dddddd")) }
                    "gun" -> fill(Rectangle2D.Double(x - 6, y - 1.5, 12.0, 3.0), hex("ffe08a"))
                    "leaf" -> fill(ellipse(x, y, 7.0, 3.0), hex("7bd65a"))
                    else -> { save { alpha(0.4); fill(circle(x, y, 9.0), s.color) }; fill(circle(x, y, 5.0), s.color); fill(circle(x, y, 2.5), Color.WHITE) }
                }
            }
            for (s in slashes) {
                val k = 1 - s.life / 0.25
                save {
                    translate(s.x, s.y); rotate(s.a); alpha(1 - k)
                    stroke = stroke(((if (s.crit) 9 else 6) * (1 - k * 0.5)).toFloat())
                    color = if (s.crit) hex("ffb43a") else Color.WHITE
                    draw(arc(0.0, 0.0, 50.0, -0.9 + k * 0.6, 0.9 * k + 0.1))
                }
            }
            if (bolt > 0) save {
                val (cx, cy) = monsterCenter()
                alpha(bolt / 0.45)
                val p = path {
                    var x = cx + Random.nextDouble(-30.0, 30.0); var y = 0.0; moveTo(x, y)
                    while (y < cy) { y += Random.nextDouble(20.0, 40.0); x += Random.nextDouble(-25.0, 25.0); lineTo(x, y) }
                }
                stroke = stroke(7f); color = hex("5ab8ff"); draw(p)
                stroke = stroke(3f); color = Color.WHITE; draw(p)
            }
            for (p in parts) fill(Rectangle2D.Double(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size), p.color)
            for (c in coins) Art.coin(this, c.x, c.y, 8.0, cos(c.spin))
            wisp?.let { Art.wisp(this, it[0], it[1], t) }
            for (f in floaters) if (f.life > f.max * 0.15) text(f.text, f.x.toInt(), f.y.toInt(), f.color, f.scale, 1)
        }
        drawHud()
        val (mx, _) = monPos()
        hit(BX + mx.toInt() - 70, BY + 200, 140, 190, "tap-monster") { g.tap(false) }
        wisp?.let { w -> hit(BX + w[0].toInt() - 20, BY + w[1].toInt() - 20, 40, 40, "wisp") { catchWisp(w) } }
        if (flash > 0) save { alpha(min(1.0, flash)); fill(Rectangle2D.Double(0.0, 0.0, BW.toDouble(), BH.toDouble()), flashColor) }
        banner?.let { b ->
            val y = 190
            fill(path { moveTo(30.0, y - 4.0); lineTo(BW - 20.0, y - 4.0); lineTo(BW - 30.0, y + 26.0); lineTo(20.0, y + 26.0); closePath() }, Color(20, 24, 40, 190))
            text(b.text, BW / 2, y + 4, b.color, 2, 1)
        }
        // border
        stroke = stroke(3f); color = hex("8a8c4a"); draw(Rectangle2D.Double(1.5, 1.5, BW - 3.0, BH - 3.0))
    }

    private fun heroLook(): Look {
        var l = HERO_LOOK
        gearDef(d.equipped["weapon"])?.let { l = l.copy(blade = it.blade, bladeGlow = it.glow) }
        gearDef(d.equipped["helm"])?.let { l = l.copy(helmGear = it.helm, helmColor = it.color) }
        gearDef(d.equipped["armor"])?.let { l = l.copy(outfit = it.outfit ?: l.outfit, trim = it.trim ?: l.trim) }
        gearDef(d.equipped["aura"])?.let { l = l.copy(aura = it.aura) }
        skinDef(d.skin)?.let { l = it.apply(l) }
        return l
    }

    /** HUD drawn in battle-panel coordinates (the graphics are already translated). */
    private fun drawHud() {
        val m = g.monster
        // top-left buttons (these are in screen coordinates for hit testing)
        if (host.allowExit) topButton(8, 8, 50, 22, "EXIT", hex("c0392b")) { host.exit() }
        iconButton(62, 6, 26, "trophy") { modal = AchievementsModal() }
        iconButton(92, 6, 26, "top") { modal = TopModal(host.leaderboard()) }
        iconButton(122, 6, 26, "gear") { modal = SettingsModal() }

        // stage pips
        val cy = 22.0
        fun pip(x: Double, r: Double, stage: Int, cur: Boolean) {
            if (stage < 1) return
            val w = worldFor(stage)
            gfx.save {
                clip(circle(x, cy, r))
                paint = GradientPaint(0f, (cy - r).toFloat(), hex(w.sky.first), 0f, (cy + r).toFloat(), hex(w.sky.second))
                fill(Rectangle2D.Double(x - r, cy - r, r * 2, r * 2))
                fill(Rectangle2D.Double(x - r * 0.3, cy - r * 0.4, r * 0.6, r), hex(w.mid))
                fill(Rectangle2D.Double(x - r, cy + r * 0.45, r * 2, r), hex(w.ground))
            }
            gfx.stroke = stroke(if (cur) 3f else 2f); gfx.color = if (cur) Color.WHITE else Color(255, 255, 255, 170); gfx.draw(circle(x, cy, r))
            text(stage.toString(), x.toInt(), (cy + r * 0.45).toInt(), Color.WHITE, 1, 1)
        }
        for (dd in listOf(-1, 1)) for (i in 1..3) gfx.fill(circle(BW / 2.0 + dd * (24 + i * 7), cy, 1.6), Color(255, 255, 255, 190))
        pip(BW / 2.0 - 60, 13.0, d.stage - 1, false)
        pip(BW / 2.0, 18.0, d.stage, true)
        pip(BW / 2.0 + 60, 13.0, d.stage + 1, false)

        // wave / boss indicator + boss button
        if (m != null && m.boss && !m.dead) {
            Art.skull(gfx, BW - 30.0, 20.0, 11.0)
            text("%.1f".format(g.bossTimer), BW - 30, 36, if (g.bossTimer < 8) hex("ff4a3a") else Color.WHITE, 2, 1)
            topButton(BW - 100, 60, 90, 22, "LEAVE", hex("4a4f62")) { g.leaveBoss() }
        } else if (d.farming) {
            topButton(BW - 110, 8, 100, 26, "FIGHT BOSS", hex("d0402a")) { g.fightBoss() }
        } else {
            Art.skull(gfx, BW - 30.0, 18.0, 10.0)
            text("${min(d.wave + 1, WAVES)}/$WAVES", BW - 30, 32, Color.WHITE, 2, 1)
        }

        // HP bar
        val bw = 300.0; val bh = 18.0; val bx = BW / 2.0 - bw / 2; val by = 46.0
        fun para(x: Double, w: Double) = path { moveTo(x, by + bh); lineTo(x + 8, by); lineTo(x + w + 8, by); lineTo(x + w, by + bh); closePath() }
        gfx.fill(para(bx, bw), hex("1b2a44"))
        if (m != null) {
            val f = clamp(m.hp / m.maxHp, 0.0, 1.0)
            gfx.fill(para(bx, bw * f), if (m.boss) hex("e0402a") else hex("f06a2a"))
            gfx.fill(Rectangle2D.Double(bx + 8, by + 2, bw * f, 4.0), Color(255, 255, 255, 60))
            if (m.boss) Art.skull(gfx, bx - 6, by + bh / 2, 10.0)
            text(fit(m.name, 170), (bx + 14).toInt(), (by + 6).toInt(), Color.WHITE)
            text("${fmt(ceil(m.hp))} HP", (bx + bw - 4).toInt(), (by + 6).toInt(), Color.WHITE, 1, 2)
        }
        gfx.stroke = stroke(2f); gfx.color = OUT; gfx.draw(para(bx, bw))

        // gold
        val (gx, gy) = goldPos()
        Art.coin(gfx, gx, gy, 12.0)
        text(fmt(d.gold), (gx + 18).toInt(), (gy - 7).toInt(), Color.WHITE, 2)

        // mana + spells (left column)
        val mx = 10.0; val my = 108.0
        gfx.fill(rrect(mx, my, 44.0, 8.0, 3.0), hex("151726"))
        gfx.fill(rrect(mx, my, 44.0 * (d.mana / g.maxMana()), 8.0, 3.0), hex("4aa8ff"))
        text("${floor(d.mana).toInt()}", 32, (my - 10).toInt(), hex("8ad8ff"), 1, 1)
        SKILLS.forEachIndexed { i, sk -> skillButton(sk, 10, 120 + i * 46) }

        // buffs (right column)
        var yy = 96
        for ((id, left) in d.buffs) {
            val name = SKILLS.firstOrNull { it.id == id }?.name ?: SHOP_ITEMS.firstOrNull { it.id == id }?.name ?: continue
            gfx.fill(rrect(BW - 128.0, yy.toDouble(), 120.0, 14.0, 4.0), Color(0, 0, 0, 120))
            text(fit("$name ${fmtTime(left)}", 114), BW - 124, yy + 4, hex("ffe08a"))
            yy += 17
        }
    }

    // Buttons inside the battle panel: draw translated, but register screen coordinates.
    private fun topButton(x: Int, y: Int, w: Int, h: Int, label: String, c: Color, action: () -> Unit) {
        gfx.save { translate(-BX.toDouble(), -BY.toDouble()); button(BX + x, BY + y, w, h, label, c) { host.sfx(Sfx.CLICK); action() } }
    }

    private fun iconButton(x: Int, y: Int, s: Int, key: String, action: () -> Unit) {
        val hov = hovered(BX + x, BY + y, s, s)
        Art.icon(gfx, key, x.toDouble(), y.toDouble(), s.toDouble())
        if (hov) { gfx.stroke = stroke(2f); gfx.color = Color.WHITE; gfx.draw(rrect(x.toDouble(), y.toDouble(), s.toDouble(), s.toDouble(), 4.0)) }
        hit(BX + x, BY + y, s, s) { host.sfx(Sfx.CLICK); action() }
    }

    private fun skillButton(sk: Skill, x: Int, y: Int) {
        val lvl = g.skillLvl(sk.id)
        val active = d.buffs[sk.id]
        val ready = lvl > 0 && active == null && d.mana >= sk.mana
        gfx.save {
            Art.icon(this, sk.id, x.toDouble(), y.toDouble(), 40.0)
            if (lvl == 0 || (!ready && active == null)) { alpha(0.65); fill(rrect(x.toDouble(), y.toDouble(), 40.0, 40.0, 6.0), Color(20, 20, 30)) }
        }
        if (active != null) {
            val f = active / g.spellDuration()
            gfx.save { alpha(0.45); fill(Rectangle2D.Double(x.toDouble(), y + 40 * (1 - f), 40.0, 40 * f), hex("ffe08a")) }
            gfx.stroke = stroke(2f); gfx.color = hex("ffd23a"); gfx.draw(rrect(x.toDouble(), y.toDouble(), 40.0, 40.0, 6.0))
            text("${ceil(active).toInt()}", x + 38, y + 30, Color.WHITE, 1, 2)
        } else if (lvl == 0) text("LV${sk.req}", x + 20, y + 16, hex("c0c4d0"), 1, 1)
        else text("${sk.mana}", x + 38, y + 30, hex("8ad8ff"), 1, 2)
        if (hovered(BX + x, BY + y, 40, 40)) {
            footer = if (lvl == 0) "${sk.name}: learn it in the Hero tab (tap level ${sk.req})" else "${sk.name}: ${skillDesc(sk.id, lvl)} (${sk.mana} mana)"
            gfx.stroke = stroke(2f); gfx.color = Color.WHITE; gfx.draw(rrect(x.toDouble(), y.toDouble(), 40.0, 40.0, 6.0))
        }
        hit(BX + x, BY + y, 40, 40, "cast:${sk.id}") { if (!g.castSkill(sk.id)) host.sfx(Sfx.ERROR) }
    }

    // ================================================================== right panel
    private val TABS = listOf("hero" to "HERO", "allies" to "ALLY", "skills" to "SKILL", "gear" to "GEAR", "pets" to "PETS", "artifacts" to "RELIC", "quests" to "QUEST", "shop" to "SHOP")
    private val TAB_COLORS = mapOf("hero" to "e8643a", "allies" to "3aa0b8", "skills" to "c0507a", "gear" to "e3b72e", "pets" to "58b04a",
        "artifacts" to "8a5ad0", "quests" to "4a7ad0", "shop" to "2f8a86")

    private fun drawPanel() {
        panel(RX, RY, RW, RH)
        // tabs
        TABS.forEachIndexed { i, (id, label) ->
            val x = RX + 6 + i * 46; val y = RY + 6; val w = 44; val h = 46
            val c = hex(TAB_COLORS[id]!!)
            val active = tab == id
            val hov = hovered(x, y, w, h)
            gfx.fill(Rectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()), OUT)
            gfx.fill(Rectangle2D.Double(x + 2.0, y + 2.0, w - 4.0, h - 4.0), if (active) c.shade(0.15) else if (hov) c.shade(-0.1) else c.shade(-0.35))
            if (active) gfx.fill(Rectangle2D.Double(x + 2.0, y + 2.0, w - 4.0, 3.0), Color.WHITE)
            tabIcon(id, x + w / 2.0, y + 18.0)
            text(label, x + w / 2, y + h - 11, if (active) Color.WHITE else hex("c8cbd8"), 1, 1)
            if (tabBadge(id)) { gfx.fillStroke(circle(x + w - 6.0, y + 7.0, 4.0), hex("e8392a"), 1.5f, Color.WHITE) }
            hit(x, y, w, h, "tab:$id") { if (tab != id) { tab = id; scroll = 0.0; host.sfx(Sfx.CLICK) } }
        }
        // header
        val hy = RY + 58
        gfx.fill(Rectangle2D.Double(RX + 6.0, hy.toDouble(), RW - 12.0, 26.0), hex("4a4d5e"))
        when (tab) {
            "hero" -> headerStat(fmt(g.tapDamage()), "TAP DAMAGE")
            "allies" -> headerStat(fmt(g.heroDps()), "ALLY DPS")
            "gear" -> headerStat("${d.gear.size}/${GEAR.size}", "EQUIPMENT")
            "pets" -> headerStat(petDef(d.activePet)?.name?.uppercase() ?: "NO PET", "ACTIVE")
            "artifacts" -> headerStat("${d.maxStage}", "MAX STAGE")
            "skills" -> headerStat("${g.spFree()}", "SKILL POINTS")
            "quests" -> headerStat("DAILY", "QUESTS & REWARDS")
            else -> headerStat("SHOP", "")
        }
        when (tab) {
            "artifacts" -> { Art.relic(gfx, RX + RW - 70.0, hy + 13.0, 8.0); text(fmt(d.relics), RX + RW - 58, hy + 6, Color.WHITE, 2) }
            "shop", "gear", "pets", "quests" -> { Art.gem(gfx, RX + RW - 70.0, hy + 12.0, 8.0); text(fmt(d.gems), RX + RW - 58, hy + 6, Color.WHITE, 2) }
            else -> { Art.coin(gfx, RX + RW - 70.0, hy + 13.0, 8.0); text(fmt(d.gold), RX + RW - 58, hy + 6, Color.WHITE, 2) }
        }
        // toolbar: buy multiplier + scroll arrows
        val ty = RY + 88
        if (tab == "hero" || tab == "allies") listOf(1 to "X1", 10 to "X10", 100 to "X100", 0 to "MAX").forEachIndexed { i, (v, label) ->
            button(RX + 6 + i * 52, ty, 48, 20, label, if (d.mult == v) hex("c9a02a") else hex("5b6074")) { d.mult = v; host.sfx(Sfx.CLICK) }
        } else text(when (tab) {
            "gear" -> "Open chests in the shop for gear"
            "pets" -> "Pets strike every $PET_CHARGE taps"
            "artifacts" -> "Prestige to earn relics"
            "skills" -> "Points: 1 per 20 stages, 2 per prestige"
            "quests" -> "New quests every day"
            else -> "Get more gems at our store!"
        }, RX + 10, ty + 7, hex("9aa0b8"))
        button(RX + RW - 60, ty, 26, 20, "^", hex("5b6074"), id = "scroll:up") { scrollBy(-104) }
        button(RX + RW - 32, ty, 26, 20, "v", hex("5b6074"), id = "scroll:down") { scrollBy(104) }

        // list
        gfx.save {
            clip(Rectangle2D.Double(RX + 4.0, LIST_Y.toDouble(), RW - 8.0, LIST_H.toDouble()))
            val rows = buildList()
            listContentH = rows.sumOf { it.h }
            scroll = clamp(scroll, 0.0, max(0.0, (listContentH - LIST_H).toDouble()))
            var y = LIST_Y - scroll.toInt()
            drawingList = true
            for (r in rows) {
                if (y + r.h > LIST_Y && y < LIST_Y + LIST_H) r.draw(RX + 6, y, RW - 26) // only rows that show
                y += r.h
            }
            drawingList = false
        }
        for (b in nextButtons) if (b.inList && (b.y + b.h <= LIST_Y + 4 || b.y >= LIST_Y + LIST_H - 4)) b.visible = false
        // scroll bar: click it (or hold W/S, or rest the cursor at the top/bottom of the list) to scroll
        if (listContentH > LIST_H) {
            val (sy, sh) = thumb()
            val hot = dragging || (modal == null && hovered(SB_X - 4, LIST_Y, SB_W + 8, LIST_H))
            gfx.fill(Rectangle2D.Double(SB_X.toDouble(), LIST_Y.toDouble(), SB_W.toDouble(), LIST_H.toDouble()), hex("23253a"))
            gfx.fill(Rectangle2D.Double(SB_X + 1.0, sy.toDouble(), SB_W - 2.0, sh.toDouble()), if (hot) hex("ffd23a") else hex("8a8c9a"))
            hit(SB_X - 4, LIST_Y, SB_W + 8, LIST_H, "scrollbar") { dragging = true; dragTo(curY) }
        } else dragging = false
        // footer
        gfx.fill(Rectangle2D.Double(RX + 6.0, RY + RH - 66.0, RW - 12.0, 60.0), hex("22243a"))
        val lines = wrap(footer.ifEmpty { "Move the mouse to aim, LEFT-CLICK to attack and press buttons, scroll the MOUSE WHEEL for lists. SHIFT: back" }, RW - 24)
        lines.take(4).forEachIndexed { i, l -> text(l, RX + 12, RY + RH - 60 + i * 12, hex("c8cbd8")) }
    }

    private fun headerStat(big: String, small: String) {
        val hy = RY + 58
        text(big, RX + 12, hy + 6, Color.WHITE, 2)
        text(small, RX + 18 + PixelFont.width(big, 2), hy + 13, hex("c8cbd8"))
    }

    private fun wrap(s: String, maxW: Int): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (word in s.split(' ')) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (PixelFont.width(test) > maxW && line.isNotEmpty()) { out += line; line = word } else line = test
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    private fun tabIcon(id: String, cx: Double, cy: Double) {
        when (id) {
            "hero" -> Art.icon(gfx, "tap", cx - 13, cy - 13, 26.0)
            "allies" -> gfx.save { clip(Rectangle2D.Double(cx - 14, cy - 14, 28.0, 28.0)); Art.drawChibi(this, HEROES[8].look.copy(cape = null), cx - 1, cy + 34, 0.6, 0.0) }
            "gear" -> Art.gearIcon(gfx, GEAR.first { it.id == "a_dragon" }, cx - 13, cy - 13, 26.0, true)
            "pets" -> Art.drawPet(gfx, PETS[0], cx, cy + 2, 0.5, t)
            "artifacts" -> Art.relic(gfx, cx, cy + 1, 11.0)
            "skills" -> Art.icon(gfx, "tome", cx - 12, cy - 12, 24.0)
            "quests" -> Art.icon(gfx, "quest", cx - 12, cy - 12, 24.0)
            else -> Art.chest(gfx, CHESTS[2], cx, cy + 2, 0.42)
        }
    }

    private fun tabBadge(id: String) = when (id) {
        "hero" -> d.gold >= g.tapUpgradeInfo().second
        "allies" -> HEROES.indices.any { g.heroUnlocked(it) && d.gold >= g.heroUpgradeInfo(HEROES[it]).second }
        "artifacts" -> ARTIFACTS.any { (it.max == 0 || g.art(it.id) < it.max) && d.relics >= g.artifactCost(it, g.art(it.id)) }
        "shop" -> g.freeChestReady()
        "skills" -> g.spFree() > 0 && SKILL_TREE.any { g.st(it.id) < it.max }
        "quests" -> g.loginReady() || g.quests().list.any { !it.claimed && g.questProgress(it) >= it.target } || g.questBonusReady()
        else -> false
    }

    // ------------------------------------------------------------------ list rows
    private class Row(val h: Int, val draw: (Int, Int, Int) -> Unit)

    private fun section(title: String) = Row(24) { x, y, w ->
        gfx.fill(Rectangle2D.Double(x.toDouble(), y + 4.0, w.toDouble(), 18.0), hex("22243a"))
        text(title, x + w / 2, y + 9, Color.WHITE, 1, 1)
    }

    /** A standard upgrade row with an icon, title, two lines of text and a buy button. */
    private fun upgradeRow(
        id: String, icon: (Int, Int) -> Unit, title: String, line1: String, line2: String,
        cost: String?, costIcon: String, label: String, enabled: Boolean, glow: Boolean = false, hover: String = "", action: () -> Boolean,
    ) = Row(52) { x, y, w ->
        val hov = hovered(x, y, w, 50)
        gfx.fill(Rectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), 50.0), if (hov) hex("3a3d52") else hex("34374a"))
        gfx.fill(Rectangle2D.Double(x.toDouble(), y + 48.0, w.toDouble(), 2.0), hex("232534"))
        icon(x + 4, y + 4)
        text(fit(title, w - 160, 1), x + 52, y + 6, Color.WHITE)
        text(fit(line1, w - 160), x + 52, y + 19, hex("8ad8ff"))
        text(fit(line2, w - 160), x + 52, y + 32, hex("c8cbd8"))
        if (hov && hover.isNotEmpty()) footer = hover
        val bx = x + w - 104; val by = y + 5
        val c = if (glow && enabled) hex("d0861a") else hex("5b6074")
        button(bx, by, 100, 40, "", c, enabled, id = id) { if (action()) host.sfx(Sfx.BUY) else host.sfx(Sfx.ERROR) }
        if (cost != null) {
            when (costIcon) { "coin" -> Art.coin(gfx, bx + 14.0, by + 12.0, 6.0); "gem" -> Art.gem(gfx, bx + 14.0, by + 12.0, 6.0); "relic" -> Art.relic(gfx, bx + 14.0, by + 12.0, 6.0) }
            text(fit(cost, 74), bx + 24, by + 8, if (enabled) Color.WHITE else hex("aab0c0"))
            text(label, bx + 50, by + 24, if (enabled) hex("ffe8a0") else hex("9a9db0"), 1, 1)
        } else text(label, bx + 50, by + 16, if (enabled) Color.WHITE else hex("9a9db0"), 1, 1)
    }

    private fun buildList(): List<Row> = when (tab) {
        "hero" -> heroRows()
        "allies" -> allyRows()
        "gear" -> gearRows()
        "pets" -> petRows()
        "artifacts" -> artifactRows()
        "skills" -> skillTreeRows()
        "quests" -> questRows()
        else -> shopRows()
    }

    private fun heroRows(): List<Row> {
        val rows = ArrayList<Row>()
        val (n, cost) = g.tapUpgradeInfo()
        rows += upgradeRow("tap", { x, y -> Art.heroPortrait(gfx, heroLook(), x.toDouble(), y.toDouble(), 42.0, hex("e8643a")) },
            "${host.playerName} LV ${d.tapLevel}", "Tap ${fmt(g.tapDamage(false))}",
            "Crit ${(g.critChance() * 100).toInt()}% x${fmt(g.critMult())}",
            fmt(cost), "coin", if (n > 1) "LEVEL x$n" else "LEVEL UP", d.gold >= cost, true,
            "Level up your hero to hit harder. Allies also add 3% of their DPS to your taps.") { g.buyTap() }
        val canP = g.canPrestige()
        rows += upgradeRow("prestige", { x, y -> Art.icon(gfx, "prestige", x.toDouble(), y.toDouble(), 42.0) },
            "Prestige", if (canP) "+${fmt(g.relicsFor(d.maxStage))} relics" else "Reach stage $PRESTIGE_STAGE", "Restart for relics",
            null, "", if (canP) "PRESTIGE" else "STAGE $PRESTIGE_STAGE", canP, true,
            "Go back to stage 1 to earn relics. You keep gems, artifacts, gear, pets and achievements.") { confirmPrestige(); true }
        rows += section("SPELLS - CAST THEM FROM THE BATTLE")
        for (sk in SKILLS) {
            val lvl = g.skillLvl(sk.id)
            val maxed = lvl >= SKILL_MAX
            val locked = d.tapLevel < sk.req
            val c = g.skillCost(sk, lvl)
            rows += upgradeRow("skill:${sk.id}", { x, y -> Art.icon(gfx, sk.id, x.toDouble(), y.toDouble(), 42.0) },
                "${sk.name} LV $lvl", skillDesc(sk.id, max(1, lvl)) + if (sk.timed) " ${g.spellDuration().toInt()}s" else "",
                "${sk.mana} mana", if (maxed) null else fmt(c), "coin",
                when { maxed -> "MAX"; locked -> "TAP LV ${sk.req}"; lvl == 0 -> "LEARN"; else -> "UPGRADE" },
                !maxed && !locked && d.gold >= c, false) { g.buySkill(sk.id) }
        }
        return rows
    }

    private fun allyRows(): List<Row> {
        val rows = ArrayList<Row>()
        HEROES.forEachIndexed { i, h ->
            if (!g.heroUnlocked(i)) {
                if (i > 0 && !g.heroUnlocked(i - 1)) return@forEachIndexed
                rows += upgradeRow("hero:${h.id}", { x, y -> gfx.fill(rrect(x.toDouble(), y.toDouble(), 42.0, 42.0, 6.0), hex("2a2d3d")); text("?", x + 21, y + 14, hex("5a5e76"), 2, 1) },
                    "???", "Hire ${HEROES[i - 1].name}", "to reveal", fmt(h.cost), "coin", "LOCKED", false) { false }
                return@forEachIndexed
            }
            val lvl = g.heroLvl(h.id)
            val (n, cost) = g.heroUpgradeInfo(h)
            val gain = (g.heroBaseDps(h, lvl + n) - g.heroBaseDps(h, lvl)) * g.allyMult()
            val next = HERO_MILESTONES.firstOrNull { it.first > lvl }
            rows += upgradeRow("hero:${h.id}", { x, y -> Art.heroPortrait(gfx, h.look, x.toDouble(), y.toDouble(), 42.0, hex("3aa0b8")) },
                "${h.name} LV $lvl", "DPS ${fmt(g.heroBaseDps(h, lvl) * g.allyMult())}  +${fmt(gain)}",
                if (next != null) "x${next.second.toString().removeSuffix(".0")} dmg at LV ${next.first}" else "Fully mastered!",
                fmt(cost), "coin", (if (lvl == 0) "HIRE" else "LEVEL UP") + (if (n > 1) " x$n" else ""), d.gold >= cost, true) { g.buyHero(h.id) }
        }
        return rows
    }

    private fun gearRows(): List<Row> {
        val rows = ArrayList<Row>()
        rows += section("EQUIPPED")
        rows += Row(74) { x, y, w ->
            GEAR_SLOTS.forEachIndexed { i, s ->
                val gx = x + 8 + i * 90
                val gear = gearDef(d.equipped[s.id])
                if (gear == null) { gfx.fill(rrect(gx.toDouble(), y + 4.0, 48.0, 48.0, 6.0), hex("2a2d3d")); text("EMPTY", gx + 24, y + 24, hex("5a5e76"), 1, 1) }
                else Art.gearIcon(gfx, gear, gx.toDouble(), y + 4.0, 48.0, true)
                text(if (gear == null) s.name else "+${(g.gearValue(gear.id) * 100).toInt()}%", gx + 24, y + 58, hex("c8cbd8"), 1, 1)
            }
        }
        for (s in GEAR_SLOTS) {
            rows += section(s.name.uppercase() + "S - BOOST " + s.label.uppercase())
            val items = GEAR.filter { it.slot == s.id }
            rows += Row(64) { x, y, _ ->
                items.forEachIndexed { i, gear ->
                    val gx = x + 6 + i * 60
                    val owned = gear.id in d.gear
                    Art.gearIcon(gfx, gear, gx.toDouble(), y + 4.0, 52.0, owned)
                    if (owned) text("LV${d.gear[gear.id]}", gx + 50, y + 46, Color.WHITE, 1, 2)
                    if (d.equipped[gear.slot] == gear.id) { gfx.stroke = stroke(3f); gfx.color = Color.WHITE; gfx.draw(rrect(gx - 1.0, y + 3.0, 54.0, 54.0, 7.0)) }
                    if (hovered(gx, y + 4, 52, 52)) footer = if (owned) "${gear.name} (${RARITIES[gear.r].name} ${slotDef(gear.slot).name}, LV ${d.gear[gear.id]}): +${(g.gearValue(gear.id) * 100).toInt()}% ${slotDef(gear.slot).label}. Click to equip."
                        else "??? - a ${RARITIES[gear.r].name} ${slotDef(gear.slot).name}. Find it in shop chests."
                    hit(gx, y + 4, 52, 52, "gear:${gear.id}") { if (owned) { g.equip(gear.id); host.sfx(Sfx.EQUIP) } else host.sfx(Sfx.ERROR) }
                }
            }
        }
        return rows
    }

    private fun petRows(): List<Row> = PETS.map { p ->
        val lvl = g.petLevel(p.id)
        val eggs = d.pets[p.id]?.eggs ?: 0
        val active = d.activePet == p.id
        upgradeRow("pet:${p.id}", { x, y -> Art.petPortrait(gfx, p, x.toDouble(), y.toDouble(), 42.0, lvl > 0) },
            "${p.name} LV $lvl", "+${(p.per * max(1, lvl) * 100).toInt()}% ${p.label}",
            if (lvl > 0) "Eggs $eggs/${petEggsForLevel(lvl)}  hits ${5 + 3 * lvl}x tap" else "Find eggs in shop deals",
            null, "", when { lvl == 0 -> "LOCKED"; active -> "ACTIVE"; else -> "EQUIP" }, lvl > 0 && !active, active) {
            g.setActivePet(p.id); host.sfx(Sfx.PET); true
        }
    }

    private fun artifactRows(): List<Row> {
        val rows = ArrayList<Row>()
        val canP = g.canPrestige()
        rows += upgradeRow("prestige2", { x, y -> Art.icon(gfx, "prestige", x.toDouble(), y.toDouble(), 42.0) },
            "Prestige", if (canP) "+${fmt(g.relicsFor(d.maxStage))} relics" else "Reach stage $PRESTIGE_STAGE",
            "Earned so far: ${fmt(d.stats.relicsEarned)}", null, "", if (canP) "PRESTIGE" else "STAGE $PRESTIGE_STAGE", canP, true) { confirmPrestige(); true }
        rows += section("ARTIFACTS")
        for (a in ARTIFACTS) {
            val lvl = g.art(a.id)
            val maxed = a.max > 0 && lvl >= a.max
            val cost = g.artifactCost(a, lvl)
            rows += upgradeRow("art:${a.id}", { x, y -> Art.icon(gfx, a.id, x.toDouble(), y.toDouble(), 42.0) },
                "${a.name} LV $lvl" + if (a.max > 0) "/${a.max}" else "",
                if (lvl > 0) a.desc(lvl) else "Not unlocked", if (maxed) "Max level" else "Next: ${a.desc(lvl + 1)}",
                if (maxed) null else fmt(cost), "relic", if (maxed) "MAX" else if (lvl == 0) "UNLOCK" else "UPGRADE",
                !maxed && d.relics >= cost, true) { g.buyArtifact(a.id) }
        }
        return rows
    }

    private fun shopRows(): List<Row> {
        val rows = ArrayList<Row>()
        rows += section("FREE CHEST - EVERY 4 HOURS")
        val ready = g.freeChestReady()
        rows += upgradeRow("free", { x, y -> Art.chest(gfx, null, x + 21.0, y + 24.0, 0.7) },
            "Free Chest", "1 piece of gear",
            if (ready) "Ready!" else "Ready in ${fmtTime((d.freeChestAt - System.currentTimeMillis()) / 1000.0)}",
            null, "", if (ready) "OPEN" else "WAIT", ready, true) {
            val items = g.openFreeChest(); if (items != null) modal = LootModal("FREE CHEST", items, null); items != null
        }
        for ((sec, title) in SHOP_SECTIONS) {
            rows += section(title)
            when (sec) {
                "chest" -> for (c in CHESTS) rows += upgradeRow("chest:${c.id}", { x, y -> Art.chest(gfx, c, x + 21.0, y + 24.0, 0.7) },
                    c.name, "${c.items} pieces of gear",
                    when (c.id) { "titan" -> "1st is Epic or better!"; "legend" -> "All Epic or Legendary!"; else -> "%.1f%% Epic+".format(c.odds[2] * 100 + c.odds[3] * 100) },
                    c.gems.toString(), "gem", "OPEN", d.gems >= c.gems, true) {
                    val items = g.openChest(c.id); if (items != null) modal = LootModal(c.name.uppercase(), items, null); items != null
                }
                "skin" -> for (sk in SKINS) {
                    val owned = sk.id in d.skins
                    val wearing = d.skin == sk.id
                    rows += upgradeRow("skin:${sk.id}", { x, y -> Art.heroPortrait(gfx, sk.apply(HERO_LOOK), x.toDouble(), y.toDouble(), 42.0, hex("c9a02a")) },
                        sk.name, "Hero outfit (looks only)", if (wearing) "Wearing" else if (owned) "Owned" else "",
                        if (owned) null else sk.gems.toString(), "gem", when { wearing -> "TAKE OFF"; owned -> "WEAR"; else -> "BUY" },
                        owned || d.gems >= sk.gems, !owned) {
                        val ok = g.buySkin(sk.id); if (ok && !owned) showBanner("${sk.name.uppercase()}!", hex("ffd23a")); ok
                    }
                }
                else -> for (item in SHOP_ITEMS.filter { it.section == sec }) {
                    val bought = g.shopBought(item)
                    val active = d.buffs[item.id]
                    val lvl = g.shopLevel(item.id)
                    val line2 = when {
                        bought && item.kind == "perm" -> "Owned"
                        item.kind == "level" -> "Level $lvl/${item.maxLevel}"
                        active != null -> "Active ${fmtTime(active)}"
                        else -> ""
                    }
                    rows += upgradeRow("shop:${item.id}", { x, y -> shopIcon(item.icon, x, y) },
                        item.name, item.desc, line2, if (bought) null else g.shopPrice(item).toString(), "gem",
                        when { bought -> "OWNED"; active != null -> "ACTIVE"; else -> "BUY" },
                        !bought && active == null && d.gems >= g.shopPrice(item) && (item.id != "warp" || d.stage < d.maxStage), item.kind == "perm" || item.kind == "level") {
                        val msg = g.buyShopItem(item.id)
                        if (msg != null) showBanner(msg.uppercase(), hex("6ae4ff"))
                        msg != null
                    }
                    if (sec == "eggs" && item.id == "eggs20") {
                        val deals = g.currentDeals()
                        rows += section("DAILY DEALS - NEW IN ${fmtTime((DEAL_MS - System.currentTimeMillis() % DEAL_MS) / 1000.0)}")
                        deals.list.forEachIndexed { i, deal ->
                            val p = petDef(deal.pet) ?: return@forEachIndexed
                            rows += upgradeRow("deal:$i", { x, y -> Art.petPortrait(gfx, p, x.toDouble(), y.toDouble(), 42.0, true) },
                                "${p.name} x${deal.eggs} eggs", "Pet bonus: ${p.label}", if (deal.bought) "Sold out" else "",
                                if (deal.bought) null else deal.gems.toString(), "gem", if (deal.bought) "SOLD" else "BUY", !deal.bought && d.gems >= deal.gems, false) {
                                val ok = g.buyDeal(i); if (ok) { host.sfx(Sfx.PET); showBanner("${p.name.uppercase()} +${deal.eggs} EGGS", hex("9bff7a")) }; ok
                            }
                        }
                    }
                }
            }
        }
        return rows
    }

    private fun shopIcon(key: String, x: Int, y: Int) {
        when (key) {
            "chest_titan" -> { gfx.fill(rrect(x.toDouble(), y.toDouble(), 42.0, 42.0, 6.0), hex("2a5f8a")); Art.chest(gfx, CHESTS[2], x + 21.0, y + 24.0, 0.7) }
            "relic" -> { gfx.fill(rrect(x.toDouble(), y.toDouble(), 42.0, 42.0, 6.0), hex("5a2a8a")); Art.relic(gfx, x + 21.0, y + 22.0, 14.0) }
            "egg" -> { gfx.fill(rrect(x.toDouble(), y.toDouble(), 42.0, 42.0, 6.0), hex("3a7a4a")); Art.egg(gfx, x + 21.0, y + 22.0, 13.0) }
            else -> Art.icon(gfx, key, x.toDouble(), y.toDouble(), 42.0)
        }
    }

    // ------------------------------------------------------------------ skill tree
    private fun skillTreeRows(): List<Row> {
        val rows = ArrayList<Row>()
        TREE_BRANCHES.forEachIndexed { b, (name, color) ->
            rows += section("$name PATH")
            for (n in SKILL_TREE.filter { it.branch == b }) {
                val lvl = g.st(n.id)
                val maxed = lvl >= n.max
                rows += upgradeRow("tree:${n.id}", { x, y ->
                    gfx.fill(rrect(x.toDouble(), y.toDouble(), 42.0, 42.0, 6.0), hex(color).shade(-0.2))
                    text("${lvl}", x + 21, y + 8, Color.WHITE, 3, 1)
                }, "${n.name} LV $lvl/${n.max}", if (lvl > 0) n.desc(lvl) else "Not learned",
                    if (maxed) "Max level" else "Next: ${n.desc(lvl + 1)}",
                    if (maxed) null else "1 SP", "", if (maxed) "MAX" else if (lvl == 0) "LEARN" else "UPGRADE",
                    !maxed && g.spFree() > 0, true) { g.buyNode(n.id) }
            }
        }
        rows += section("MORE SKILL POINTS")
        rows += upgradeRow("tree:more", { x, y -> Art.icon(gfx, "tome", x.toDouble(), y.toDouble(), 42.0) },
            "Skill Book", "+3 skill points", "Also in the shop", g.shopPrice(SHOP_ITEMS.first { it.id == "sp_pack" }).toString(), "gem", "BUY",
            d.gems >= g.shopPrice(SHOP_ITEMS.first { it.id == "sp_pack" }), false) {
            val msg = g.buyShopItem("sp_pack"); if (msg != null) showBanner(msg.uppercase(), hex("6ae4ff")); msg != null
        }
        rows += upgradeRow("tree:respec", { x, y -> Art.icon(gfx, "prestige", x.toDouble(), y.toDouble(), 42.0) },
            "Forget Skills", "Refund every skill point", "", "50", "gem", "RESET", d.gems >= 50 && d.skillTree.isNotEmpty(), false) {
            g.buyShopItem("respec") != null
        }
        return rows
    }

    // ------------------------------------------------------------------ quests & daily login
    private fun questRows(): List<Row> {
        val rows = ArrayList<Row>()
        rows += section("DAILY LOGIN - DAY ${g.loginDay() + 1} OF 7")
        rows += Row(64) { x, y, w ->
            val cell = (w - 6) / 7
            for (i in 0 until 7) {
                val cx = x + 3 + i * cell
                val done = i < g.loginDay() || (i == g.loginDay() && !g.loginReady())
                val today = i == g.loginDay()
                gfx.fill(rrect(cx + 1.0, y + 4.0, cell - 2.0, 56.0, 5.0), when { done -> hex("2a4a2a"); today -> hex("4a4d5e"); else -> hex("34374a") })
                if (today && g.loginReady()) { gfx.stroke = stroke(2f); gfx.color = hex("ffd23a"); gfx.draw(rrect(cx + 1.0, y + 4.0, cell - 2.0, 56.0, 5.0)) }
                text("DAY ${i + 1}", cx + cell / 2, y + 8, Color.WHITE, 1, 1)
                when (i) {
                    1, 4 -> Art.egg(gfx, cx + cell / 2.0, y + 32.0, 9.0)
                    3 -> Art.chest(gfx, null, cx + cell / 2.0, y + 34.0, 0.4)
                    6 -> Art.gem(gfx, cx + cell / 2.0, y + 32.0, 9.0)
                    else -> Art.coin(gfx, cx + cell / 2.0, y + 32.0, 9.0)
                }
                if (done) text("OK", cx + cell / 2, y + 46, hex("7aff7a"), 1, 1)
            }
        }
        rows += upgradeRow("login", { x, y -> Art.icon(gfx, "clock", x.toDouble(), y.toDouble(), 42.0) },
            "Daily Reward", LOGIN_REWARDS[g.loginDay()], if (g.loginReady()) "Ready to collect!" else "Come back tomorrow",
            null, "", if (g.loginReady()) "COLLECT" else "DONE", g.loginReady(), true) {
            val msg = g.claimLogin(); if (msg != null) showBanner(msg.uppercase(), hex("ffd23a")); msg != null
        }
        rows += section("DAILY QUESTS")
        g.quests().list.forEachIndexed { i, q ->
            val prog = g.questProgress(q)
            val done = prog >= q.target
            rows += upgradeRow("quest:$i", { x, y -> Art.icon(gfx, "quest", x.toDouble(), y.toDouble(), 42.0) },
                g.questDef(q).text(q.target), "Progress $prog/${q.target}",
                "Reward: " + when (i) { 0 -> "gold"; 1 -> "3 pet eggs"; else -> "relics" },
                null, "", when { q.claimed -> "DONE"; done -> "CLAIM"; else -> "${(prog * 100 / max(1, q.target))}%" }, done && !q.claimed, done && !q.claimed) {
                val msg = g.claimQuest(i); if (msg != null) showBanner(msg.uppercase(), hex("ffd23a")); msg != null
            }
        }
        rows += upgradeRow("quest:bonus", { x, y -> gfx.fill(rrect(x.toDouble(), y.toDouble(), 42.0, 42.0, 6.0), hex("1a5a8a")); Art.gem(gfx, x + 21.0, y + 22.0, 13.0) },
            "Finish all 3 quests", "Bonus reward", if (d.quests.bonusClaimed) "Collected" else "", "2", "gem",
            if (d.quests.bonusClaimed) "DONE" else "CLAIM", g.questBonusReady(), true) {
            val msg = g.claimQuestBonus(); if (msg != null) showBanner(msg.uppercase(), hex("6ae4ff")); msg != null
        }
        return rows
    }

    private fun confirmPrestige() {
        if (!g.canPrestige()) { host.sfx(Sfx.ERROR); return }
        modal = ConfirmModal("PRESTIGE?", listOf("Go back to stage 1 and lose your gold,", "hero level, allies and spells.", "", "You keep gems, artifacts, gear, pets.", "You get:"),
            "+${fmt(g.relicsFor(d.maxStage))} relics") {
            val gained = g.prestige()
            host.sfx(Sfx.LEVEL_UP)
            showBanner("REBORN! +${fmt(gained)} RELICS", hex("c58aff"))
            tab = "artifacts"; scroll = 0.0
        }
    }

    // ================================================================== modals
    private abstract inner class Modal(val title: String, val w: Int = 520, val h: Int = 380) {
        val x get() = (this@GameScreen.width - w) / 2
        val y get() = (this@GameScreen.height - h) / 2
        fun draw() {
            gfx.save { alpha(0.6); fill(Rectangle2D.Double(0.0, 0.0, this@GameScreen.width.toDouble(), this@GameScreen.height.toDouble()), Color.BLACK) }
            nextButtons.clear() // only the modal is clickable
            panel(x, y, w, h)
            gfx.fill(Rectangle2D.Double(x + 3.0, y + 3.0, w - 6.0, 32.0), hex("4a4d5e"))
            text(title, x + w / 2, y + 12, Color.WHITE, 2, 1)
            body() // first, so a pop-up's main button gets the default highlight
            button(x + w - 34, y + 6, 26, 26, "X", hex("c0392b")) { close() }
        }
        abstract fun body()
        fun close() { modal = null; host.sfx(Sfx.CLICK); selected = "tap" }
        /** Shift pressed while open. */
        open fun back() = close()
    }

    /** Opens the screen-size check (first time, or from settings). */
    fun calibrate() { modal = CalibrateModal() }

    private inner class CalibrateModal : Modal("SCREEN SIZE", 460, 250) {
        private var zoom = d.screenZoom.takeIf { it > 0 } ?: 1.0
        init { selected = "m:done" }

        fun nudge(dx: Int, dy: Int) {
            zoom = clamp(zoom * (1 + 0.025 * (dx - dy)), 0.5, 2.0)
            d.screenZoom = zoom
            host.setZoom(zoom)
            host.sfx(Sfx.CLICK)
        }

        override fun body() {
            // yellow corner marks at the very edges of the game screen
            val c = hex("ffe34a"); val L = 40.0; val T = 6.0
            val W = this@GameScreen.width.toDouble(); val H = this@GameScreen.height.toDouble()
            for ((cx, cy) in listOf(0.0 to 0.0, W to 0.0, 0.0 to H, W to H)) {
                val sx = if (cx == 0.0) 0.0 else cx - L; val sy = if (cy == 0.0) 0.0 else cy - T
                gfx.fill(Rectangle2D.Double(sx, sy, L, T), c)
                gfx.fill(Rectangle2D.Double(if (cx == 0.0) 0.0 else cx - T, if (cy == 0.0) 0.0 else cy - L, T, L), c)
            }
            val lines = listOf("Click + or - (or press W/S) until the four", "yellow corners just touch the edges", "of your Minecraft window.", "")
            lines.forEachIndexed { i, l -> text(l, x + w / 2, y + 50 + i * 14, hex("c8cbd8"), 1, 1) }
            text("SIZE ${(zoom * 100).toInt()}%", x + w / 2, y + 116, hex("ffe34a"), 2, 1)
            text("(Using Minecraft's default FOV of 70 fits best)", x + w / 2, y + 140, hex("8a8ea6"), 1, 1)
            button(x + w / 2 - 110, y + 106, 36, 28, "-", hex("5b6074")) { nudge(0, 1) }
            button(x + w / 2 + 74, y + 106, 36, 28, "+", hex("5b6074")) { nudge(0, -1) }
            button(x + w / 2 - 60, y + h - 50, 120, 32, "DONE", hex("4f9a1a"), id = "m:done") {
                if (d.screenZoom <= 0) d.screenZoom = zoom
                close()
            }
        }
    }

    private inner class InfoModal(title: String, val lines: List<String>, val big: String) : Modal(title, 420, 220) {
        override fun body() {
            lines.forEachIndexed { i, l -> text(l, x + w / 2, y + 50 + i * 14, hex("c8cbd8"), 1, 1) }
            text(big, x + w / 2, y + 60 + lines.size * 14, hex("ffd23a"), 3, 1)
            button(x + w / 2 - 60, y + h - 44, 120, 30, "COLLECT", hex("4f9a1a")) { close() }
        }
    }

    private inner class ConfirmModal(title: String, val lines: List<String>, val big: String, val onYes: () -> Unit) : Modal(title, 440, 260) {
        override fun body() {
            lines.forEachIndexed { i, l -> text(l, x + w / 2, y + 48 + i * 14, hex("c8cbd8"), 1, 1) }
            text(big, x + w / 2, y + 54 + lines.size * 14, hex("c58aff"), 2, 1)
            button(x + w / 2 - 130, y + h - 46, 120, 32, "CANCEL", hex("5b6074")) { close() }
            button(x + w / 2 + 10, y + h - 46, 120, 32, "YES!", hex("d0861a")) { modal = null; onYes() }
        }
    }

    private inner class LootModal(title: String, val loot: List<GearRoll>, val extra: String?) : Modal(title, 520, 260) {
        init { host.sfx(if (loot.any { it.gear.r >= 3 }) Sfx.LEGENDARY else Sfx.CHEST) }
        override fun body() {
            val total = loot.size * 96
            loot.forEachIndexed { i, roll ->
                val gx = x + (w - total) / 2 + i * 96 + 16
                Art.gearIcon(gfx, roll.gear, gx.toDouble(), y + 54.0, 64.0, true)
                text(fit(roll.gear.name, 92), gx + 32, y + 126, hex(RARITIES[roll.gear.r].color), 1, 1)
                text(if (roll.isNew) "NEW!" else "LV ${d.gear[roll.gear.id]}", gx + 32, y + 140, if (roll.isNew) hex("ffe34a") else hex("c8cbd8"), 1, 1)
            }
            extra?.let { text(it, x + w / 2, y + 162, hex("6ae4ff"), 1, 1) }
            button(x + w / 2 - 130, y + h - 46, 120, 32, "AWESOME!", hex("4f9a1a")) { close() }
            button(x + w / 2 + 10, y + h - 46, 120, 32, "SEE GEAR", hex("c9a02a")) { close(); tab = "gear"; this@GameScreen.scroll = 0.0 }
        }
    }

    private inner class AchievementsModal : Modal("ACHIEVEMENTS", 560, 440) {
        override fun body() {
            ACH_GROUPS.forEachIndexed { i, a ->
                val ry = y + 44 + i * 34
                val value = a.stat(d)
                val next = a.tiers.indices.firstOrNull { "${a.key}_$it" !in d.achievements }
                val target = a.tiers[next ?: a.tiers.lastIndex]
                gfx.fill(Rectangle2D.Double(x + 12.0, ry.toDouble(), w - 24.0, 30.0), hex("34374a"))
                text(a.name, x + 20, ry + 4, Color.WHITE)
                var sx = x + 20 + PixelFont.width(a.name) + 8
                a.tiers.indices.forEach { ti -> text("*", sx, ry + 4, if ("${a.key}_$ti" in d.achievements) hex("ffd23a") else hex("5a5e76")); sx += 8 }
                text(a.text(target), x + 20, ry + 17, hex("c8cbd8"))
                val bx = x + w - 230.0
                gfx.fill(rrect(bx, ry + 8.0, 150.0, 12.0, 4.0), hex("151726"))
                gfx.fill(rrect(bx, ry + 8.0, 150 * min(1.0, value / target), 12.0, 4.0), if (next == null) hex("4f9a1a") else hex("e0901a"))
                text("${fmt(min(value, target))}/${fmt(target)}", (bx + 75).toInt(), ry + 11, Color.WHITE, 1, 1)
                when {
                    next == null -> text("DONE", x + w - 60, ry + 11, hex("7aff7a"))
                    g.achievementGems(a, next) > 0 -> { Art.gem(gfx, x + w - 64.0, ry + 15.0, 6.0); text("${g.achievementGems(a, next)}", x + w - 54, ry + 11, hex("6ae4ff")) }
                    else -> { Art.coin(gfx, x + w - 64.0, ry + 15.0, 6.0); text("gold", x + w - 54, ry + 11, hex("ffd23a")) }
                }
            }
        }
    }

    private inner class TopModal(val top: List<TopEntry>) : Modal("TOP PLAYERS", 440, 400) {
        override fun body() {
            if (top.isEmpty()) text("No players yet!", x + w / 2, y + 60, hex("c8cbd8"), 1, 1)
            top.take(10).forEachIndexed { i, e ->
                val ry = y + 46 + i * 32
                val c = when (i) { 0 -> hex("ffd23a"); 1 -> hex("e0e4ea"); 2 -> hex("cd7f32"); else -> hex("c8cbd8") }
                gfx.fill(Rectangle2D.Double(x + 12.0, ry.toDouble(), w - 24.0, 28.0), hex("34374a"))
                text("#${i + 1}", x + 22, ry + 7, c, 2)
                text(fit(e.name, 180, 2), x + 74, ry + 7, Color.WHITE, 2)
                text("STAGE ${e.best}", x + w - 22, ry + 6, c, 1, 2)
                text("${e.prestiges} prestiges", x + w - 22, ry + 16, hex("9aa0b8"), 1, 2)
            }
        }
    }

    private inner class SettingsModal : Modal("STATS & SETTINGS", 480, 380) {
        override fun body() {
            val st = d.stats
            val lines = listOf(
                "Highest stage" to "${st.bestStage}", "Monsters defeated" to fmt(st.kills), "Bosses defeated" to fmt(st.bosses),
                "Total taps" to fmt(st.taps), "Critical hits" to fmt(st.crits), "Gold earned" to fmt(st.goldEarned),
                "Spells cast" to fmt(st.casts), "Prestiges" to "${st.prestiges}", "Play time" to fmtTime(st.playTime),
            )
            lines.forEachIndexed { i, (k, v) ->
                val ry = y + 46 + i * 22
                gfx.fill(Rectangle2D.Double(x + 16.0, ry.toDouble(), w - 32.0, 18.0), hex("34374a"))
                text(k, x + 24, ry + 5, hex("c8cbd8")); text(v, x + w - 24, ry + 5, Color.WHITE, 1, 2)
            }
            button(x + 16, y + h - 74, 140, 28, "SOUND: " + if (d.sound) "ON" else "OFF", hex("3a6ab0")) { d.sound = !d.sound }
            button(x + w / 2 - 70, y + h - 74, 140, 28, "SCREEN SIZE", hex("3a8a6a")) { calibrate() }
            text("CURSOR SPEED ${(d.cursorSpeed * 100).toInt()}%", x + w / 2, y + h - 104, Color.WHITE, 1, 1)
            button(x + w / 2 - 110, y + h - 110, 30, 20, "-", hex("5b6074")) { d.cursorSpeed = clamp(d.cursorSpeed - 0.1, 0.3, 3.0) }
            button(x + w / 2 + 80, y + h - 110, 30, 20, "+", hex("5b6074")) { d.cursorSpeed = clamp(d.cursorSpeed + 0.1, 0.3, 3.0) }
            button(x + w - 156, y + h - 74, 140, 28, "RESET", hex("a8281a")) {
                modal = ConfirmModal("WIPE ALL PROGRESS?", listOf("Everything will be lost,", "including relics and gems.", "", "This cannot be undone!"), "ARE YOU SURE?") { host.resetProgress() }
            }
            text("TapX v${host.version}", x + w / 2, y + h - 30, hex("6a6e86"), 1, 1)
        }
    }
}

const val CURSOR_W = 15
const val CURSOR_H = 21
/** The arrow cursor, drawn once: a white arrow with a solid black border (visible on any colour). */
private val cursorImage: BufferedImage by lazy {
    val img = BufferedImage(CURSOR_W, CURSOR_H, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF)
    g.translate(1.0, 1.0)
    g.color = java.awt.Color.WHITE
    g.fill(java.awt.geom.Path2D.Double().apply {
        moveTo(0.0, 0.0); lineTo(0.0, 15.0); lineTo(4.0, 11.5); lineTo(7.0, 17.5); lineTo(9.5, 16.5); lineTo(7.0, 10.5); lineTo(11.5, 10.5); closePath()
    })
    g.dispose()
    // every empty pixel touching the arrow becomes the border
    val fill = Array(CURSOR_H) { y -> BooleanArray(CURSOR_W) { x -> (img.getRGB(x, y) ushr 24) >= 128 } }
    for (y in 0 until CURSOR_H) for (x in 0 until CURSOR_W) {
        if (fill[y][x]) continue
        val edge = (-1..1).any { dy -> (-1..1).any { dx -> fill.getOrNull(y + dy)?.getOrNull(x + dx) == true } }
        if (edge) img.setRGB(x, y, 0xff101010.toInt())
    }
    img
}
private val cursorSprite = ByteArray(CURSOR_W * CURSOR_H) { i -> Palette.toMap(cursorImage.getRGB(i % CURSOR_W, i / CURSOR_W) and 0xffffff) }
private val cursorMask = BooleanArray(CURSOR_W * CURSOR_H) { i -> (cursorImage.getRGB(i % CURSOR_W, i / CURSOR_W) ushr 24) >= 128 }
