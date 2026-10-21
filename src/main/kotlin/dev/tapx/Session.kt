package dev.tapx

import dev.tapx.screen.CURSOR_H
import dev.tapx.screen.CURSOR_W
import dev.tapx.screen.GameScreen
import dev.tapx.screen.MapScreen
import dev.tapx.screen.Palette
import dev.tapx.screen.ScreenHost
import dev.tapx.screen.Sfx
import dev.tapx.screen.TopEntry
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * One online player's game. While the player is on the game screen, a background thread runs
 * the game loop (input -> game update -> draw -> send changed pixels) 10 times per second.
 * All access to [game] happens on that thread while active; use [post] from elsewhere.
 */
class Session(val plugin: TapXPlugin, val player: Player, data: PlayerData) : GameEvents, ScreenHost {
    override val game = Game(data, this)
    val d get() = game.d
    val screen = GameScreen(this, SCREEN_COLS * 128, SCREEN_ROWS * 128)
    private val mapColors = ByteArray(screen.width * screen.height)
    private val frameColors = ByteArray(screen.width * screen.height) // last full frame, without the cursor

    @Volatile var active = false
        private set
    private var map: MapScreen? = null
    private var task: ScheduledFuture<*>? = null
    private val inbox = ConcurrentLinkedQueue<() -> Unit>()
    private var lastInput = BooleanArray(6)
    private var frames = 0L
    private val lastSound = HashMap<Sfx, Long>()

    override val playerName: String get() = player.name
    override val version: String get() = plugin.version

    /** Run [action] on the game thread (or right away when the game isn't running). */
    fun post(action: () -> Unit) {
        if (active) inbox.add(action) else action()
    }

    // ---------------------------------------------------------- mouse (head rotation while seated)
    private var lastYaw = Float.NaN
    private var lastPitch = 0f
    private var shift = 0f   // pitch we asked the client to subtract, until we see it applied
    private var shiftWait = 0

    /**
     * The client's head rotation, sent every tick because the player is seated. The view comes from
     * the camera entity, so the rotation is free to act as a mouse. Called on the network thread.
     */
    fun look(yaw: Float, pitch: Float) {
        if (lastYaw.isNaN()) { lastYaw = yaw; lastPitch = pitch; return }
        if (shift != 0f) {
            // the re-centre is relative, so no movement is lost; spot the packet where it lands
            if (kotlin.math.abs(pitch - (lastPitch - shift)) < kotlin.math.abs(pitch - lastPitch)) { lastPitch -= shift; shift = 0f }
            else if (++shiftWait > 40) shift = 0f
        }
        var dy = yaw - lastYaw
        while (dy > 180) dy -= 360
        while (dy < -180) dy += 360
        val dp = pitch - lastPitch
        lastYaw = yaw; lastPitch = pitch
        if (dy != 0f || dp != 0f) post { screen.mouse(dy.toDouble(), dp.toDouble()) }
        // the client's pitch stops at +-90, so turn it back towards level before it gets there
        if (shift == 0f && kotlin.math.abs(pitch) > 45f) {
            shift = pitch; shiftWait = 0
            (player as org.bukkit.craftbukkit.entity.CraftPlayer).handle.connection.send(
                net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket(0f, true, -pitch, true))
        }
    }

    @Volatile private var wheelSlot = 0

    fun wheelStart(slot: Int) { wheelSlot = slot }

    /** The mouse wheel: the client's hotbar slot, one step per notch. Called on the network thread. */
    fun wheel(slot: Int) {
        val steps = (slot - wheelSlot + 9 + 4) % 9 - 4 // shortest way round the 9 slots
        wheelSlot = slot
        if (steps != 0) post { screen.wheel(steps) }
    }

    /** Movement keys from the client (WASD, space, shift). Called on the main thread. */
    fun input(i: org.bukkit.Input) {
        val now = booleanArrayOf(i.isForward, i.isBackward, i.isLeft, i.isRight, i.isJump, i.isSneak)
        post {
            val prev = lastInput
            lastInput = now
            screen.input(now[0], now[1], now[2], now[3], now[4], now[5], prev[4], prev[5])
        }
    }

    private var clickWindow = 0L
    private var clicksInWindow = 0

    /** A left-click. Limited per second so auto-clickers can't break the game. */
    fun click() {
        val now = System.currentTimeMillis()
        if (now - clickWindow >= 1000) { clickWindow = now; clicksInWindow = 0 }
        if (++clicksInWindow > plugin.config.getInt("max-taps-per-second", 20)) return
        post { screen.click() }
    }

    fun start(screenMap: MapScreen) {
        if (active) return
        map = screenMap
        val away = (System.currentTimeMillis() - d.lastSeen) / 1000.0
        if (away > 30) screen.welcome(away, game.applyOffline(away, plugin.config.getDouble("max-offline-hours", 12.0)))
        d.lastSeen = System.currentTimeMillis()
        screenMap.spawn()
        if (d.screenZoom <= 0) screen.calibrate()
        active = true
        task = plugin.gameThreads.scheduleAtFixedRate(::loop, 0, TICK_MS, TimeUnit.MILLISECONDS)
    }

    /** Stop the game loop and remove the screen. Safe to call from any thread. */
    fun stop() {
        if (!active) return
        active = false
        task?.cancel(false)
        task = null
        map?.remove()
        map = null
        // apply anything still queued so it isn't lost
        while (true) inbox.poll()?.invoke() ?: break
        d.lastSeen = System.currentTimeMillis()
    }

    private var ticks = 0L
    private var drawnX = 0
    private var drawnY = 0

    /**
     * Runs every [TICK_MS]. The game and the screen update at the configured fps; in between only
     * the cursor moves: its old spot is restored from the last frame and only that area is re-sent.
     */
    private fun loop() {
        try {
            while (true) inbox.poll()?.invoke() ?: break
            if (!active) return
            val every = (1000 / TICK_MS / plugin.config.getInt("screen-fps", 20).coerceIn(2, 1000 / TICK_MS.toInt())).toInt()
            val full = ticks++ % every == 0L
            if (full) {
                val dt = every * TICK_MS / 1000.0
                game.update(dt)
                screen.update(dt)
                d.lastSeen = System.currentTimeMillis()
                if (frames++ % 10 == 0L) game.checkAchievements()
                screen.render()
                Palette.convert(screen.pixels, frameColors, screen.width)
            }
            val (cx, cy) = screen.stepCursor()
            val m = map ?: return
            if (full) {
                System.arraycopy(frameColors, 0, mapColors, 0, mapColors.size)
                screen.overlayCursorAt(mapColors, cx, cy)
                m.push(mapColors)
            } else if (cx != drawnX || cy != drawnY) {
                val w = screen.width
                for (y in maxOf(0, drawnY) until minOf(screen.height, drawnY + CURSOR_H)) {
                    val x0 = maxOf(0, drawnX); val x1 = minOf(w, drawnX + CURSOR_W)
                    if (x1 > x0) System.arraycopy(frameColors, y * w + x0, mapColors, y * w + x0, x1 - x0)
                }
                screen.overlayCursorAt(mapColors, cx, cy)
                m.push(mapColors, minOf(cx, drawnX), minOf(cy, drawnY), maxOf(cx, drawnX) + CURSOR_W, maxOf(cy, drawnY) + CURSOR_H)
            }
            drawnX = cx; drawnY = cy
        } catch (e: Throwable) {
            plugin.logger.log(Level.WARNING, "TapX frame failed for ${player.name}", e)
        }
    }

    // ---------------------------------------------------------- ScreenHost
    override fun sfx(s: Sfx) {
        if (!d.sound) return
        val now = System.currentTimeMillis()
        if (now - (lastSound[s] ?: 0) < 60) return
        lastSound[s] = now
        val (sound, vol, pitch) = when (s) {
            Sfx.HIT -> Triple(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 1.5f)
            Sfx.CRIT -> Triple(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.0f)
            Sfx.KILL -> Triple(Sound.ENTITY_GENERIC_DEATH, 0.3f, 1.4f)
            Sfx.COIN -> Triple(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f, 1.6f)
            Sfx.BOSS -> Triple(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.3f)
            Sfx.BOSS_KILL -> Triple(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f)
            Sfx.LEVEL_UP -> Triple(Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f)
            Sfx.BUY -> Triple(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f)
            Sfx.CLICK -> Triple(Sound.UI_BUTTON_CLICK, 0.4f, 1.2f)
            Sfx.ERROR -> Triple(Sound.ENTITY_VILLAGER_NO, 0.4f, 1.0f)
            Sfx.THUNDER -> Triple(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.4f)
            Sfx.SPELL -> Triple(Sound.ENTITY_EVOKER_CAST_SPELL, 0.5f, 1.2f)
            Sfx.PET -> Triple(Sound.ENTITY_FOX_AMBIENT, 0.7f, 1.4f)
            Sfx.CHEST -> Triple(Sound.BLOCK_CHEST_OPEN, 0.6f, 1.0f)
            Sfx.LEGENDARY -> Triple(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f)
            Sfx.EQUIP -> Triple(Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.6f, 1.0f)
            Sfx.WISP -> Triple(Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.7f, 1.2f)
            Sfx.FAIL -> Triple(Sound.ENTITY_WITHER_SKELETON_AMBIENT, 0.4f, 0.8f)
            Sfx.ACHIEVEMENT -> Triple(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.4f, 1.5f)
        }
        // Bukkit calls belong on the main thread.
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) player.playSound(player.location, sound, SoundCategory.MASTER, vol, pitch)
        })
    }

    override fun exit() {
        plugin.server.scheduler.runTask(plugin, Runnable { plugin.arcade.leave(player) })
    }

    override fun leaderboard(): List<TopEntry> = plugin.leaderboard()

    override val allowExit: Boolean get() = player.hasPermission("tapx.admin")

    override fun setZoom(zoom: Double) {
        plugin.server.scheduler.runTask(plugin, Runnable { plugin.arcade.applyZoom(player, zoom) })
    }

    override fun resetProgress() {
        val old = d
        game.d = PlayerData().apply {
            name = player.name
            gems = plugin.config.getLong("starting-gems", 25)
            returnTo = old.returnTo
            returnMode = old.returnMode
            screenZoom = old.screenZoom
            cursorSpeed = old.cursorSpeed
        }
        game.monster = null
    }

    // ---------------------------------------------------------- GameEvents (forwarded to the screen)
    override fun onTap(damage: Double, crit: Boolean, auto: Boolean) = screen.onTap(damage, crit, auto)
    override fun onKill(m: Monster, gold: Double, gems: Int) = screen.onKill(m, gold, gems)
    override fun onSpawn(m: Monster) = screen.onSpawn(m)
    override fun onStage(skipped: Int) = screen.onStage(skipped)
    override fun onBossFail() = screen.onBossFail()
    override fun onSkill(id: String, damage: Double) = screen.onSkill(id, damage)
    override fun onPetAttack(damage: Double) = screen.onPetAttack(damage)
    override fun onAllyAttack(id: String, damage: Double) = screen.onAllyAttack(id, damage)
    override fun onAchievement(group: AchGroup, target: Double, gems: Int) = screen.onAchievement(group, target, gems)

    companion object {
        const val SCREEN_COLS = 7
        const val SCREEN_ROWS = 4
        /** Mouse movement arrives every 50 ms (a client tick); the cursor is drawn twice as often. */
        const val TICK_MS = 25L
    }
}
